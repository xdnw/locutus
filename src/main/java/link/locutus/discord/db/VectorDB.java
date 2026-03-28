package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.IVectorDB;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.SqliteVecStore;
import link.locutus.discord.gpt.imps.VectorRow;
import link.locutus.discord.gpt.imps.embedding.EmbeddingInfo;
import link.locutus.discord.gpt.imps.embedding.IEmbedding;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import org.jdbi.v3.core.statement.PreparedBatch;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static graphql.com.google.common.base.Preconditions.checkArgument;

public class VectorDB extends DBMainV3 implements IVectorDB {

    private final IEmbedding embedding;
    private volatile boolean loaded = false;
    private final Map<Integer, EmbeddingSource> embeddingSources;
    private final Map<Integer, ConvertingDocument> unconvertedDocuments;
    private final SqliteVecStore vectors;
    private final Map<String, Integer> usageCache = new ConcurrentHashMap<>();

    public VectorDB(IEmbedding embedding) throws Exception {
        super(
                new File(Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY),
                "gpt",
                true,
                false,
                Settings.INSTANCE.DATABASE.SQLITE.GPT_MMAP_SIZE_MB,
                20,
                5
        );
        this.embeddingSources = new ConcurrentHashMap<>();
        this.unconvertedDocuments = new ConcurrentHashMap<>();
        this.embedding = embedding;
        this.vectors = new SqliteVecStore(Path.of("database", embedding.getTableName() + ".db"), embedding.getDimensions());
    }

    @SuppressWarnings("unchecked")
    public <T extends VectorDB> T load() {
        if (loaded) {
            return (T) this;
        }
        loaded = true;
        createTables();
        loadSources();
        loadUnconvertedDocuments();
        loadUsageCache();
        return (T) this;
    }

    @Override
    public void deleteMissing(EmbeddingSource source, Set<Long> hashesSet) {
        vectors.deleteMissing(source.source_id, hashesSet);
    }

    @Override
    public String getText(long hash) {
        VectorRow vector = vectors.getVectorById(hash, true, false);
        return vector == null ? null : vector.text;
    }

    @Override
    public void createEmbeddingIfNotExist(IEmbedding provider, long embeddingHash, String embeddingText,
            EmbeddingSource source, ThrowingConsumer<String> moderate) {
        vectors.addDocumentIfNotExists(embeddingText, embeddingHash, (ThrowingSupplier<float[]>) () -> {
            if (moderate != null) {
                moderate.accept(embeddingText);
            }
            return provider.fetchAndNormalize(embeddingText);
        }, source.source_id);
    }

    private void createSourcesTable() {
        jdbi().useHandle(handle ->
                handle.execute("CREATE TABLE IF NOT EXISTS sources (source_id INTEGER PRIMARY KEY AUTOINCREMENT, source_name VARCHAR NOT NULL, date_added BIGINT NOT NULL, hash BIGINT NOT NULL, guild_id BIGINT NOT NULL)")
        );
    }

    private void createDocumentQueueTable() {
        jdbi().useHandle(handle ->
                handle.execute("CREATE TABLE IF NOT EXISTS document_queue ("
                        + "source_id INTEGER NOT NULL, "
                        + "prompt VARCHAR NOT NULL, "
                        + "remaining VARCHAR, "
                        + "converted BOOLEAN NOT NULL, "
                        + "use_global_context BOOLEAN NOT NULL, "
                        + "user BIGINT NOT NULL, "
                        + "error VARCHAR, "
                        + "date BIGINT NOT NULL, "
                        + "hash BIGINT NOT NULL, "
                        + "PRIMARY KEY (source_id))")
        );
    }

    private void createUsageTable() {
        jdbi().useHandle(handle ->
                handle.execute("CREATE TABLE IF NOT EXISTS usage ("
                        + "model VARCHAR PRIMARY KEY, "
                        + "usage INTEGER NOT NULL, "
                        + "day BIGINT NOT NULL)")
        );
    }

