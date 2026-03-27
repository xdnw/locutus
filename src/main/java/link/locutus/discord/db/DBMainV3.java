package link.locutus.discord.db;

import org.jdbi.v3.core.Jdbi;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public abstract class DBMainV3 implements AutoCloseable {
    private final DataSource dataSource;
    private final Jdbi jdbi;
    private final String jdbcUrl;
    private final Connection keepAliveConnection;

    public DBMainV3(
            File path,
            String name,
            boolean fullDurability,
            boolean inMemory,
            int mmapSizeMb,
            int memCacheMb,
            int busyTimeoutSeconds
    ) throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        this.jdbcUrl = buildJdbcUrl(path == null ? null : path.toPath(), name, inMemory);

        SQLiteDataSource sqlite = new SQLiteDataSource();
        sqlite.setUrl(jdbcUrl);

        this.dataSource = new ConfiguringDataSource(
                sqlite,
                fullDurability,
                inMemory,
                mmapSizeMb,
                memCacheMb,
                busyTimeoutSeconds
        );
        this.jdbi = Jdbi.create(dataSource);

        // A named shared in-memory DB disappears when the last connection closes.
        this.keepAliveConnection = inMemory ? this.dataSource.getConnection() : null;
    }

    public abstract void createTables();

    protected final Jdbi jdbi() {
        return jdbi;
    }

    protected final DataSource dataSource() {
        return dataSource;
    }

    protected final String jdbcUrl() {
        return jdbcUrl;
    }

    protected final Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected final boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND lower(name) = lower(?) LIMIT 1"
             )) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    protected final boolean hasColumn(String tableName, String columnName) throws SQLException {
        return getColumnType(tableName, columnName) != null;
    }

    protected final String getColumnType(String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + quoteIdentifier(tableName) + ")";
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(columnName)) {
                    return rs.getString("type");
                }
            }
        }
        return null;
    }

    protected static String quoteIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() throws SQLException {
        if (keepAliveConnection != null && !keepAliveConnection.isClosed()) {
            keepAliveConnection.close();
        }
    }

    private static String buildJdbcUrl(Path path, String name, boolean inMemory) throws SQLException {
        String databaseName = requireName(name);

        if (inMemory) {
            return "jdbc:sqlite:file:" + encodeForSqliteUri(databaseName) + "?mode=memory&cache=shared";
        }

        if (path == null) {
            throw new IllegalArgumentException("path cannot be null when inMemory is false");
        }

        Path dbFile = path.resolve(toDatabaseFileName(databaseName));

        try {
            Path parent = dbFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to create SQLite directory for " + dbFile, e);
        }

        return "jdbc:sqlite:" + dbFile;
    }

    private static String requireName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or blank");
        }

        return trimmed;
    }

    private static String toDatabaseFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".db") || lower.endsWith(".sqlite") || lower.endsWith(".sqlite3")) {
            return name;
        }
        return name + ".db";
    }

    private static String encodeForSqliteUri(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static final class ConfiguringDataSource implements DataSource {
        private static final long MB = 1024L * 1024L;

        private final DataSource delegate;
        private final boolean fullDurability;
        private final boolean inMemory;
        private final long mmapSizeBytes;
        private final long cacheSizeKb;
        private final int busyTimeoutMs;

        private ConfiguringDataSource(
                DataSource delegate,
                boolean fullDurability,
                boolean inMemory,
                int mmapSizeMb,
                int memCacheMb,
                int busyTimeoutSeconds
        ) {
            this.delegate = delegate;
            this.fullDurability = fullDurability;
            this.inMemory = inMemory;
            this.mmapSizeBytes = Math.max(0L, (long) mmapSizeMb) * MB;
            this.cacheSizeKb = Math.max(0L, (long) memCacheMb) * 1024L;

            long busyTimeoutMsLong = Math.max(0L, (long) busyTimeoutSeconds) * 1000L;
            this.busyTimeoutMs = busyTimeoutMsLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) busyTimeoutMsLong;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return configure(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return configure(delegate.getConnection(username, password));
        }

        private Connection configure(Connection connection) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                execute(stmt, "PRAGMA foreign_keys = ON");
                execute(stmt, "PRAGMA temp_store = MEMORY");
                execute(stmt, "PRAGMA busy_timeout = " + busyTimeoutMs);

                if (cacheSizeKb > 0) {
                    // Negative cache_size means the value is in KiB instead of pages.
                    // Note: this is per connection.
                    execute(stmt, "PRAGMA cache_size = -" + cacheSizeKb);
                }

                if (inMemory) {
                    // WAL is not available for in-memory databases.
                    execute(stmt, "PRAGMA journal_mode = MEMORY");
                    execute(stmt, "PRAGMA synchronous = OFF");
                } else {
                    execute(stmt, "PRAGMA journal_mode = WAL");
                    execute(stmt, "PRAGMA synchronous = " + (fullDurability ? "FULL" : "NORMAL"));

                    if (mmapSizeBytes > 0) {
                        execute(stmt, "PRAGMA mmap_size = " + mmapSizeBytes);
                    }
                }
            } catch (SQLException e) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            return connection;
        }

        private void execute(Statement stmt, String sql) throws SQLException {
            if (stmt.execute(sql)) {
                try (ResultSet ignored = stmt.getResultSet()) {
                    // no-op
                }
            }
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return delegate.isWrapperFor(iface);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }
    }
}