package link.locutus.discord.db;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.config.Settings;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import org.jooq.impl.SQLDataType;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AEmbeddingDatabase extends DBMainV3 implements IEmbeddingDatabase, Closeable {
    private Long2ObjectOpenHashMap<byte[]> embeddingsByContentHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<EmbeddingInfo> embeddingInfoByContentHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<EmbeddingInfo> embeddingInfoByIdTypeHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<String> hashContent = new Long2ObjectOpenHashMap<>();

    public record EmbeddingInfo(long contentHash, long type, String id) {

        @Override
        public int hashCode() {
            return Long.hashCode(contentHash);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof EmbeddingInfo other) {
                return other.contentHash == contentHash;
            }
            if (obj instanceof Long aLong) {
                return aLong == contentHash;
            }
            return false;
        }
    }

    public AEmbeddingDatabase(String name) throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, name, false);
        loadEmbeddings();
        loadContent();
    }

    private void loadEmbeddings() {
        ctx().select().from("embeddings_2").fetch().forEach(r -> {
            long hash = r.get("hash", Long.class);
            long type = r.get("type", Long.class);
            String id = r.get("id", String.class);
            byte[] data = r.get("data", byte[].class);

            long idTypeHash = getHash(type, id);
            EmbeddingInfo info = new EmbeddingInfo(hash, type, id);

            embeddingsByContentHash.put(hash, data);
            embeddingInfoByContentHash.put(hash, info);
            if (!id.isEmpty() && type >= 0) {
                embeddingInfoByIdTypeHash.put(idTypeHash, info);
            }
        });
    }

    private void loadContent() {
        ctx().select().from("content").fetch().forEach(r -> {
            long idTypeHash = r.get("hash", Long.class);
            String content = r.get("content", String.class);
            hashContent.put(idTypeHash, content);
        });
    }

    @Override
    public synchronized void createTables() {
        // embeddings
        ctx().createTableIfNotExists("embeddings_2")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("type", SQLDataType.BIGINT.notNull())
                .column("id", SQLDataType.VARCHAR.notNull())
                .column("data", SQLDataType.BINARY.notNull())
                .primaryKey("hash")
                .execute();

        // table content
        ctx().createTableIfNotExists("content")
                .column("hash", SQLDataType.BIGINT.notNull())
                .column("content", SQLDataType.VARCHAR.notNull())
                .primaryKey("hash")
                .execute();

        // if table `embeddings` exists
        try {
            AtomicInteger inserted = new AtomicInteger();
            try (ResultSet query = getConnection().getMetaData().getTables(null, null, "embeddings", null)) {
                if (query.next()) {
                    // iterate over all rows
                    ctx().select().from("embeddings").fetch().forEach(r -> {
                        // get hash
                        long hash = r.get("hash", Long.class);
                        // get type
                        long type = r.get("type", Long.class);
                        // get id
                        String id = r.get("id", String.class);
                        // get data
                        byte[] data = r.get("data", byte[].class);
                        double[] vectors = ArrayUtil.toDoubleArray(data);
                        float[] downCast = new float[vectors.length];
                        for (int i = 0; i < vectors.length; i++) {
                            downCast[i] = (float) vectors[i];
                        }
                        byte[] downCastBytes = ArrayUtil.toByteArray(downCast);
                        addEmbedding(hash, type, id, downCastBytes);
                        inserted.incrementAndGet();
                    });
                }
            }
            if (inserted.get() > 0) {
                System.out.println("Inserted " + inserted.get() + " embeddings");
                // drop old table
                ctx().dropTableIfExists("embeddings").execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public float[] getEmbedding(long hash) {
        byte[] data = embeddingsByContentHash.get(hash);
        return data == null ? null : ArrayUtil.toFloatArray(data);
    }

    public float[] getEmbedding(String content) {
        return getEmbedding(getHash(content));
    }

    public long getHash(long type, String id) {
        return getHash(type + ":" + id);
    }

    public float[] getEmbedding(int type, String id) {
        long typeIdHash = getHash(type, id);
        EmbeddingInfo existing = embeddingInfoByIdTypeHash.get(typeIdHash);
        if (existing != null) {
            byte[] data = embeddingsByContentHash.get(existing.contentHash);
            if (data != null) {
                return ArrayUtil.toFloatArray(data);
            }
        }
        return null;
    }

    public void setEmbedding(int type, @Nullable String id2, String content, float[] value, boolean saveContent) {
        long contentHash = getHash(content);

        EmbeddingInfo info;
        Long typeHash = null;
        if (id2 != null && !id2.isEmpty()) {
            typeHash = getHash(type, id2);
            info = embeddingInfoByIdTypeHash.get(typeHash);
        } else {
            info = embeddingInfoByContentHash.get(contentHash);
        }

        if (info != null) {
            if (info.contentHash == contentHash) {
                if (typeHash != null && (info.type != type || !id2.equalsIgnoreCase(info.id))) {
                    updateEmbedding(contentHash, type, id2);
                    info = new EmbeddingInfo(contentHash, type, id2);
                    embeddingInfoByContentHash.put(contentHash, info);
                    embeddingInfoByIdTypeHash.put(typeHash, info);
                }
                return;
            }
            synchronized (this) {
                // delete from database
                ctx().execute("DELETE FROM `embeddings` WHERE `hash` = ?", info.contentHash);

                // delete content if exists
                if (hashContent.remove(info.contentHash) != null) {
                    ctx().execute("DELETE FROM `content` WHERE `hash` = ?", info.contentHash);
                }
            }

            System.out.println("Delete different embedding");
            embeddingInfoByContentHash.remove(info.contentHash);
            hashContent.remove(info.contentHash);
            embeddingsByContentHash.remove(info.contentHash);
        }

        byte[] valueBytes = ArrayUtil.toByteArray(value);

        embeddingsByContentHash.put(contentHash, valueBytes);
        addEmbedding(contentHash, type, id2, valueBytes);
        if (saveContent) {
            addContent(contentHash, content);
            hashContent.put(contentHash, content);
        }

        EmbeddingInfo newInfo = new EmbeddingInfo(contentHash, type, id2);
        embeddingInfoByContentHash.put(contentHash, newInfo);
        if (typeHash != null) {
            embeddingInfoByIdTypeHash.put(typeHash, newInfo);
        }
    }

    private synchronized void updateEmbedding(long contentHash, int type, String id) {
        if (id == null) id = "";
        ctx().execute("UPDATE `embeddings_2` SET `type` = ?, `id` = ? WHERE `hash` = ?", type, id, contentHash);
    }

    private synchronized void addContent(long hash, String content) {
        ctx().execute("INSERT OR IGNORE INTO `content` (`hash`, `content`) VALUES (?, ?)", hash, content);
    }

    public static long getHash(String data) {
        BigInteger value = StringMan.hash_fnv1a_64(data.getBytes());
        value = value.add(BigInteger.valueOf(Long.MIN_VALUE));
        return value.longValueExact();
    }
//
    public synchronized void addEmbedding(long hash, long type, String id, byte[] data) {
        if (id == null) id = "";
        ctx().execute("INSERT OR REPLACE INTO `embeddings_2` (`hash`, `type`, `id`, `data`) VALUES (?, ?, ?, ?)", hash, type, id, data);
    }
}