    public synchronized void loadUsageCache() {
        long currentDay = TimeUtil.getDay();
        boolean[] needsDelete = { false };

        List<UsageRow> rows = jdbi().withHandle(handle -> handle.createQuery(
                        "SELECT model, usage, day FROM usage")
                .map((rs, ctx) -> new UsageRow(
                        rs.getString("model"),
                        rs.getInt("usage"),
                        rs.getLong("day")
                ))
                .list());

        for (UsageRow row : rows) {
            if (row.day() == currentDay) {
                usageCache.put(row.model(), row.usage());
            } else if (row.day() < currentDay) {
                needsDelete[0] = true;
            }
        }

        if (needsDelete[0]) {
            jdbi().useHandle(handle -> handle.createUpdate("DELETE FROM usage WHERE day < :currentDay")
                    .bind("currentDay", currentDay)
                    .execute());
        }
    }

    @Override
    public int getUsage(String model) {
        return usageCache.getOrDefault(model, 0);
    }

    @Override
    public void addUsage(String model, int usage) {
        usageCache.merge(model, usage, Integer::sum);
        jdbi().useTransaction(handle -> handle.createUpdate(
                        "INSERT INTO usage (model, usage, day) VALUES (:model, :usage, :day) "
                                + "ON CONFLICT(model) DO UPDATE SET usage = usage + :usage")
                .bind("model", model)
                .bind("usage", usage)
                .bind("day", TimeUtil.getDay())
                .execute());
    }

    private void loadUnconvertedDocuments() {
        jdbi().useHandle(handle -> {
            handle.createUpdate("DELETE FROM document_queue WHERE converted = :converted")
                    .bind("converted", true)
                    .execute();
            handle.execute("DELETE FROM document_queue WHERE source_id NOT IN (SELECT source_id FROM sources)");
        });

        List<ConvertingDocument> docs = jdbi().withHandle(handle -> handle.createQuery(
                        "SELECT source_id, prompt, remaining, converted, use_global_context, user, error, date, hash FROM document_queue")
                .map((rs, ctx) -> {
                    ConvertingDocument doc = new ConvertingDocument();
                    doc.source_id = rs.getInt("source_id");
                    doc.prompt = rs.getString("prompt");
                    doc.text = rs.getString("remaining");
                    doc.converted = rs.getBoolean("converted");
                    doc.use_global_context = rs.getBoolean("use_global_context");
                    doc.user = rs.getLong("user");
                    doc.error = rs.getString("error");
                    doc.date = rs.getLong("date");
                    doc.hash = rs.getLong("hash");
                    return doc;
                })
                .list());

        unconvertedDocuments.clear();
        for (ConvertingDocument doc : docs) {
            unconvertedDocuments.put(doc.source_id, doc);
        }
    }

    @Override
    public List<ConvertingDocument> getUnconvertedDocuments() {
        load();
        return new ArrayList<>(this.unconvertedDocuments.values());
    }

    @Override
    public ConvertingDocument getConvertingDocument(int source_id) {
        load();
        return unconvertedDocuments.get(source_id);
    }

