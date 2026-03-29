package link.locutus.discord.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

final class SqliteTableMoveSupport {
    private SqliteTableMoveSupport() {
    }

    static String moveTableToAvailableName(Connection connection, String currentName, String preferredName)
            throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(currentName, "currentName");
        Objects.requireNonNull(preferredName, "preferredName");

        boolean writableSchemaEnabled = false;
        try {
            setWritableSchema(connection, true);
            writableSchemaEnabled = true;

            String targetName = availableTableName(connection, preferredName);
            try {
                renameTable(connection, currentName, targetName);
                return targetName;
            } catch (SQLException renameFailure) {
                if (tableExists(connection, targetName) && !tableExists(connection, currentName)) {
                    return targetName;
                }
                snapshotTableForMigration(connection, currentName, targetName, renameFailure);
                return targetName;
            }
        } finally {
            if (writableSchemaEnabled) {
                setWritableSchema(connection, false);
            }
        }
    }

    private static void snapshotTableForMigration(Connection connection, String currentName, String targetName,
            SQLException renameFailure) throws SQLException {
        if (!tableExists(connection, currentName)) {
            throw renameFailure;
        }

        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            // The migration only needs a readable snapshot; preserving every legacy index is unnecessary.
            stmt.executeUpdate("CREATE TABLE " + quoteIdentifier(targetName) + " AS SELECT * FROM "
                    + quoteIdentifier(currentName));
            createTxIdIndexIfPresent(connection, stmt, targetName);
            stmt.executeUpdate("DROP TABLE " + quoteIdentifier(currentName));
            connection.commit();
        } catch (SQLException snapshotFailure) {
            connection.rollback();
            snapshotFailure.addSuppressed(renameFailure);
            throw snapshotFailure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static void createTxIdIndexIfPresent(Connection connection, Statement stmt, String tableName)
            throws SQLException {
        if (!hasColumn(connection, tableName, "tx_id")) {
            return;
        }
        String indexName = "idx_" + sanitizeIdentifier(tableName) + "_tx_id";
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName) + " ON "
                + quoteIdentifier(tableName) + "(tx_id)");
    }

    private static void renameTable(Connection connection, String currentName, String targetName) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + quoteIdentifier(currentName) + " RENAME TO "
                    + quoteIdentifier(targetName));
        }
    }

    private static String availableTableName(Connection connection, String preferredName) throws SQLException {
        String targetName = preferredName;
        if (tableExists(connection, targetName)) {
            targetName = preferredName + "_" + System.currentTimeMillis();
        }
        return targetName;
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND lower(name) = lower(?) LIMIT 1")) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasColumn(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + quoteIdentifier(tableName) + ")";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && name.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void setWritableSchema(Connection connection, boolean enabled) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA writable_schema = " + (enabled ? "ON" : "OFF"));
        }
    }

    private static String sanitizeIdentifier(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return builder.toString();
    }

    private static String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}