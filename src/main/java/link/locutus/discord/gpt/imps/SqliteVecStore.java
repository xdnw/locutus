package link.locutus.discord.gpt.imps;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SqliteVecStore implements AutoCloseable {
    private final int dim;
    private final Connection conn;

    private static final boolean USE_COSINE_QUERY = false;

    public SqliteVecStore(Path dbFile, int dim) throws Exception {
        this.dim = dim;

        Path parent = dbFile.toAbsolutePath().getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        SQLiteConfig cfg = new SQLiteConfig();
        cfg.enableLoadExtension(true);
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toString(), cfg.toProperties());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
        }

        // Load vec0.dll from resources to a temp file, then load the extension
//        Path dll = extractResourceToTemp("/vec0.dll", "vec0-", ".dll");

        Path cacheDir = parent != null ? parent : Path.of(".");
        Path dll = SqliteVecFetcher.ensureLatestForCurrentPlatform(cacheDir);
        loadExtension(dll);

        try (Statement st = conn.createStatement()) {
            // vec table holds only the vector column
            st.execute("CREATE VIRTUAL TABLE IF NOT EXISTS vectors " +
                    "USING vec0(embedding float[" + dim + "])");

            // regular table holds metadata keyed by rowid from the vec table
            st.execute("CREATE TABLE IF NOT EXISTS vector_meta (" +
                    "id BIGINT PRIMARY KEY, " +
                    "source_id INT, " +
                    "label TEXT)");
        }
    }

    private static final Function<ResultSet, VectorRow> DEFAULT_WITH_VECTOR = rs -> {
        try {
            long id = rs.getLong(1);
            int sourceId = rs.getInt(2);
            String label = rs.getString(3);
            byte[] blob = rs.getBytes(4);
            float[] vec = fromBlob(blob);
            return new VectorRow(id, label, vec, sourceId, Double.NaN);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };

    private static final Function<ResultSet, VectorRow> DEFAULT_NO_VECTOR = rs -> {
        try {
            long id = rs.getLong(1);
            int sourceId = rs.getInt(2);
            String label = rs.getString(3);
            return new VectorRow(id, label, null, sourceId, Double.NaN);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };

    private static final Function<ResultSet, VectorRow> SEARCH_NO_VECTOR = rs -> {
        try {
            long id = rs.getLong(1);
            int sourceId = rs.getInt(2);
            String label = rs.getString(3);
            byte[] blob = rs.getBytes(4);
            float[] vec = fromBlob(blob);
            double dist = rs.getDouble(5);
            return new VectorRow(id, label, vec, sourceId, dist);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };

    private static final Function<ResultSet, VectorRow> SEARCH_WITH_VECTOR = rs -> {
        try {
            long id = rs.getLong(1);
            int sourceId = rs.getInt(2);
            String label = rs.getString(3);
            byte[] blob = rs.getBytes(4);
            float[] vec = fromBlob(blob);
            double dist = rs.getDouble(5);
            return new VectorRow(id, label, vec, sourceId, dist);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    };

    public void forEachResult(String query, boolean includeVector, ThrowingConsumer<PreparedStatement> onStmt, Function<ResultSet, VectorRow> constructor, Consumer<VectorRow> consumer) {
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            if (onStmt != null) {
                onStmt.accept(ps);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VectorRow row = constructor.apply(rs);
                    consumer.accept(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void getAllDocuments(boolean fetchVector, Consumer<VectorRow> consumer) throws SQLException {
        String query = fetchVector ?
                "SELECT m.id, m.source_id, m.label, v.embedding FROM vector_meta m JOIN vectors v ON m.id = v.rowid"
                : "SELECT m.id, m.source_id, m.label, NULL AS embedding FROM vector_meta m";
        Function<ResultSet, VectorRow> constructor = fetchVector ? DEFAULT_WITH_VECTOR : DEFAULT_NO_VECTOR;
        forEachResult(query, fetchVector, null, constructor, consumer);
    }

    public void getDocumentsBySource(int sourceId, boolean fetchVector, Consumer<VectorRow> consumer) {
        String query = fetchVector ?
                "SELECT m.id, m.source_id, m.label, v.embedding FROM vector_meta m JOIN vectors v ON m.id = v.rowid WHERE m.source_id = ?"
                : "SELECT m.id, m.source_id, m.label, NULL AS embedding FROM vector_meta m WHERE m.source_id = ?";
        Function<ResultSet, VectorRow> constructor = fetchVector ? DEFAULT_WITH_VECTOR : DEFAULT_NO_VECTOR;
        forEachResult(query, fetchVector, ps -> {
            ps.setInt(1, sourceId);
        }, constructor, consumer);
    }

    public int countBySource(Set<Integer> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) return 0;
        if (sourceIds.size() == 1) {
            String query = "SELECT COUNT(*) FROM vector_meta WHERE source_id = ?";
            final int[] count = {0};
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, sourceIds.iterator().next());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        count[0] = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("SELECT COUNT(*) FROM vector_meta WHERE source_id IN (");
        int i = 0;
        for (Integer id : sourceIds) {
            if (i > 0) sb.append(',');
            sb.append('?');
            i++;
        }
        sb.append(')');
        String query = sb.toString();
        final int[] count = {0};
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            int idx = 1;
            for (Integer id : sourceIds) {
                ps.setInt(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count[0] = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return count[0];
    }

    public void getDocumentsBySource(Set<Integer> sourceIds, boolean fetchVector, Consumer<VectorRow> consumer) {
        if (sourceIds == null || sourceIds.isEmpty()) return;
        if (sourceIds.size() == 1) {
            getDocumentsBySource(sourceIds.iterator().next(), fetchVector, consumer);
            return;
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append("SELECT m.id, m.source_id, m.label, ");
        if (fetchVector) {
            sb.append("v.embedding ");
        } else {
            sb.append("NULL AS embedding ");
        }
        sb.append("FROM vector_meta m ");
        if (fetchVector) {
            sb.append("JOIN vectors v ON m.id = v.rowid ");
        }
        sb.append("WHERE m.source_id IN (");
        int i = 0;
        for (Integer id : sourceIds) {
            if (i > 0) sb.append(',');
            sb.append('?');
            i++;
        }
        sb.append(')');
        String query = sb.toString();
        Function<ResultSet, VectorRow> constructor = fetchVector ? DEFAULT_WITH_VECTOR : DEFAULT_NO_VECTOR;
        forEachResult(query, fetchVector, ps -> {
            int idx = 1;
            for (Integer id : sourceIds) {
                ps.setInt(idx++, id);
            }
        }, constructor, consumer);
    }

    public void deleteMissing(int sourceId, Set<Long> idSet) {
        boolean oldAuto = false;
        try {
            oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Build optional NOT IN clause
            String notInClause = "";
            List<Long> ids = new ArrayList<>();
            if (idSet != null && !idSet.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append(" AND id NOT IN (");
                int i = 0;
                for (Long id : idSet) {
                    if (i++ > 0) sb.append(',');
                    sb.append('?');
                    ids.add(id);
                }
                sb.append(')');
                notInClause = sb.toString();
            }

            // 1) Delete vectors by rowid via subquery into vector_meta (before meta delete)
            String sqlVec = "DELETE FROM vectors " +
                    "WHERE rowid IN (" +
                    "  SELECT id FROM vector_meta WHERE source_id = ?" + notInClause +
                    ")";
            try (PreparedStatement ps = conn.prepareStatement(sqlVec)) {
                int idx = 1;
                ps.setInt(idx++, sourceId);
                for (Long id : ids) ps.setLong(idx++, id);
                ps.executeUpdate();
            }

            // 2) Delete metadata rows
            String sqlMeta = "DELETE FROM vector_meta WHERE source_id = ?" + notInClause;
            try (PreparedStatement ps = conn.prepareStatement(sqlMeta)) {
                int idx = 1;
                ps.setInt(idx++, sourceId);
                for (Long id : ids) ps.setLong(idx++, id);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { conn.setAutoCommit(oldAuto); } catch (SQLException ignore) {}
        }
    }

    public int addDocumentIfNotExists(String text, Long hash, float[] vector, int sourceId) {
        return addDocumentIfNotExists(text, hash, () -> vector, sourceId);
    }

    public int addDocumentIfNotExists(String text, Long hash, Supplier<float[]> vectorSupplier, int sourceId) {
        try {
            boolean oldAuto = conn.getAutoCommit();
            try {
                if (hash == null) {
                    hash = StringMan.hash(text);
                }
                conn.setAutoCommit(false);
                int affected;
                try (PreparedStatement pm = conn.prepareStatement(
                        "INSERT OR IGNORE INTO vector_meta(id, source_id, label) VALUES (?, ?, ?)")) {
                    pm.setLong(1, hash);
                    pm.setInt(2, sourceId);
                    pm.setString(3, text);
                    affected = pm.executeUpdate();
                }
                if (affected > 0) {
                    float[] embedding = vectorSupplier.get();
                    requireDim(embedding);
                    try (PreparedStatement pv = conn.prepareStatement(
                            "INSERT INTO vectors(rowid, embedding) VALUES (?, vec_f32(?))")) {
                        pv.setLong(1, hash);                  // bind as rowid
                        pv.setString(2, toJsonArray(embedding));
                        pv.executeUpdate();
                    }
                }
                conn.commit();
                return affected;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAuto);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int addDocumentsIfNotExists(List<VectorRow> entries, Function<String, float[]> vectorFunc, int sourceId) {
        if (entries == null || entries.isEmpty()) return 0;

        try {
            boolean oldAuto = conn.getAutoCommit();
            conn.setAutoCommit(false);

            int[] counts;
            // 1) Insert meta rows (OR IGNORE) in batch
            try (PreparedStatement pm = conn.prepareStatement(
                    "INSERT OR IGNORE INTO vector_meta(id, source_id, label) VALUES (?, ?, ?)")) {
                for (VectorRow e : entries) {
                    String label = e.text;
                    long id = (e.id != 0L) ? e.id : StringMan.hash(label);
                    pm.setLong(1, id);
                    pm.setInt(2, sourceId);
                    pm.setString(3, label);
                    pm.addBatch();
                }
                counts = pm.executeBatch();
            }

            // 2) For the rows actually inserted, compute/add vectors
            int inserted = 0;
            try (PreparedStatement pv = conn.prepareStatement(
                    "INSERT INTO vectors(rowid, embedding) VALUES (?, vec_f32(?))")) {
                for (int i = 0; i < entries.size(); i++) {
                    int c = counts[i];
                    boolean newlyInserted = (c > 0) || (c == Statement.SUCCESS_NO_INFO);
                    if (!newlyInserted) continue;

                    VectorRow e = entries.get(i);
                    String label = e.text;
                    long id = (e.id != 0L) ? e.id : StringMan.hash(label);

                    float[] embedding = (e.vector != null) ? e.vector
                            : (vectorFunc != null ? vectorFunc.apply(label) : null);
                    if (embedding == null) {
                        throw new IllegalArgumentException("Missing embedding for label: " + label);
                    }
                    requireDim(embedding);

                    pv.setLong(1, id);
                    pv.setString(2, toJsonArray(embedding));
                    pv.addBatch();
                    inserted++;
                }
                if (inserted > 0) {
                    pv.executeBatch();
                }
            }

            conn.commit();
            conn.setAutoCommit(oldAuto);
            return inserted;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
            throw new RuntimeException(e);
        } finally {
            try { if (!conn.getAutoCommit()) conn.setAutoCommit(true); } catch (SQLException ignore) {}
        }
    }

    public List<VectorRow> searchSimilarReranked(float[] query, int k, boolean fetchVector) {
        int ratio = 5;
        int knnK = k * 5;
        List<VectorRow> results = new ObjectArrayList<>(knnK);
        searchSimilar(query, knnK, fetchVector, results::add);
        return GPTUtil.rerankTopKByCosine_mutable(results, query, k);
    }

    public void searchSimilar(float[] query, int k, boolean fetchVector, Consumer<VectorRow> consumer) {
        requireDim(query);

        String sqlQuery;
        if (USE_COSINE_QUERY) {
            sqlQuery = fetchVector
                    ? "SELECT m.id, m.source_id, m.label, v.embedding, " +
                    "vec_distance_cosine(v.embedding, vec_f32(?)) AS distance " +
                    "FROM vectors v " +
                    "JOIN vector_meta m ON m.id = v.rowid " +
                    "ORDER BY distance " +
                    "LIMIT ?"
                    : "SELECT m.id, m.source_id, m.label, NULL AS embedding, " +
                    "vec_distance_cosine(v.embedding, vec_f32(?)) AS distance " +
                    "FROM vectors v " +
                    "JOIN vector_meta m ON m.id = v.rowid " +
                    "ORDER BY distance " +
                    "LIMIT ?";
        } else {
            sqlQuery = fetchVector
                    ? "SELECT m.id, m.source_id, m.label, v.embedding, distance " +
                    "FROM vectors v " +
                    "JOIN vector_meta m ON m.id = v.rowid " +
                    "WHERE v.embedding MATCH vec_f32(?) AND k = ? " +
                    "ORDER BY distance"
                    : "SELECT m.id, m.source_id, m.label, NULL AS embedding, distance " +
                    "FROM vectors v " +
                    "JOIN vector_meta m ON m.id = v.rowid " +
                    "WHERE v.embedding MATCH vec_f32(?) AND k = ? " +
                    "ORDER BY distance";
        }

        Function<ResultSet, VectorRow> constructor = fetchVector ? SEARCH_WITH_VECTOR : SEARCH_NO_VECTOR;
        forEachResult(sqlQuery, fetchVector, ps -> {
            ps.setString(1, toJsonArray(query));
            ps.setInt(2, k); // LIMIT ? for cosine, k = ? for KNN
        }, constructor, consumer);
    }

    public List<VectorRow> searchSimilarReranked(float[] query, int k, boolean fetchVector, Set<Integer> sourceIds) {
        int ratio = 5;
        int knnK = k * 5;
        List<VectorRow> results = new ObjectArrayList<>(knnK);
        searchSimilar(query, knnK, fetchVector, sourceIds, results::add);
        return GPTUtil.rerankTopKByCosine_mutable(results, query, k);
    }

    public void searchSimilar(float[] query, int k, boolean fetchVector, Set<Integer> sourceIds, Consumer<VectorRow> consumer) {
        requireDim(query);
        if (sourceIds == null || sourceIds.isEmpty()) {
            searchSimilar(query, k, fetchVector, consumer);
            return;
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("SELECT m.id, m.source_id, m.label, ");
        sb.append(fetchVector ? "v.embedding, " : "NULL AS embedding, ");

        if (USE_COSINE_QUERY) {
            sb.append("vec_distance_cosine(v.embedding, vec_f32(?)) AS distance ");
            sb.append("FROM vectors v JOIN vector_meta m ON m.id = v.rowid ");
            sb.append("WHERE m.source_id IN (");
        } else {
            sb.append("distance ");
            sb.append("FROM vectors v JOIN vector_meta m ON m.id = v.rowid ");
            sb.append("WHERE v.embedding MATCH vec_f32(?) AND m.source_id IN (");
        }

        int i = 0;
        for (int ignored : sourceIds) {
            if (i++ > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(") ");
        if (USE_COSINE_QUERY) {
            sb.append("ORDER BY distance LIMIT ?");
        } else {
            sb.append("AND k = ? ORDER BY distance");
        }

        String sqlQuery = sb.toString();
        Function<ResultSet, VectorRow> constructor = fetchVector ? SEARCH_WITH_VECTOR : SEARCH_NO_VECTOR;

        forEachResult(sqlQuery, fetchVector, ps -> {
            int idx = 1;
            ps.setString(idx++, toJsonArray(query));
            for (Integer id : sourceIds) {
                ps.setInt(idx++, id);
            }
            ps.setInt(idx, k); // LIMIT ? (cosine) or k = ? (KNN)
        }, constructor, consumer);
    }

    public List<VectorRow> searchSimilarReranked(float[] query, int k, boolean fetchVector, Set<Integer> sourceIds, Predicate<VectorRow> predicate) {
        int ratio = 5;
        int knnK = k * 5;
        List<VectorRow> results = new ObjectArrayList<>(knnK);
        searchSimilar(query, knnK, fetchVector, sourceIds, predicate, results::add);
        return GPTUtil.rerankTopKByCosine_mutable(results, query, k);
    }


    public void searchSimilar(float[] query,
                              int k,
                              boolean fetchVector,
                              Set<Integer> sourceIds,
                              Predicate<VectorRow> predicate,
                              Consumer<VectorRow> consumer) {
        requireDim(query);

        StringBuilder sb = new StringBuilder(256);
        sb.append("SELECT m.id, m.source_id, m.label, ");
        sb.append(fetchVector ? "v.embedding, " : "NULL AS embedding, ");

        if (USE_COSINE_QUERY) {
            sb.append("vec_distance_cosine(v.embedding, vec_f32(?)) AS distance ");
            sb.append("FROM vectors v JOIN vector_meta m ON m.id = v.rowid WHERE 1=1 ");
        } else {
            sb.append("distance ");
            sb.append("FROM vectors v JOIN vector_meta m ON m.id = v.rowid ");
            sb.append("WHERE v.embedding MATCH vec_f32(?) ");
        }

        if (sourceIds != null && !sourceIds.isEmpty()) {
            sb.append("AND m.source_id IN (");
            int i = 0;
            for (int ignored : sourceIds) {
                if (i++ > 0) sb.append(',');
                sb.append('?');
            }
            sb.append(") ");
        }

        if (USE_COSINE_QUERY) {
            sb.append("ORDER BY distance LIMIT ?");
        } else {
            sb.append("AND k = ? ORDER BY distance");
        }

        String sql = sb.toString();
        Function<ResultSet, VectorRow> constructor = fetchVector ? SEARCH_WITH_VECTOR : SEARCH_NO_VECTOR;

        final int desiredK = Math.max(1, k);
        int sqlK = Math.max(desiredK * 4, 32);     // initial oversample
        final int maxK = Math.max(desiredK * 32, 1024); // cap to avoid runaway scans

        int accepted = 0;
        HashSet<Long> seen = new HashSet<>();

        while (accepted < desiredK) {
            try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
                int idx = 1;
                ps.setString(idx++, toJsonArray(query));
                if (sourceIds != null && !sourceIds.isEmpty()) {
                    for (Integer id : sourceIds) {
                        ps.setInt(idx++, id);
                    }
                }
                ps.setInt(idx++, sqlK);

                int returned = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        returned++;
                        VectorRow row = constructor.apply(rs);
                        // Deduplicate across rounds
                        if (!seen.add(row.id)) continue;

                        if (predicate == null || predicate.test(row)) {
                            consumer.accept(row);
                            if (++accepted >= desiredK) {
                                return;
                            }
                        }
                    }
                }

                // If the DB returned fewer than requested, there are no more rows to fetch.
                if (returned < sqlK) {
                    return;
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            // Increase k and try again
            if (sqlK >= maxK) {
                return; // stop if we've reached the cap
            }
            sqlK = Math.min(sqlK * 2, maxK);
        }
    }

    @Override
    public void close() throws Exception {
        if (conn != null) conn.close();
    }

    // --- Helpers ---

    private void loadExtension(Path dll) throws SQLException {
        // Try with explicit entrypoint first (vec0 uses sqlite3_vec_init), then fallback
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT load_extension(?, 'sqlite3_vec_init')")) {
            ps.setString(1, dll.toString());
            ps.execute();
        } catch (SQLException e) {
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT load_extension(?)")) {
                ps2.setString(1, dll.toString());
                ps2.execute();
            }
        }
    }

    private static Path extractResourceToTemp(String resourcePath, String prefix, String suffix) throws IOException {
        Path tmp = Files.createTempFile(prefix, suffix);
        tmp.toFile().deleteOnExit();
        try (InputStream in = SqliteVecStore.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp;
    }

    private void requireDim(float[] v) {
        if (v == null || v.length != dim) {
            throw new IllegalArgumentException("Expected vector dim=" + dim + " but got " +
                    (v == null ? "null" : v.length));
        }
    }

    private static byte[] toBlob(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : v) bb.putFloat(f);
        return bb.array();
    }

    private static float[] fromBlob(byte[] blob) {
        if (blob == null) return null;
        FloatBuffer fb = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] out = new float[fb.remaining()];
        fb.get(out);
        return out;
    }

    public VectorRow getVectorById(long id, boolean fetchText, boolean fetchVector) {
        String sql = fetchVector
                ? "SELECT m.id, m.source_id, m.label, v.embedding FROM vector_meta m JOIN vectors v ON m.id = v.rowid WHERE m.id = ?"
                : "SELECT m.id, m.source_id, m.label, NULL AS embedding FROM vector_meta m WHERE m.id = ?";
        Function<ResultSet, VectorRow> constructor = fetchVector ? DEFAULT_WITH_VECTOR : DEFAULT_NO_VECTOR;
        final VectorRow[] result = {null};
        forEachResult(sql, fetchVector, ps -> {
            ps.setLong(1, id);
        }, constructor, r -> {
            result[0] = r;
        });
        return result[0];
    }

    private static String toJsonArray(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            // Use plain decimal to avoid scientific notation issues in parsers
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}