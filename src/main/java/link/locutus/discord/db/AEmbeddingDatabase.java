package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.jetbrains.annotations.Nullable;
import org.jooq.Record;
import org.jooq.impl.SQLDataType;

import java.io.Closeable;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static graphql.com.google.common.base.Preconditions.checkNotNull;

public abstract class AEmbeddingDatabase extends DBMainV3 implements IEmbeddingDatabase, Closeable {

    private final Long2ObjectOpenHashMap<float[]> vectors = new Long2ObjectOpenHashMap<>();
    private Map<Integer, Set<Long>> hashesBySource = new Int2ObjectOpenHashMap<>();

    private Map<Long, Set<EmbeddingSource>> embeddingSourcesByGuild = new ConcurrentHashMap<>();

    /*
    PW binding needs to register all the commands/settings etc.
    then it deletes the missing sources
     */

    public void registerHashes(EmbeddingSource source, Set<Long> hashes, boolean deleteAbsent) {
        checkNotNull(source);
        if (hashes.isEmpty()) {
            if (deleteAbsent) {
                throw new IllegalArgumentException("Cannot delete absent hashes if no hashes are provided");
            }
            return;
        }
        Set<Long> existing = hashesBySource.get(source);
        if (existing == null) {
            existing = new LongOpenHashSet();
            hashesBySource.put(source.source_id, existing);
            for (long hash : hashes) {
                existing.add(hash);
                saveVectorSources(hash, source.source_id);
            }
        } else {
            Iterator<Long> iter = existing.iterator();
            while (iter.hasNext()) {
                long hash = iter.next();
                if (!hashes.contains(hash)) {
                    iter.remove();
                    if (deleteAbsent) {
                        deleteHash(source.source_id, hash);
                    }
                }
            }
            for (long hash : hashes) {
                if (!existing.contains(hash)) {
                    existing.add(hash);
                    saveVectorSources(hash, source.source_id);
                }
            }
        }
    }

    public void deleteHash(int source, long hash) {
        ctx().execute("DELETE FROM vector_sources WHERE hash = ? AND source_id = ?", hash, source);
    }

    private void createVectorsTable() {
        ctx().createTableIfNotExists("vectors")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("data", SQLDataType.BINARY.notNull())
                .primaryKey("hash")
                .execute();
    }

    public synchronized void saveVector(long hash, float[] vector) {
        byte[] data = ArrayUtil.toByteArray(vector);
        vectors.put(hash, vector);
        ctx().execute("INSERT INTO vectors (hash, data) VALUES (?, ?)", hash, data);
    }

    private void createVectorTextTable() {
        ctx().createTableIfNotExists("vector_text")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("description", SQLDataType.VARCHAR.notNull())
                .primaryKey("hash")
                .execute();
    }

    public synchronized void saveVectorText(long hash, String description) {
        ctx().execute("INSERT INTO vector_text (hash, description) VALUES (?, ?)", hash, description);
    }

    private void createExpandedTextTable() {
        ctx().createTableIfNotExists("expanded_text")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("source_id", SQLDataType.BIGINT.notNull())
                .column("body", SQLDataType.VARCHAR.notNull())
                .primaryKey("hash", "source_id")
                .execute();
    }

    public synchronized void saveExpandedText(int source_id, long hash, String body) {
        ctx().execute("INSERT INTO expanded_text (source_id, hash, body) VALUES (?, ?, ?)", source_id, hash, body);
    }

    private void createVectorSourcesTable() {
        ctx().createTableIfNotExists("vector_sources")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("source_id", SQLDataType.BIGINT.notNull())
                .primaryKey("hash", "source_id")
                .execute();
    }

    public synchronized void saveVectorSources(long hash, int source_id) {
        hashesBySource.computeIfAbsent(source_id, k -> new LongOpenHashSet()).add(hash);
        ctx().execute("INSERT INTO vector_sources (source_id, hash) VALUES (?, ?)", source_id, hash);
    }

    private void createSourcesTable() {
        ctx().execute("CREATE TABLE IF NOT EXISTS sources (source_id INTEGER PRIMARY KEY AUTOINCREMENT, source_name VARCHAR NOT NULL, date_added BIGINT NOT NULL, guild_id BIGINT NOT NULL)");
    }

    @Override
    public Map<Long, Set<EmbeddingSource>> getEmbeddingSources() {
        return embeddingSourcesByGuild;
    }

    @Override
    public synchronized EmbeddingSource getOrCreateSource(String name, long guild_id) {
        name = name.toLowerCase();
        // get existing
        Set<EmbeddingSource> sourcesByGuild = embeddingSourcesByGuild.get(guild_id);
        EmbeddingSource source = null;
        if (sourcesByGuild != null) {
            for (EmbeddingSource other : sourcesByGuild) {
                if (other.source_name.equals(name)) {
                    source = other;
                    break;
                }
            }
        }

        if (source == null) {
            long date_added = System.currentTimeMillis();
            // create
            source = new EmbeddingSource(-1, name, date_added, guild_id);
            ctx().execute("INSERT INTO sources (source_name, date_added, guild_id) VALUES (?, ?, ?)", source.source_name, source.date_added, source.guild_id);
            // set source id
            @Nullable Record result = ctx().fetchOne("SELECT source_id FROM sources WHERE source_name = ? AND date_added = ? AND guild_id = ?", source.source_name, source.date_added, source.guild_id);
            int source_id = (Integer) result.getValue("source_id");
            source = new EmbeddingSource(source_id, source.source_name, source.date_added, source.guild_id);
            // add to map
            embeddingSourcesByGuild.computeIfAbsent(source.guild_id, k -> new HashSet<>()).add(source);
            return source;
        } else {
            return source;
        }
    }

