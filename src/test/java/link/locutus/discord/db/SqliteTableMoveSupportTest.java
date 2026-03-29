package link.locutus.discord.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTableMoveSupportTest {
    @Test
    void renamesTableDirectlyWhenSchemaIsHealthy() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacyTransactionsTable(conn, "TRANSACTIONS_2");
            insertLegacyRow(conn, "TRANSACTIONS_2", 1, 2);

            String moved = SqliteTableMoveSupport.moveTableToAvailableName(conn, "TRANSACTIONS_2",
                    "TRANSACTIONS_2_LEGACY");

            assertEquals("TRANSACTIONS_2_LEGACY", moved);
            assertFalse(tableExists(conn, "TRANSACTIONS_2"));
            assertTrue(tableExists(conn, "TRANSACTIONS_2_LEGACY"));
            assertEquals(1, countRows(conn, "TRANSACTIONS_2_LEGACY"));
        }
    }

    @Test
    void fallsBackToSnapshotWhenRenameHitsMalformedLegacyIndex() throws Exception {
        Path dbFile = Files.createTempFile("sqlite-table-move", ".db");
        try {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
                createLegacyTransactionsTable(conn, "TRANSACTIONS_2");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE INDEX index_receiver_type ON TRANSACTIONS_2(receiver_type)");
                }
                insertLegacyRow(conn, "TRANSACTIONS_2", 7, 4);
                corruptIndexSql(conn, "index_receiver_type",
                        "CREATE INDEX index_receiver_type ON TRANSACTIONS_2_LEGACY(receiver_type)");
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath())) {
                SQLException failure = assertThrows(SQLException.class,
                        () -> renameDirect(conn, "TRANSACTIONS_2", "TRANSACTIONS_2_LEGACY"));
                assertTrue(failure.getMessage().contains("index_receiver_type"));

                String moved = SqliteTableMoveSupport.moveTableToAvailableName(conn, "TRANSACTIONS_2",
                        "TRANSACTIONS_2_LEGACY");

                assertEquals("TRANSACTIONS_2_LEGACY", moved);
                assertFalse(tableExists(conn, "TRANSACTIONS_2"));
                assertTrue(tableExists(conn, "TRANSACTIONS_2_LEGACY"));
                assertEquals(1, countRows(conn, "TRANSACTIONS_2_LEGACY"));
                assertEquals(1, readReceiverType(conn, "TRANSACTIONS_2_LEGACY", 7));
                assertTrue(indexExists(conn, "idx_transactions_2_legacy_tx_id"));
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    private static void createLegacyTransactionsTable(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"" + tableName + "\" ("
                    + "tx_id INTEGER PRIMARY KEY, "
                    + "receiver_type INTEGER NOT NULL, "
                    + "note TEXT)");
        }
    }

    private static void insertLegacyRow(Connection conn, String tableName, int txId, int receiverType)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO \"" + tableName + "\" (tx_id, receiver_type, note) VALUES (?, ?, ?)") ) {
            stmt.setInt(1, txId);
            stmt.setInt(2, receiverType);
            stmt.setString(3, "#deposit");
            stmt.executeUpdate();
        }
    }

    private static void corruptIndexSql(Connection conn, String indexName, String sql) throws SQLException {
        int schemaVersion = readPragmaInt(conn, "schema_version");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA writable_schema = ON");
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE sqlite_master SET sql = ? WHERE type = 'index' AND name = ?")) {
            stmt.setString(1, sql);
            stmt.setString(2, indexName);
            stmt.executeUpdate();
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA schema_version = " + (schemaVersion + 1));
            stmt.execute("PRAGMA writable_schema = OFF");
        }
    }

    private static void renameDirect(Connection conn, String currentName, String targetName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE \"" + currentName + "\" RENAME TO \"" + targetName + "\"");
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND lower(name) = lower(?) LIMIT 1")) {
            stmt.setString(1, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean indexExists(Connection conn, String indexName) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ? LIMIT 1")) {
            stmt.setString(1, indexName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int countRows(Connection conn, String tableName) throws SQLException {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM \"" + tableName + "\"")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int readReceiverType(Connection conn, String tableName, int txId) throws SQLException {
        try (PreparedStatement stmt = conn
                .prepareStatement("SELECT receiver_type FROM \"" + tableName + "\" WHERE tx_id = ?")) {
            stmt.setInt(1, txId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int readPragmaInt(Connection conn, String pragma) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}