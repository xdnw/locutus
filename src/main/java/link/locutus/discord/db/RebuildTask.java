package link.locutus.discord.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class RebuildTask {
    private final File file;
    private final Runnable closeDatabaseCallback;
    private final Runnable reopenDatabaseCallback;

    public enum RecoveryMode {
        VACUUM_INTO,   // healthy DB rebuild/compact
        SQLITE_RECOVER, // real corruption recovery via sqlite3 .recover
        JDBC_COPY      // Java-only best-effort salvage fallback
    }

    public RebuildTask(File dbFile, Runnable closeDatabaseCallback, Runnable reopenDatabaseCallback) {
        this.file = dbFile;
        this.closeDatabaseCallback = closeDatabaseCallback;
        this.reopenDatabaseCallback = reopenDatabaseCallback;
    }

    // Adapt these two if your wrapper behaves differently.
    private void closeDatabase() throws Exception {
        if (this.closeDatabaseCallback != null) {
            try {
                closeDatabaseCallback.run();
            } finally {
            }
        }
    }

    private void reopenDatabase() throws Exception {
        reopenDatabaseCallback.run();
    }

    private static final String SQLITE3_BINARY = "sqlite3";
    private static final int BUSY_TIMEOUT_MS = 10_000;

    public boolean rebuild(RecoveryMode mode) {
        return rebuild(mode, false);
    }

    public boolean rebuild(RecoveryMode mode, boolean fullIntegrityCheck) {
        Path source = file.toPath().toAbsolutePath();
        String baseName = source.getFileName().toString();
        Path temp = source.resolveSibling(baseName + ".rebuild.tmp");
        Path backup = source.resolveSibling(baseName + ".backup-" + timestamp());

        boolean reopened = false;

        try {
            // Stop using the app's shared connection before doing any file-level work.
            closeDatabase();

            // Clean up any previous temp output.
            deleteDatabaseFiles(temp);

            switch (mode) {
                case VACUUM_INTO:
                    vacuumInto(source, temp);
                    break;
                case SQLITE_RECOVER:
                    sqliteRecover(source, temp);
                    break;
                case JDBC_COPY:
                    jdbcCopyInto(source, temp);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported mode: " + mode);
            }

            validateDatabase(temp, fullIntegrityCheck);
            swapDatabaseFiles(source, temp, backup);

            reopenDatabase();
            reopened = true;

            System.err.println("SQLite maintenance complete.");
            System.err.println("Mode:   " + mode);
            System.err.println("Live:   " + source);
            System.err.println("Backup: " + backup);
            return true;

        } catch (Exception e) {
            System.err.println("SQLite maintenance FAILED.");
            System.err.println("Mode: " + mode);
            System.err.println("DB:   " + source);
            e.printStackTrace();
            return false;

        } finally {
            try {
                deleteDatabaseFiles(temp);
            } catch (Exception ignored) {
            }

            if (!reopened) {
                try {
                    if (Files.exists(source)) {
                        reopenDatabase();
                    }
                } catch (Exception reopenEx) {
                    System.err.println("Failed to reopen DB after maintenance failure:");
                    reopenEx.printStackTrace();
                }
            }
        }
    }

/* =========================
   Mode 1: healthy rebuild
   ========================= */

    private void vacuumInto(Path source, Path temp) throws SQLException {
        try (Connection conn = openDirectConnection(source);
             Statement stmt = conn.createStatement()) {

            stmt.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);

            // Best effort; okay if it fails.
            try {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException e) {
                System.err.println("wal_checkpoint failed before VACUUM INTO (continuing): " + e.getMessage());
            }

            stmt.execute("VACUUM INTO " + sqlString(temp));
        }
    }