    private void importLegacyDate() {
        ctx().select().from("embeddings_2").fetch().forEach(r -> {
            long hash = r.get("hash", Long.class);
            byte[] data = r.get("data", byte[].class);
            String id = r.get("id", String.class);
            ctx().execute("INSERT INTO vectors (hash, data) VALUES (?, ?)", hash, data);
            ctx().execute("INSERT INTO vector_text (hash, description) VALUES (?, ?)", hash, id);
        });
        ctx().dropTableIfExists("embeddings_2").execute();
    }

    public void loadVectors() {
            ctx().select().from("vectors").fetch().forEach(r -> {
            long hash = r.get("hash", Long.class);
            byte[] data = r.get("data", byte[].class);
            float[] vector = ArrayUtil.toFloatArray(data);
            vectors.put(hash, vector);
        });
    }

    public void loadHashesBySource() {
        ctx().select().from("vector_sources").fetch().forEach(r -> {
            long hash = r.get("hash", Long.class);
            int source_id = r.get("source_id", Integer.class);
            Set<Long> hashes = hashesBySource.get(source_id);
            if (hashes == null) {
                hashes = new LongOpenHashSet();
                hashesBySource.put(source_id, hashes);
            }
            hashes.add(hash);
        });
    }

    public void loadSources() {
        ctx().select().from("sources").fetch().forEach(r -> {
            int source_id = r.get("source_id", Integer.class);
            String source_name = r.get("source_name", String.class);
            long date_added = r.get("date_added", Long.class);
            long guild_id = r.get("guild_id", Long.class);

            // embeddingSources is a map of guild_id to set<EmbeddingSource>
            EmbeddingSource source = new EmbeddingSource(source_id, source_name, date_added, guild_id);
            embeddingSourcesByGuild.computeIfAbsent(guild_id, k -> new HashSet<>()).add(source);
        });
    }

    @Override
    public synchronized void createTables() {
        // vectors: long hash, byte[] data
        createVectorsTable();
        //        vector_text: long hash, String description
        createVectorTextTable();
        //        expanded_text: long hash, long source_id, String body primary key is (hash, source_id)
        createExpandedTextTable();
//        vector_sources long hash, long source_id
        createVectorSourcesTable();
//        sources: long source_id, String source_name, long date_added, long guild_id
        createSourcesTable();

        // import old data
        importLegacyDate();

        loadVectors();

        loadHashesBySource();

        loadSources();
    }

    public AEmbeddingDatabase(String name) throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, name, false);
        createTables();
    }

    public float[] getEmbedding(long hash) {
        float[] vector = vectors.get(hash);
        return vector == null ? null : vector;
    }

    @Override
    public float[] getEmbedding(String content) {
        return getEmbedding(getHash(content));
    }

    @Override
    public float[] getOrCreateEmbedding(String content, EmbeddingSource source) {
        long hash = getHash(content);
        float[] existing = getEmbedding(hash);
        if (existing == null) {
            // fetch embedding
            existing = fetchEmbedding(content);
            // store
            saveVector(hash, existing);
        }
        Set<Long> hashes = hashesBySource.get(source.source_id);
        if (hashes == null || !hashes.contains(hash)) {
            saveVectorSources(hash, source.source_id);
        }
        return existing;
    }

    public static long getHash(String data) {
        BigInteger value = StringMan.hash_fnv1a_64(data.getBytes());
        value = value.add(BigInteger.valueOf(Long.MIN_VALUE));
        return value.longValueExact();
    }

    public void iterateVectors(Set<EmbeddingSource> allowedSources, TriConsumer<Integer, Long, float[]> source_hash_vector_consumer) {
        Set<Integer> sources = new IntOpenHashSet();
        for (EmbeddingSource allowedSource : allowedSources) {
            sources.add(allowedSource.source_id);
        }
        for (int source_id : sources) {
            Set<Long> hashes = hashesBySource.get(source_id);
            if (hashes != null && !hashes.isEmpty()) {
                for (long hash : hashes) {
                    float[] vector = vectors.get(hash);
                    source_hash_vector_consumer.consume(source_id, hash, vector);
                }
            }
        }
    }

    public Map<Long, String> getContent(Set<Long> hashes) {
        Map<Long, String> result = new Long2ObjectOpenHashMap<>();
        List<Long> hashesSorted = new LongArrayList();
        // sort ascending
        hashesSorted.addAll(hashes);
        hashesSorted.sort(Long::compareTo);
        String query = "SELECT hash, description FROM vector_text WHERE hash IN (" + hashes.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
        ctx().fetch(query).forEach(r -> {
            long hash = r.get("hash", Long.class);
            String description = r.get("description", String.class);
            result.put(hash, description);
        });
        return result;
    }

    public Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate) {
        Set<EmbeddingSource> result = new LinkedHashSet<>();
        for (Map.Entry<Long, Set<EmbeddingSource>> entry : embeddingSourcesByGuild.entrySet()) {
            if (guildPredicateOrNull == null || guildPredicateOrNull.test(entry.getKey())) {
                for (EmbeddingSource source : entry.getValue()) {
                    if (sourcePredicate == null || sourcePredicate.test(source)) {
                        result.add(source);
                    }
                }
            }
        }
        return result;
    }
}
