package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.TransactionEndpointKey;
import link.locutus.discord.db.entities.TransactionNote;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionTableMigratorTest {
    private static final String LEGACY_SOURCE = "LEGACY_TX";
    private static final String LEGACY_ALLIANCE_SOURCE = "LEGACY_ALLIANCE_TX";
    private static final String SPLIT_SOURCE = "SPLIT_TX";
    private static final String TARGET = "TRANSACTIONS_2";

    @Test
    void migratesLegacyRowsInBatchesWithoutMaterializingWholeTable() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacySourceTable(conn, LEGACY_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            List<Transaction2> expected = sampleTransactions(5);
            for (Transaction2 tx : expected) {
                insertLegacySourceRow(conn, LEGACY_SOURCE, tx);
            }

            TransactionTableMigrator.migrate(conn, LEGACY_SOURCE, TARGET, false, 2,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));

            assertCanonicalRows(conn, TARGET, expected);
            assertProgress(conn, TARGET, LEGACY_SOURCE, 5L, 2, true);
        }
    }

    @Test
    void migratesSplitEndpointRowsInBatches() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSplitSourceTable(conn, SPLIT_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            List<Transaction2> expected = sampleTransactions(4);
            for (Transaction2 tx : expected) {
                insertSplitSourceRow(conn, SPLIT_SOURCE, tx);
            }

            TransactionTableMigrator.migrate(conn, SPLIT_SOURCE, TARGET, true, 3,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));

            assertCanonicalRows(conn, TARGET, expected);
            assertProgress(conn, TARGET, SPLIT_SOURCE, 4L, 3, true);
        }
    }

    @Test
    void migratesSplitRowsWithTaxAndNoneEndpointsLosslessly() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createSplitSourceTable(conn, SPLIT_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            Transaction2 expected = Transaction2.construct(7, 1_700_000_007_000L,
                    44L, TransactionEndpointKey.TAX_TYPE,
                    0L, TransactionEndpointKey.NONE_TYPE,
                    901,
                    TransactionNote.of(DepositType.TAX),
                    false,
                    false,
                    money(12.5));
            insertSplitSourceRow(conn, SPLIT_SOURCE, expected);

            TransactionTableMigrator.migrate(conn, SPLIT_SOURCE, TARGET, true, 10,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));

            assertCanonicalRows(conn, TARGET, List.of(expected));
            assertProgress(conn, TARGET, SPLIT_SOURCE, 7L, 10, true);
        }
    }

    @Test
    void resumesFromPersistedProgressAfterInterruptedRun() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacySourceTable(conn, LEGACY_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            List<Transaction2> expected = sampleTransactions(5);
            for (Transaction2 tx : expected) {
                insertLegacySourceRow(conn, LEGACY_SOURCE, tx);
            }

            AtomicInteger batchCalls = new AtomicInteger();
            RuntimeException failure = assertThrows(RuntimeException.class, () -> TransactionTableMigrator.migrate(conn,
                    LEGACY_SOURCE, TARGET, false, 2, batch -> {
                        if (batchCalls.incrementAndGet() == 2) {
                            throw new RuntimeException("stop after first committed batch");
                        }
                        writeCanonicalBatch(conn, TARGET, batch);
                    }));
            assertEquals("stop after first committed batch", failure.getMessage());
            assertProgress(conn, TARGET, LEGACY_SOURCE, 2L, 2, false);

            TransactionTableMigrator.migrate(conn, LEGACY_SOURCE, TARGET, false, 2,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));

            assertCanonicalRows(conn, TARGET, expected);
            assertProgress(conn, TARGET, LEGACY_SOURCE, 5L, 2, true);
        }
    }

    @Test
    void resumesFromTargetMaxTxIdWhenWriteSucceededBeforeProgressUpdate() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacySourceTable(conn, LEGACY_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            List<Transaction2> expected = sampleTransactions(5);
            for (Transaction2 tx : expected) {
                insertLegacySourceRow(conn, LEGACY_SOURCE, tx);
            }

            RuntimeException failure = assertThrows(RuntimeException.class, () -> TransactionTableMigrator.migrate(conn,
                    LEGACY_SOURCE, TARGET, false, 2, batch -> {
                        writeCanonicalBatch(conn, TARGET, batch);
                        throw new RuntimeException("crash after batch write before cursor save");
                    }));
            assertEquals("crash after batch write before cursor save", failure.getMessage());
            assertEquals(2, countRows(conn, TARGET));
            assertProgress(conn, TARGET, LEGACY_SOURCE, 0L, 2, false);

            TransactionTableMigrator.migrate(conn, LEGACY_SOURCE, TARGET, false, 2,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));

            assertCanonicalRows(conn, TARGET, expected);
            assertProgress(conn, TARGET, LEGACY_SOURCE, 5L, 2, true);
        }
    }

    @Test
    void migratesSecondLegacySourceIntoSameTargetWithoutSkippingLowerUnmigratedIds() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacySourceTable(conn, LEGACY_SOURCE);
            createLegacySourceTable(conn, LEGACY_ALLIANCE_SOURCE);
            createCanonicalTargetTable(conn, TARGET);

            List<Transaction2> primary = List.of(sampleTransaction(1), sampleTransaction(3));
            List<Transaction2> alliance = List.of(sampleTransaction(2), sampleTransaction(4));
            for (Transaction2 tx : primary) {
                insertLegacySourceRow(conn, LEGACY_SOURCE, tx);
            }
            for (Transaction2 tx : alliance) {
                insertLegacySourceRow(conn, LEGACY_ALLIANCE_SOURCE, tx);
            }

            TransactionTableMigrator.migrate(conn, LEGACY_SOURCE, TARGET, false, 2,
                    batch -> writeCanonicalBatch(conn, TARGET, batch));
            TransactionTableMigrator.migrate(conn, LEGACY_ALLIANCE_SOURCE, TARGET, TARGET + "::" + LEGACY_ALLIANCE_SOURCE,
                    false, 2, false, batch -> writeCanonicalBatch(conn, TARGET, batch));

            List<Transaction2> expected = List.of(sampleTransaction(1), sampleTransaction(2), sampleTransaction(3),
                    sampleTransaction(4));
            assertCanonicalRows(conn, TARGET, expected);
            assertProgress(conn, TARGET, LEGACY_SOURCE, 3L, 2, true);
            assertProgress(conn, TARGET + "::" + LEGACY_ALLIANCE_SOURCE, LEGACY_ALLIANCE_SOURCE, 4L, 2, true);
        }
    }

    private static List<Transaction2> sampleTransactions(int count) {
        List<Transaction2> list = new ArrayList<>(count);
        long baseDate = 1_700_000_000_000L;
        for (int i = 1; i <= count; i++) {
            list.add(sampleTransaction(i));
        }
        return list;
    }

    private static Transaction2 sampleTransaction(int i) {
        long baseDate = 1_700_000_000_000L;
        TransactionNote note = TransactionNote.builder()
                .put(DepositType.DEPOSIT)
                .put(DepositType.CITY, (long) i)
                .build();
        return Transaction2.construct(i, baseDate + (i * 1_000L), 100L + i, i % 2 == 0 ? 2 : 1,
                200L + i, i % 3 == 0 ? 4 : 2, 900 + i, note, false, false, money(i * 10.25));
    }

    private static void createLegacySourceTable(Connection conn, String tableName) throws Exception {
        StringBuilder sql = new StringBuilder("CREATE TABLE `").append(tableName).append("` (")
                .append("tx_id INTEGER PRIMARY KEY, ")
                .append("tx_datetime INTEGER NOT NULL, ")
                .append("sender_id INTEGER NOT NULL, ")
                .append("sender_type INTEGER NOT NULL, ")
                .append("receiver_id INTEGER NOT NULL, ")
                .append("receiver_type INTEGER NOT NULL, ")
                .append("banker_nation_id INTEGER NOT NULL, ")
                .append("note TEXT");
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            sql.append(", `").append(type.name()).append("` INTEGER NOT NULL DEFAULT 0");
        }
        sql.append(")");
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql.toString());
        }
    }

    private static void createSplitSourceTable(Connection conn, String tableName) throws Exception {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE `%s` (
                        tx_id INTEGER PRIMARY KEY,
                        tx_datetime INTEGER NOT NULL,
                        sender_id INTEGER NOT NULL,
                        sender_type INTEGER NOT NULL,
                        receiver_id INTEGER NOT NULL,
                        receiver_type INTEGER NOT NULL,
                        banker_nation_id INTEGER NOT NULL,
                        note BLOB
                    )
                    """.formatted(tableName));
        }
    }

    private static void createCanonicalTargetTable(Connection conn, String tableName) throws Exception {
        try (Statement statement = conn.createStatement()) {
            statement.execute("""
                    CREATE TABLE `%s` (
                        tx_id INTEGER PRIMARY KEY,
                        tx_datetime INTEGER NOT NULL,
                        sender_key INTEGER NOT NULL,
                        receiver_key INTEGER NOT NULL,
                        banker_nation_id INTEGER NOT NULL,
                        note BLOB
                    )
                    """.formatted(tableName));
        }
    }

    private static void insertLegacySourceRow(Connection conn, String tableName, Transaction2 tx) throws Exception {
        StringBuilder columns = new StringBuilder("tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker_nation_id, note");
        StringBuilder values = new StringBuilder("?, ?, ?, ?, ?, ?, ?, ?");
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            columns.append(", `").append(type.name()).append("`");
            values.append(", ?");
        }
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `" + tableName + "` (" + columns + ") VALUES (" + values + ")")) {
            int index = 1;
            stmt.setInt(index++, tx.tx_id);
            stmt.setLong(index++, tx.tx_datetime);
            stmt.setLong(index++, tx.sender_id);
            stmt.setInt(index++, tx.sender_type);
            stmt.setLong(index++, tx.receiver_id);
            stmt.setInt(index++, tx.receiver_type);
            stmt.setInt(index++, tx.banker_nation);
            stmt.setString(index++, tx.getLegacyNote());
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) {
                    continue;
                }
                stmt.setLong(index++, ArrayUtil.toCents(tx.resources[type.ordinal()]));
            }
            stmt.executeUpdate();
        }
    }

    private static void insertSplitSourceRow(Connection conn, String tableName, Transaction2 tx) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO `" + tableName + "` (tx_id, tx_datetime, sender_id, sender_type, receiver_id, receiver_type, banker_nation_id, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            BitBuffer buffer = Transaction2.createNoteBuffer();
            int index = 1;
            stmt.setInt(index++, tx.tx_id);
            stmt.setLong(index++, tx.tx_datetime);
            stmt.setLong(index++, tx.sender_id);
            stmt.setInt(index++, tx.sender_type);
            stmt.setLong(index++, tx.receiver_id);
            stmt.setInt(index++, tx.receiver_type);
            stmt.setInt(index++, tx.banker_nation);
            stmt.setBytes(index++, tx.getNoteBytes(buffer));
            stmt.executeUpdate();
        }
    }

    private static void writeCanonicalBatch(Connection conn, String tableName, List<Transaction2> batch) throws java.sql.SQLException {
        if (batch.isEmpty()) {
            return;
        }
        String query = batch.get(0).createInsert(tableName, true, false);
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            BitBuffer buffer = Transaction2.createNoteBuffer();
            for (Transaction2 tx : batch) {
                stmt.clearParameters();
                tx.set(stmt, buffer);
                stmt.addBatch();
            }
            stmt.executeBatch();
            conn.commit();
        } catch (java.sql.SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private static void assertCanonicalRows(Connection conn, String tableName, List<Transaction2> expected) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM `" + tableName + "` ORDER BY tx_id ASC");
                ResultSet rs = stmt.executeQuery()) {
            BitBuffer buffer = Transaction2.createNoteBuffer();
            int index = 0;
            while (rs.next()) {
                Transaction2 actual = Transaction2.load(rs, buffer);
                Transaction2 exp = expected.get(index++);
                assertEquals(exp.tx_id, actual.tx_id);
                assertEquals(exp.tx_datetime, actual.tx_datetime);
                assertEquals(exp.sender_id, actual.sender_id);
                assertEquals(exp.sender_type, actual.sender_type);
                assertEquals(exp.receiver_id, actual.receiver_id);
                assertEquals(exp.receiver_type, actual.receiver_type);
                assertEquals(exp.banker_nation, actual.banker_nation);
                assertEquals(exp.getLegacyNote(), actual.getLegacyNote());
                assertEquals(exp.resources[ResourceType.MONEY.ordinal()], actual.resources[ResourceType.MONEY.ordinal()]);
                assertEquals(TransactionEndpointKey.encode(exp.sender_id, exp.sender_type), actual.getSenderKey());
                assertEquals(TransactionEndpointKey.encode(exp.receiver_id, exp.receiver_type), actual.getReceiverKey());
            }
            assertEquals(expected.size(), index);
        }
    }

    private static void assertProgress(Connection conn, String targetTable, String sourceTable, long lastTxId,
            int batchSize, boolean completed) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT source_table, last_tx_id, batch_size, completed_at FROM `" + TransactionTableMigrator.PROGRESS_TABLE + "` WHERE target_table = ?")) {
            stmt.setString(1, targetTable);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(sourceTable, rs.getString("source_table"));
                assertEquals(lastTxId, rs.getLong("last_tx_id"));
                assertEquals(batchSize, rs.getInt("batch_size"));
                if (completed) {
                    assertNotNull(rs.getObject("completed_at"));
                } else {
                    assertNull(rs.getObject("completed_at"));
                }
            }
        }
    }

    private static int countRows(Connection conn, String tableName) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM `" + tableName + "`");
                ResultSet rs = stmt.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static double[] money(double amount) {
        double[] resources = new double[ResourceType.values.length];
        resources[ResourceType.MONEY.ordinal()] = amount;
        return resources;
    }
}