/* ====================================
   Mode 2: real recovery using sqlite3
   ==================================== */

    private void sqliteRecover(Path source, Path temp) throws Exception {
        deleteDatabaseFiles(temp);

        Process recoverOut = new ProcessBuilder(
                SQLITE3_BINARY,
                source.toString(),
                ".recover"
        ).start();

        Process importIn = new ProcessBuilder(
                SQLITE3_BINARY,
                temp.toString()
        ).start();

        StringBuilder recoverErr = new StringBuilder();
        StringBuilder importErr = new StringBuilder();
        AtomicReference<Throwable> pipeError = new AtomicReference<>();

        Thread recoverErrThread = drainTextAsync(recoverOut.getErrorStream(), recoverErr, "sqlite-recover-stderr");
        Thread importErrThread = drainTextAsync(importIn.getErrorStream(), importErr, "sqlite-import-stderr");

        Thread pipeThread = new Thread(() -> {
            try (InputStream in = recoverOut.getInputStream();
                 OutputStream out = importIn.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();
            } catch (Throwable t) {
                pipeError.set(t);
            }
        }, "sqlite-recover-pipe");

        pipeThread.setDaemon(true);
        pipeThread.start();

        int recoverExit = recoverOut.waitFor();
        pipeThread.join();
        int importExit = importIn.waitFor();

        recoverErrThread.join();
        importErrThread.join();

        if (pipeError.get() != null) {
            throw new IOException("Failed piping .recover output into sqlite3 import", pipeError.get());
        }

        if (recoverExit != 0 || importExit != 0) {
            throw new IOException(
                    "sqlite3 .recover failed. recoverExit=" + recoverExit +
                            ", importExit=" + importExit +
                            "\nrecover stderr:\n" + recoverErr +
                            "\nimport stderr:\n" + importErr
            );
        }

        if (!Files.exists(temp) || Files.size(temp) == 0L) {
            throw new IOException("sqlite3 .recover produced no usable database: " + temp);
        }
    }

