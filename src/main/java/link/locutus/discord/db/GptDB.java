package link.locutus.discord.db;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import org.jooq.impl.SQLDataType;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.sql.SQLException;

public class GptDB extends DBMainV3 {
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

    public GptDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "gpt", false);
        loadEmbeddings();
        loadContent();
    }

    private void loadEmbeddings() {
        ctx().select().from("embeddings").fetch().forEach(r -> {
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

            /*
             private Long2ObjectOpenHashMap<byte[]> embeddingsByContentHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<EmbeddingInfo> embeddingInfoByContentHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<EmbeddingInfo> embeddingInfoByIdTypeHash = new Long2ObjectOpenHashMap<>();
    private Long2ObjectOpenHashMap<String> hashContent = new Long2ObjectOpenHashMap<>();
             */
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
    public void createTables() {
        // embeddings
        ctx().createTableIfNotExists("embeddings")
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
    }

    public double[] getEmbedding(long hash) {
        byte[] data = embeddingsByContentHash.get(hash);
        return data == null ? null : ArrayUtil.toDoubleArray(data);
    }

    public double[] getEmbedding(String content) {
        return getEmbedding(getHash(content));
    }

    public long getHash(long type, String id) {
        return getHash(type + ":" + id);
    }

    public double[] getEmbedding(int type, String id) {
        long typeIdHash = getHash(type, id);
        EmbeddingInfo existing = embeddingInfoByIdTypeHash.get(typeIdHash);
        if (existing != null) {
            byte[] data = embeddingsByContentHash.get(existing.contentHash);
            if (data != null) {
                return ArrayUtil.toDoubleArray(data);
            }
        }
        return null;
    }

    public void setEmbedding(int type, @Nullable String id2, String content, double[] value, boolean saveContent) {
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
            // delete from database
            ctx().execute("DELETE FROM `embeddings` WHERE `hash` = ?", info.contentHash);

            // delete content if exists
            if (hashContent.remove(info.contentHash) != null) {
                ctx().execute("DELETE FROM `content` WHERE `hash` = ?", info.contentHash);
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

    private void updateEmbedding(long contentHash, int type, String id) {
        if (id == null) id = "";
        ctx().execute("UPDATE `embeddings` SET `type` = ?, `id` = ? WHERE `hash` = ?", type, id, contentHash);
    }

    private void addContent(long hash, String content) {
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
        ctx().execute("INSERT OR REPLACE INTO `embeddings` (`hash`, `type`, `id`, `data`) VALUES (?, ?, ?, ?)", hash, type, id, data);
    }



}
