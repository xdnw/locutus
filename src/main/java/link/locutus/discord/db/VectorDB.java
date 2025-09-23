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
import org.jetbrains.annotations.Nullable;
import org.jooq.Record;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static graphql.com.google.common.base.Preconditions.checkArgument;

public class VectorDB extends DBMainV3 implements IVectorDB, Closeable {

    private final IEmbedding embedding;

    private volatile boolean loaded = false;
    private final Map<Integer, EmbeddingSource> embeddingSources;
    private final Map<Integer, ConvertingDocument> unconvertedDocuments;
    private final SqliteVecStore vectors;

    private final Map<String, Integer> usageCache = new ConcurrentHashMap<>();

    public VectorDB(IEmbedding embedding) throws Exception {
        super(Settings.INSTANCE.DATABASE, "gpt", false);
        this.embeddingSources = new ConcurrentHashMap<>();
        this.unconvertedDocuments = new ConcurrentHashMap<>();

        this.embedding = embedding;

        this.vectors = new SqliteVecStore(Path.of("database", embedding.getTableName() + ".db"), embedding.getDimensions());
    }

    public <T extends VectorDB> T load() {
        if (loaded) return (T) this;
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
    public void createEmbeddingIfNotExist(IEmbedding provider, long embeddingHash, String embeddingText, EmbeddingSource source, ThrowingConsumer<String> moderate) {
        vectors.addDocumentIfNotExists(embeddingText, embeddingHash, (ThrowingSupplier<float[]>) () -> {
            if (moderate != null) {
                moderate.accept(embeddingText);
            }
            return provider.fetchAndNormalize(embeddingText);
        }, source.source_id);
    }

    private void createSourcesTable() {
        ctx().execute("CREATE TABLE IF NOT EXISTS sources (source_id INTEGER PRIMARY KEY AUTOINCREMENT, source_name VARCHAR NOT NULL, date_added BIGINT NOT NULL, hash BIGINT NOT NULL, guild_id BIGINT NOT NULL)");
    }

    private void createDocumentQueueTable() {
        ctx().execute("CREATE TABLE IF NOT EXISTS document_queue (" +
                "source_id INTEGER NOT NULL, " +
                "prompt VARCHAR NOT NULL, " +
                "remaining VARCHAR, " +
                "converted BOOLEAN NOT NULL, " +
                "use_global_context BOOLEAN NOT NULL, " +
                "user BIGINT NOT NULL, " +
                "error VARCHAR, " +
                "date BIGINT NOT NULL, " +
                "hash BIGINT NOT NULL, " +
                "PRIMARY KEY (source_id))");
    }

    private void createUsageTable() {
        ctx().execute(
                "CREATE TABLE IF NOT EXISTS usage (" +
                        "model VARCHAR PRIMARY KEY, " +
                        "usage INTEGER NOT NULL, " +
                        "day BIGINT NOT NULL)"
        );
    }

    public synchronized void loadUsageCache() {
        long currentDay = TimeUtil.getDay();
        boolean[] needsDelete = {false};

        ctx().selectFrom("usage").fetch().forEach(r -> {
            String model = r.get("model", String.class);
            int usage = r.get("usage", int.class);
            long day = r.get("day", long.class);
            if (day == currentDay) {
                usageCache.put(model, usage);
            } else if (day < currentDay) {
                needsDelete[0] = true;
            }
        });

        if (needsDelete[0]) {
            ctx().execute("DELETE FROM usage WHERE day < ?", currentDay);
        }
    }

    public int getUsage(String model) {
        return usageCache.getOrDefault(model, 0);
    }

    public void addUsage(String model, int usage) {
        usageCache.merge(model, usage, Integer::sum);
        ctx().transaction((TransactionalRunnable) -> {
            ctx().execute("INSERT INTO usage (model, usage, day) VALUES (?, ?, ?) ON CONFLICT(model) DO UPDATE SET usage = usage + ?",
                    model, usage, TimeUtil.getDay(), usage);
        });
    }

    private void loadUnconvertedDocuments() {
        ctx().execute("DELETE FROM document_queue WHERE converted = ?", true);
        // delete where source_id not in sources
        ctx().execute("DELETE FROM document_queue WHERE source_id NOT IN (SELECT source_id FROM sources)");

        List<ConvertingDocument> docs = ctx().selectFrom("document_queue").fetchInto(ConvertingDocument.class);
        for (ConvertingDocument doc : docs) {
            unconvertedDocuments.put(doc.source_id, doc);
        }
    }

    public List<ConvertingDocument> getUnconvertedDocuments() {
        load();
        return new ArrayList<>(this.unconvertedDocuments.values());
    }

    @Override
    public ConvertingDocument getConvertingDocument(int source_id) {
        load();
        return unconvertedDocuments.get(source_id);
    }

    public void addDocument(List<ConvertingDocument> documents) {
        load();
        for (ConvertingDocument document : documents) {
            if (!document.converted) {
                unconvertedDocuments.put(document.source_id, document);
            } else {
                unconvertedDocuments.remove(document.source_id);
            }
        }
        ctx().transaction((TransactionalRunnable) -> {
            for (ConvertingDocument document : documents) {
                ctx().execute("INSERT OR REPLACE INTO document_queue (source_id, prompt, converted, use_global_context, user, error, date, hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        document.source_id, document.prompt, document.converted, document.use_global_context, document.user, document.error, document.date, document.hash);
            }
        });
    }


    @Override
    public void deleteDocument(int sourceId) {
        load();
        unconvertedDocuments.remove(sourceId);
        ctx().transaction((TransactionalRunnable) -> {
            ctx().execute("DELETE FROM document_queue WHERE source_id = ?", sourceId);
        });
    }

    @Override
    public EmbeddingSource getEmbeddingSource(int source_id) {
        load();
        return embeddingSources.get(source_id);
    }

    @Override
    public synchronized EmbeddingSource getOrCreateSource(String name, long guild_id) {
        load();
        name = name.toLowerCase();

        EmbeddingSource source = this.getSource(name, guild_id);

        if (source == null) {
            long date_added = System.currentTimeMillis();
            // create
            source = new EmbeddingSource(-1, name, date_added, 0, guild_id);
            // source_id INTEGER PRIMARY KEY AUTOINCREMENT, source_name VARCHAR NOT NULL, date_added BIGINT NOT NULL, hash BIGINT NOT NULL, guild_id BIGINT NOT NULL
            ctx().execute("INSERT INTO sources (source_name, date_added, guild_id, hash) VALUES (?, ?, ?, ?)", source.source_name, source.date_added, source.guild_id, source.source_hash);
            // set source id
            @Nullable Record result = ctx().fetchOne("SELECT source_id FROM sources WHERE source_name = ? AND date_added = ? AND guild_id = ?", source.source_name, source.date_added, source.guild_id);
            int source_id = (Integer) result.getValue("source_id");
            source = new EmbeddingSource(source_id, source.source_name, source.date_added, 0, source.guild_id);
            // add to map
            embeddingSources.put(source.source_id, source);
            return source;
        } else {
            return source;
        }
    }

    @Override
    public void updateSources(List<EmbeddingSource> sources) {
        load();
        ctx().transaction((TransactionalRunnable) -> {
            for (EmbeddingSource source : sources) {
                ctx().execute("UPDATE sources SET source_name = ?, date_added = ?, guild_id = ? WHERE source_id = ?",
                        source.source_name, source.date_added, source.guild_id, source.source_id);
            }
        });
    }

    private void loadSources() {
        ctx().select().from("sources").fetch().forEach(r -> {
            int source_id = r.get("source_id", Integer.class);
            String source_name = r.get("source_name", String.class);
            long date_added = r.get("date_added", Long.class);
            long guild_id = r.get("guild_id", Long.class);
            long hash = r.get("hash", Long.class);

            // embeddingSources is a map of guild_id to set<EmbeddingSource>
            EmbeddingSource source = new EmbeddingSource(source_id, source_name, date_added, hash, guild_id);
            embeddingSources.put(source.source_id, source);
        });
    }

    public synchronized void createTables() {
//        sources: long source_id, String source_name, long date_added, long guild_id
        createSourcesTable();
        // document_queue: source_id, prompt, converted, use_global_context, gpt_provider, user, error, date
        createDocumentQueueTable();
        // usage: model, usage, day
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
        // delete from expanded_text and sources and vector_sources
        int source_id = source.source_id;
        ctx().execute("DELETE FROM expanded_text WHERE source_id = ?", source_id);
        ctx().execute("DELETE FROM sources WHERE source_id = ?", source_id);
        ctx().execute("DELETE FROM vector_sources WHERE source_id = ?", source_id);

        embeddingSources.remove(source.source_id);
        unconvertedDocuments.remove(source_id);
    }

    @Override
    public float[] getEmbedding(String content) {
        return getEmbedding(getHash(content));
    }

    @Override
    public float[] getOrCreateEmbedding(IEmbedding provider, long embeddingHash, String embeddingText, EmbeddingSource source, boolean save, ThrowingConsumer<String> moderate) {
        float[] existing = getEmbedding(embeddingHash);
        if (existing == null) {
            if (moderate != null) {
                moderate.accept(embeddingText);
            }
            // fetch embedding
            existing = provider.fetchAndNormalize(embeddingText);
            // store
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
        for (EmbeddingSource src : allowedSources) srcIds.add(src.source_id);
        vectors.getDocumentsBySource(srcIds, true, new Consumer<VectorRow>() {
            @Override
            public void accept(VectorRow result) {
                EmbeddingSource source = result.sourceId == -1 ? null : embeddingSources.get(result.sourceId);
                if (source != null) {
                    source_hash_vector_consumer.accept(result);
                } else {
                    Logg.error("Source with id " + result.sourceId + " not found for hash " + result.id + " | " + result.text);
                }
            }
        });
    }

    public Map<Long, String> getContent(Set<Long> hashes) {
        Map<Long, String> result = new Long2ObjectOpenHashMap<>();
        List<Long> hashesSorted = new LongArrayList();
        // sort ascending
        hashesSorted.addAll(hashes);
        hashesSorted.sort(Long::compareTo);
        String query = "SELECT hash, description FROM vector_text WHERE hash IN (" + hashesSorted.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        ctx().fetch(query).forEach(r -> {
            long hash = r.get("hash", Long.class);
            String description = r.get("description", String.class);
            result.put(hash, description);
        });
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

    public Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate) {
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
    public List<EmbeddingInfo> getClosest(EmbeddingSource inputSource, String input, int top, Set<EmbeddingSource> allowedTypes, BiPredicate<EmbeddingSource, Long> sourceHashPredicate, ThrowingConsumer<String> moderate) {
        checkArgument(top > 0, "top must be > 0");
        PriorityQueue<EmbeddingInfo> largest = new PriorityQueue<>(top, new Comparator<EmbeddingInfo>() {
            @Override
            public int compare(EmbeddingInfo o1, EmbeddingInfo o2) {
                return Double.compare(o1.distance, o2.distance);
            }
        });

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
}