/* ==========================================
   Mode 3: Java-only best-effort JDBC salvage
   ========================================== */

    private void jdbcCopyInto(Path source, Path temp) throws SQLException, IOException {
        deleteDatabaseFiles(temp);

        try (Connection conn = openDirectConnection(temp);
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            stmt.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
            stmt.execute("PRAGMA foreign_keys=OFF");
            stmt.execute("ATTACH DATABASE " + sqlString(source) + " AS src");

            try {
                // Preserve important DB-level settings before creating tables.
                copyBasicPragmasForJdbcClone(conn);

                // Speed up bulk import into the temp DB.
                try { stmt.execute("PRAGMA journal_mode=OFF"); } catch (SQLException ignored) {}
                try { stmt.execute("PRAGMA synchronous=OFF"); } catch (SQLException ignored) {}
                try { stmt.execute("PRAGMA temp_store=MEMORY"); } catch (SQLException ignored) {}

                createObjectsFromAttachedSource(conn, "table");
                copyAllTableDataFromAttachedSource(conn);
                copySqliteSequence(conn);
                createObjectsFromAttachedSource(conn, "index");
                createObjectsFromAttachedSource(conn, "view");
                createObjectsFromAttachedSource(conn, "trigger");
                copyTrailingPragmasForJdbcClone(conn);

                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }

                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException("JDBC_COPY failed", e);
            } finally {
                try (Statement detach = conn.createStatement()) {
                    detach.execute("DETACH DATABASE src");
                } catch (SQLException e) {
                    System.err.println("DETACH src failed: " + e.getMessage());
                }
            }
        }
    }

    private void copyBasicPragmasForJdbcClone(Connection conn) {
        Integer pageSize = queryIntPragma(conn, "src.page_size");
        Integer autoVacuum = queryIntPragma(conn, "src.auto_vacuum");
        String encoding = queryStringPragma(conn, "src.encoding");

        try (Statement stmt = conn.createStatement()) {
            if (encoding != null && !encoding.isEmpty()) {
                try {
                    stmt.execute("PRAGMA main.encoding = " + sqlString(encoding));
                } catch (SQLException e) {
                    System.err.println("Could not copy encoding pragma: " + e.getMessage());
                }
            }

            if (pageSize != null && pageSize > 0) {
                try {
                    stmt.execute("PRAGMA main.page_size = " + pageSize);
                } catch (SQLException e) {
                    System.err.println("Could not copy page_size pragma: " + e.getMessage());
                }
            }

            if (autoVacuum != null) {
                try {
                    stmt.execute("PRAGMA main.auto_vacuum = " + autoVacuum);
                } catch (SQLException e) {
                    System.err.println("Could not copy auto_vacuum pragma: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not copy basic pragmas: " + e.getMessage());
        }
    }

    private void copyTrailingPragmasForJdbcClone(Connection conn) {
        Integer userVersion = queryIntPragma(conn, "src.user_version");
        Integer applicationId = queryIntPragma(conn, "src.application_id");

        try (Statement stmt = conn.createStatement()) {
            if (userVersion != null) {
                try {
                    stmt.execute("PRAGMA main.user_version = " + userVersion);
                } catch (SQLException e) {
                    System.err.println("Could not copy user_version pragma: " + e.getMessage());
                }
            }

            if (applicationId != null) {
                try {
                    stmt.execute("PRAGMA main.application_id = " + applicationId);
                } catch (SQLException e) {
                    System.err.println("Could not copy application_id pragma: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not copy trailing pragmas: " + e.getMessage());
        }
    }

    private void createObjectsFromAttachedSource(Connection conn, String type) throws SQLException {
        List<SchemaObject> objects = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name, sql " +
                        "FROM src.sqlite_master " +
                        "WHERE type = ? " +
                        "  AND name NOT LIKE 'sqlite_%' " +
                        "  AND sql IS NOT NULL " +
                        "ORDER BY COALESCE(rootpage, 0), name")) {

            ps.setString(1, type);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objects.add(new SchemaObject(rs.getString("name"), rs.getString("sql")));
                }
            }
        }

        int maxPasses = ("view".equals(type) || "trigger".equals(type)) ? 5 : 1;
        List<SchemaObject> pending = new ArrayList<>(objects);

        for (int pass = 1; pass <= maxPasses && !pending.isEmpty(); pass++) {
            int before = pending.size();

            for (Iterator<SchemaObject> it = pending.iterator(); it.hasNext();) {
                SchemaObject obj = it.next();

                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(obj.sql);
                    it.remove();
                } catch (SQLException e) {
                    if (pass == maxPasses) {
                        System.err.println("Schema copy failed for " + type + " [" + obj.name + "]: " + e.getMessage());
                    }
                }
            }

            if (pending.size() == before) {
                break;
            }
        }

        for (SchemaObject obj : pending) {
            System.err.println("Skipped " + type + " [" + obj.name + "]");
        }
    }

    private void copyAllTableDataFromAttachedSource(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name " +
                             "FROM src.sqlite_master " +
                             "WHERE type = 'table' " +
                             "  AND name NOT LIKE 'sqlite_%' " +
                             "ORDER BY COALESCE(rootpage, 0), name")) {

            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }

        for (String table : tables) {
            copyOneTableFromAttachedSource(conn, table);
        }
    }

    private void copyOneTableFromAttachedSource(Connection conn, String table) {
        List<String> columns;
        try {
            columns = getInsertableColumns(conn, table);
        } catch (SQLException e) {
            System.err.println("Could not read columns for table [" + table + "]: " + e.getMessage());
            return;
        }

        if (columns.isEmpty()) {
            System.err.println("No insertable columns for table [" + table + "], skipping.");
            return;
        }

        String qTable = quoteIdent(table);
        String colList = String.join(", ", columns);
        String bulkSql =
                "INSERT INTO main." + qTable + " (" + colList + ") " +
                        "SELECT " + colList + " FROM src." + qTable;

        try (Statement stmt = conn.createStatement()) {
            int rows = stmt.executeUpdate(bulkSql);
            conn.commit();
            System.err.println("Table [" + table + "]: " + rows + " rows bulk-copied");
            return;
        } catch (SQLException bulkEx) {
            System.err.println("Bulk copy failed for table [" + table + "]: " + bulkEx.getMessage());
            System.err.println("Falling back to row-by-row copy for table [" + table + "]");
        }

        try {
            try (Statement clear = conn.createStatement()) {
                clear.executeUpdate("DELETE FROM main." + qTable);
            }
            conn.commit();
        } catch (SQLException ignored) {
        }

        try {
            copyTableRowByRow(conn, table, columns);
        } catch (SQLException e) {
            System.err.println("Row-by-row copy failed for table [" + table + "]: " + e.getMessage());
            try {
                conn.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

    private void copyTableRowByRow(Connection conn, String table, List<String> columns) throws SQLException {
        String qTable = quoteIdent(table);
        String colList = String.join(", ", columns);
        String selectSql = "SELECT " + colList + " FROM src." + qTable;
        String insertSql = "INSERT INTO main." + qTable + " (" + colList + ") VALUES (" + placeholders(columns.size()) + ")";

        long copied = 0;
        long failed = 0;

        try (Statement selectStmt = conn.createStatement();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             ResultSet rs = selectStmt.executeQuery(selectSql)) {

            selectStmt.setFetchSize(500);

            while (true) {
                boolean hasRow;
                try {
                    hasRow = rs.next();
                } catch (SQLException e) {
                    System.err.println("Stopping row-copy for table [" + table + "] after read error: " + e.getMessage());
                    break;
                }

                if (!hasRow) {
                    break;
                }

                try {
                    for (int i = 0; i < columns.size(); i++) {
                        insertStmt.setObject(i + 1, rs.getObject(i + 1));
                    }
                    insertStmt.executeUpdate();
                    copied++;

                    if (copied % 1000 == 0) {
                        conn.commit();
                    }
                } catch (SQLException e) {
                    failed++;
                }
            }
        }

        conn.commit();
        System.err.println("Table [" + table + "]: " + copied + " rows copied, " + failed + " rows skipped");
    }

    private List<String> getInsertableColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();

        // Prefer table_xinfo because it lets us exclude hidden/generated columns.
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA src.table_xinfo(" + sqlString(table) + ")")) {

            ResultSetMetaData md = rs.getMetaData();
            boolean hasHidden = false;
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String label = md.getColumnLabel(i);
                if ("hidden".equalsIgnoreCase(label)) {
                    hasHidden = true;
                    break;
                }
            }

            while (rs.next()) {
                int hidden = hasHidden ? rs.getInt("hidden") : 0;
                if (hidden == 0) {
                    columns.add(quoteIdent(rs.getString("name")));
                }
            }
        } catch (SQLException e) {
            columns.clear();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA src.table_info(" + sqlString(table) + ")")) {
                while (rs.next()) {
                    columns.add(quoteIdent(rs.getString("name")));
                }
            }
        }

        return columns;
    }

    private void copySqliteSequence(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM main.sqlite_sequence");
            stmt.executeUpdate("INSERT INTO main.sqlite_sequence(name, seq) SELECT name, seq FROM src.sqlite_sequence");
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Skipping sqlite_sequence copy: " + e.getMessage());
        }
    }