    @Override
    public void addDocument(List<ConvertingDocument> documents) {
        load();
        for (ConvertingDocument document : documents) {
            if (!document.converted) {
                unconvertedDocuments.put(document.source_id, document);
            } else {
                unconvertedDocuments.remove(document.source_id);
            }
        }
        jdbi().useTransaction(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "INSERT OR REPLACE INTO document_queue (source_id, prompt, remaining, converted, use_global_context, user, error, date, hash) "
                            + "VALUES (:sourceId, :prompt, :remaining, :converted, :useGlobalContext, :user, :error, :date, :hash)");
            for (ConvertingDocument document : documents) {
                batch.bind("sourceId", document.source_id)
                        .bind("prompt", document.prompt)
                        .bind("remaining", document.text)
                        .bind("converted", document.converted)
                        .bind("useGlobalContext", document.use_global_context)
                        .bind("user", document.user)
                        .bind("error", document.error)
                        .bind("date", document.date)
                        .bind("hash", document.hash)
                        .add();
            }
            batch.execute();
        });
    }

    @Override
    public void deleteDocument(int sourceId) {
        load();
        unconvertedDocuments.remove(sourceId);
        jdbi().useHandle(handle -> handle.createUpdate("DELETE FROM document_queue WHERE source_id = :sourceId")
                .bind("sourceId", sourceId)
                .execute());
    }

    @Override
    public EmbeddingSource getEmbeddingSource(int source_id) {
        load();
        return embeddingSources.get(source_id);
    }

    @Override
    public synchronized EmbeddingSource getOrCreateSource(String name, long guild_id) {
        load();
        String normalizedName = name.toLowerCase();

        EmbeddingSource source = this.getSource(normalizedName, guild_id);
        if (source != null) {
            return source;
        }

        long dateAdded = System.currentTimeMillis();
        int sourceId = jdbi().withHandle(handle -> handle.createQuery(
                        "INSERT INTO sources (source_name, date_added, guild_id, hash) VALUES (:sourceName, :dateAdded, :guildId, :hash) RETURNING source_id")
                .bind("sourceName", normalizedName)
                .bind("dateAdded", dateAdded)
                .bind("guildId", guild_id)
                .bind("hash", 0L)
                .mapTo(Integer.class)
                .one());

        EmbeddingSource created = new EmbeddingSource(sourceId, normalizedName, dateAdded, 0, guild_id);
        embeddingSources.put(created.source_id, created);
        return created;
    }

    @Override
    public void updateSources(List<EmbeddingSource> sources) {
        load();
        jdbi().useTransaction(handle -> {
            PreparedBatch batch = handle.prepareBatch(
                    "UPDATE sources SET source_name = :sourceName, date_added = :dateAdded, guild_id = :guildId WHERE source_id = :sourceId");
            for (EmbeddingSource source : sources) {
                batch.bind("sourceName", source.source_name)
                        .bind("dateAdded", source.date_added)
                        .bind("guildId", source.guild_id)
                        .bind("sourceId", source.source_id)
                        .add();
            }
            batch.execute();
        });
    }

    private void loadSources() {
        embeddingSources.clear();
        List<EmbeddingSource> sources = jdbi().withHandle(handle -> handle.createQuery(
                        "SELECT source_id, source_name, date_added, guild_id, hash FROM sources")
                .map((rs, ctx) -> new EmbeddingSource(
                        rs.getInt("source_id"),
                        rs.getString("source_name"),
                        rs.getLong("date_added"),
                        rs.getLong("hash"),
                        rs.getLong("guild_id")
                ))
                .list());

        for (EmbeddingSource source : sources) {
            embeddingSources.put(source.source_id, source);
        }
    }

    @Override
    public synchronized void createTables() {
        createSourcesTable();
        createDocumentQueueTable();
        createUsageTable();
    }

    @Override
    public float[] getEmbedding(long hash) {
        load();
        VectorRow vector = vectors.getVectorById(hash, false, true);
        return vector == null ? null : vector.vector;
    }

    @Override
    public void deleteSource(EmbeddingSource source) {
        load();
        int source_id = source.source_id;
        vectors.deleteMissing(source_id, Collections.emptySet());
        jdbi().useTransaction(handle -> {
            handle.createUpdate("DELETE FROM document_queue WHERE source_id = :sourceId")
                    .bind("sourceId", source_id)
                    .execute();
            handle.createUpdate("DELETE FROM sources WHERE source_id = :sourceId")
                    .bind("sourceId", source_id)
                    .execute();
        });
        embeddingSources.remove(source.source_id);
        unconvertedDocuments.remove(source_id);
    }

    @Override
    public float[] getEmbedding(String content) {
        return getEmbedding(getHash(content));
    }

    @Override
    public float[] getOrCreateEmbedding(IEmbedding provider, long embeddingHash, String embeddingText,
            EmbeddingSource source, boolean save, ThrowingConsumer<String> moderate) {
        float[] existing = getEmbedding(embeddingHash);
        if (existing == null) {
            if (moderate != null) {
                moderate.accept(embeddingText);
            }
            existing = provider.fetchAndNormalize(embeddingText);
            if (save) {
                vectors.addDocumentIfNotExists(embeddingText, embeddingHash, existing, source.source_id);
            }
        }
        return existing;
    }

    @Override
    public long getHash(String data) {
        return StringMan.hash(data);
    }

    @Override
    public void iterateVectors(Set<EmbeddingSource> allowedSources, Consumer<VectorRow> source_hash_vector_consumer) {
        Set<Integer> srcIds = new IntOpenHashSet(allowedSources.size());
        for (EmbeddingSource src : allowedSources) {
            srcIds.add(src.source_id);
        }
        vectors.getDocumentsBySource(srcIds, true, result -> {
            EmbeddingSource source = result.sourceId == -1 ? null : embeddingSources.get(result.sourceId);
            if (source != null) {
                source_hash_vector_consumer.accept(result);
            } else {
                Logg.error("Source with id " + result.sourceId + " not found for hash " + result.id + " | " + result.text);
            }
        });
    }

    @Override
    public Map<Long, String> getContent(Set<Long> hashes) {
        Map<Long, String> result = new Long2ObjectOpenHashMap<>();
        if (hashes == null || hashes.isEmpty()) {
            return result;
        }
        List<Long> hashesSorted = new LongArrayList();
        hashesSorted.addAll(hashes);
        hashesSorted.sort(Long::compareTo);
        for (Long hash : hashesSorted) {
            VectorRow row = vectors.getVectorById(hash, true, false);
            if (row != null) {
                result.put(hash, row.text);
            }
        }
        return result;
    }

    @Override
    public int countVectors(int source_id) {
        return vectors.countBySource(Set.of(source_id));
    }

    @Override
    public EmbeddingSource getSource(String name, long guild_id) {
        for (EmbeddingSource source : embeddingSources.values()) {
            if (source.guild_id == guild_id && source.source_name.equals(name)) {
                return source;
            }
        }
        return null;
    }

    @Override
    public Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull,
            Predicate<EmbeddingSource> sourcePredicate) {
        Set<EmbeddingSource> result = new ObjectLinkedOpenHashSet<>();
        for (EmbeddingSource source : embeddingSources.values()) {
            if (guildPredicateOrNull == null || guildPredicateOrNull.test(source.guild_id)) {
                if (sourcePredicate == null || sourcePredicate.test(source)) {
                    result.add(source);
                }
            }
        }
        return result;
    }

    @Override
    public float[] getEmbedding(EmbeddingSource source, String text, ThrowingConsumer<String> moderate) {
        long hash = getHash(text);
        return getOrCreateEmbedding(embedding, hash, text, source, true, moderate);
    }

    @Override
    public List<EmbeddingInfo> getClosest(EmbeddingSource inputSource, String input, int top,
            Set<EmbeddingSource> allowedTypes,
            BiPredicate<EmbeddingSource, Long> sourceHashPredicate,
            ThrowingConsumer<String> moderate) {
        checkArgument(top > 0, "top must be > 0");

        float[] compareTo = getEmbedding(inputSource, input, moderate);
        Map<Integer, EmbeddingSource> sourceMap = new Int2ObjectLinkedOpenHashMap<>();
        for (EmbeddingSource allowedType : allowedTypes) {
            sourceMap.put(allowedType.source_id, allowedType);
        }
        List<VectorRow> results = vectors.searchSimilarReranked(compareTo, top, true, sourceMap.keySet(), result -> {
            EmbeddingSource source = sourceMap.get(result.sourceId);
            if (source == null) {
                Logg.error("Source with id " + result.sourceId + " not found for hash " + result.id + " | " + result.text);
                return false;
            }
            return sourceHashPredicate.test(source, result.id);
        });
        List<EmbeddingInfo> resultsMapped = new ObjectArrayList<>(results.size());
        for (VectorRow result : results) {
            EmbeddingSource source = sourceMap.get(result.sourceId);
            EmbeddingInfo info = new EmbeddingInfo(result.text, result.id, source, result.score);
            resultsMapped.add(info);
        }
        return resultsMapped;
    }

    private record UsageRow(String model, int usage, long day) {
    }
}
