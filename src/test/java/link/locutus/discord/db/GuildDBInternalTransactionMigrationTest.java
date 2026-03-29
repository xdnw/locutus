package link.locutus.discord.db;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildDBInternalTransactionMigrationTest {
    @Test
    void findsRecordedLegacySourceTableForRecheck() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            String targetTable = "INTERNAL_TRANSACTIONS2";
            String renamedLegacyTable = "INTERNAL_TRANSACTIONS2_LEGACY_123";

            createProgressTable(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE `" + renamedLegacyTable + "` (tx_id INTEGER PRIMARY KEY)");
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO `TRANSACTION_MIGRATION_PROGRESS` "
                            + "(target_table, source_table, last_tx_id, batch_size, started_at, updated_at, completed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, targetTable);
                stmt.setString(2, renamedLegacyTable);
                stmt.setLong(3, 0L);
                stmt.setInt(4, 100);
                stmt.setLong(5, 1L);
                stmt.setLong(6, 2L);
                stmt.setNull(7, java.sql.Types.BIGINT);
                stmt.executeUpdate();
            }

            assertEquals(renamedLegacyTable,
                    GuildDB.findTransactionMigrationSourceTable(conn, targetTable, "INTERNAL_TRANSACTIONS2_LEGACY"));
        }
    }

    @Test
    void findsUniqueSuffixedLegacyTableWhenProgressRowIsMissing() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            String renamedLegacyTable = "INTERNAL_TRANSACTIONS2_LEGACY_123";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE `" + renamedLegacyTable + "` (tx_id INTEGER PRIMARY KEY)");
            }

            assertEquals(renamedLegacyTable,
                    GuildDB.findTransactionMigrationSourceTable(conn, "INTERNAL_TRANSACTIONS2",
                            "INTERNAL_TRANSACTIONS2_LEGACY"));
        }
    }

    private static void createProgressTable(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE `TRANSACTION_MIGRATION_PROGRESS` (
                        `target_table` TEXT PRIMARY KEY,
                        `source_table` TEXT NOT NULL,
                        `last_tx_id` INTEGER NOT NULL,
                        `batch_size` INTEGER NOT NULL,
                        `started_at` BIGINT NOT NULL,
                        `updated_at` BIGINT NOT NULL,
                        `completed_at` BIGINT
                    )
                    """);
        }
    }
}