/* ===================
   Validation + swap
   =================== */

    private void validateDatabase(Path dbPath, boolean fullIntegrityCheck) throws SQLException {
        String pragma = fullIntegrityCheck ? "integrity_check" : "quick_check";

        try (Connection conn = openDirectConnection(dbPath);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {

            boolean sawRow = false;

            while (rs.next()) {
                sawRow = true;
                String result = rs.getString(1);
                System.err.println("PRAGMA " + pragma + ": " + result);

                if (!"ok".equalsIgnoreCase(result)) {
                    throw new SQLException("Validation failed: " + result);
                }
            }

            if (!sawRow) {
                throw new SQLException("PRAGMA " + pragma + " returned no rows");
            }
        }
    }

    private void swapDatabaseFiles(Path source, Path replacement, Path backup) throws IOException {
        Path sourceWal = sidecar(source, "-wal");
        Path sourceShm = sidecar(source, "-shm");
        Path backupWal = sidecar(backup, "-wal");
        Path backupShm = sidecar(backup, "-shm");
        Path replacementWal = sidecar(replacement, "-wal");
        Path replacementShm = sidecar(replacement, "-shm");

        boolean movedOriginal = false;

        try {
            if (Files.exists(source)) {
                moveIfExists(sourceWal, backupWal);
                moveIfExists(sourceShm, backupShm);
                Files.move(source, backup, StandardCopyOption.REPLACE_EXISTING);
                movedOriginal = true;
            }

            Files.move(replacement, source, StandardCopyOption.REPLACE_EXISTING);
            moveIfExists(replacementWal, sourceWal);
            moveIfExists(replacementShm, sourceShm);

        } catch (IOException e) {
            if (movedOriginal && !Files.exists(source) && Files.exists(backup)) {
                try {
                    Files.move(backup, source, StandardCopyOption.REPLACE_EXISTING);
                    moveIfExists(backupWal, sourceWal);
                    moveIfExists(backupShm, sourceShm);
                } catch (IOException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        }
    }

/* ===================
   Connection helpers
   =================== */

    private Connection openDirectConnection(Path dbPath) throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS);
        }
        return conn;
    }

/* ==============
   Misc helpers
   ============== */

    private Integer queryIntPragma(Connection conn, String pragma) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private String queryStringPragma(Connection conn, String pragma) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {
            if (rs.next()) {
                return rs.getString(1);
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private Thread drainTextAsync(InputStream in, StringBuilder sink, String threadName) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sink.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                sink.append("[stream read failed: ").append(e.getMessage()).append("]").append(System.lineSeparator());
            }
        }, threadName);

        t.setDaemon(true);
        t.start();
        return t;
    }

    private void deleteDatabaseFiles(Path dbPath) throws IOException {
        Files.deleteIfExists(sidecar(dbPath, "-wal"));
        Files.deleteIfExists(sidecar(dbPath, "-shm"));
        Files.deleteIfExists(dbPath);
    }

    private void moveIfExists(Path from, Path to) throws IOException {
        if (Files.exists(from)) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path sidecar(Path dbPath, String suffix) {
        return dbPath.resolveSibling(dbPath.getFileName().toString() + suffix);
    }

    private String quoteIdent(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String sqlString(Path p) {
        return sqlString(p.toString());
    }

    private String sqlString(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        return sb.toString();
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private static final class SchemaObject {
        final String name;
        final String sql;

        SchemaObject(String name, String sql) {
            this.name = name;
            this.sql = sql;
        }
    }
}
