package link.locutus.discord.db;

import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.io.BitBuffer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class TransactionTableMigrator {
    static final String PROGRESS_TABLE = "TRANSACTION_MIGRATION_PROGRESS";
    public static final String BATCH_SIZE_PROPERTY = "locutus.transactionMigration.batchSize";
    public static final int DEFAULT_BATCH_SIZE = 1_000;

    private static final Logger LOG = Logger.getLogger(TransactionTableMigrator.class.getName());

    private TransactionTableMigrator() {
    }

    public static int defaultBatchSize() {
        return Integer.getInteger(BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE);
    }

    public static void migrate(Connection connection, String sourceTable, String targetTable, boolean splitEndpointTable,
            int batchSize, BatchWriter batchWriter) {
        migrate(connection, sourceTable, targetTable, targetTable, splitEndpointTable, batchSize, true, batchWriter);
    }

    public static void migrate(Connection connection, String sourceTable, String targetTable, String progressKey,
            boolean splitEndpointTable, int batchSize, boolean resumeFromTargetMaxTxId, BatchWriter batchWriter) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Transaction migration batch size must be positive, got: " + batchSize);
        }
        try {
            ensureProgressTable(connection);
            MigrationState state = beginOrResume(connection, sourceTable, targetTable, progressKey, batchSize,
                    resumeFromTargetMaxTxId);
            LOG.info(() -> "Migrating `" + sourceTable + "` -> `" + targetTable + "` [progressKey=" + progressKey
                    + "] from tx_id > " + state.lastTxId
                    + " in batches of " + batchSize);

            long lastTxId = state.lastTxId;
            long migratedThisRun = 0;
            while (true) {
                List<Transaction2> batch = loadBatch(connection, sourceTable, splitEndpointTable, lastTxId, batchSize);
                if (batch.isEmpty()) {
                    markComplete(connection, sourceTable, progressKey, lastTxId, batchSize, state.startedAt);
                    long totalMigrated = countRows(connection, targetTable);
                    long finalLastTxId = lastTxId;
                    long finalMigratedThisRun = migratedThisRun;
                    LOG.info(() -> "Completed migration `" + sourceTable + "` -> `" + targetTable + "` at tx_id="
                            + finalLastTxId + " (migrated " + finalMigratedThisRun + " rows this run, " + totalMigrated
                            + " rows now present in target)");
                    return;
                }

                batchWriter.write(batch);
                lastTxId = batch.get(batch.size() - 1).tx_id;
                migratedThisRun += batch.size();
                saveProgress(connection, sourceTable, progressKey, lastTxId, batchSize, state.startedAt, null);

                long batchEndTxId = lastTxId;
                long batchMigratedThisRun = migratedThisRun;
                LOG.info(() -> "Migrated batch of " + batch.size() + " rows from `" + sourceTable + "` -> `"
                        + targetTable + "` through tx_id=" + batchEndTxId + " (" + batchMigratedThisRun
                        + " rows migrated this run)");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to migrate transactions from `" + sourceTable + "` to `" + targetTable + "`", e);
        }
    }

    private static void ensureProgressTable(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS `" + PROGRESS_TABLE + "` ("
                    + "`target_table` TEXT PRIMARY KEY, "
                    + "`source_table` TEXT NOT NULL, "
                    + "`last_tx_id` INTEGER NOT NULL, "
                    + "`batch_size` INTEGER NOT NULL, "
                    + "`started_at` BIGINT NOT NULL, "
                    + "`updated_at` BIGINT NOT NULL, "
                    + "`completed_at` BIGINT)");
        }
    }

    private static MigrationState beginOrResume(Connection connection, String sourceTable, String targetTable,
            String progressKey, int batchSize, boolean resumeFromTargetMaxTxId) throws SQLException {
        MigrationState existing = loadProgress(connection, progressKey);
        long targetMaxTxId = resumeFromTargetMaxTxId ? maxTxId(connection, targetTable) : 0L;
        long resumeTxId = existing == null ? targetMaxTxId : Math.max(existing.lastTxId, targetMaxTxId);
        long startedAt = existing != null && sourceTable.equals(existing.sourceTable) ? existing.startedAt
                : System.currentTimeMillis();
        saveProgress(connection, sourceTable, progressKey, resumeTxId, batchSize, startedAt, null);
        return new MigrationState(sourceTable, resumeTxId, startedAt);
    }

    private static MigrationState loadProgress(Connection connection, String progressKey) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT source_table, last_tx_id, started_at FROM `" + PROGRESS_TABLE + "` WHERE target_table = ?")) {
            stmt.setString(1, progressKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new MigrationState(rs.getString("source_table"), rs.getLong("last_tx_id"), rs.getLong("started_at"));
            }
        }
    }

    private static void saveProgress(Connection connection, String sourceTable, String progressKey, long lastTxId,
            int batchSize, long startedAt, Long completedAt) throws SQLException {
        long updatedAt = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO `" + PROGRESS_TABLE + "` (target_table, source_table, last_tx_id, batch_size, started_at, updated_at, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(target_table) DO UPDATE SET "
                        + "source_table = excluded.source_table, "
                        + "last_tx_id = excluded.last_tx_id, "
                        + "batch_size = excluded.batch_size, "
                        + "started_at = excluded.started_at, "
                        + "updated_at = excluded.updated_at, "
                        + "completed_at = excluded.completed_at")) {
            stmt.setString(1, progressKey);
            stmt.setString(2, sourceTable);
            stmt.setLong(3, lastTxId);
            stmt.setInt(4, batchSize);
            stmt.setLong(5, startedAt);
            stmt.setLong(6, updatedAt);
            if (completedAt == null) {
                stmt.setNull(7, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(7, completedAt);
            }
            stmt.executeUpdate();
        }
    }

    private static void markComplete(Connection connection, String sourceTable, String progressKey, long lastTxId,
            int batchSize, long startedAt) throws SQLException {
        saveProgress(connection, sourceTable, progressKey, lastTxId, batchSize, startedAt, System.currentTimeMillis());
    }

    private static List<Transaction2> loadBatch(Connection connection, String sourceTable, boolean splitEndpointTable,
            long lastTxId, int batchSize) throws SQLException {
        List<Transaction2> batch = new ArrayList<>(batchSize);
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT * FROM `" + sourceTable + "` WHERE tx_id > ? ORDER BY tx_id ASC LIMIT ?")) {
            stmt.setFetchSize(batchSize);
            stmt.setLong(1, lastTxId);
            stmt.setInt(2, batchSize);
            try (ResultSet rs = stmt.executeQuery()) {
                BitBuffer noteBuffer = splitEndpointTable ? Transaction2.reusableNoteBuffer() : null;
                while (rs.next()) {
                    batch.add(splitEndpointTable ? Transaction2.loadSplit(rs, noteBuffer) : Transaction2.loadLegacy(rs));
                }
            }
        }
        return batch;
    }

    private static long maxTxId(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COALESCE(MAX(tx_id), 0) FROM `" + tableName + "`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    private static long countRows(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT COUNT(*) FROM `" + tableName + "`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    @FunctionalInterface
    public interface BatchWriter {
        void write(List<Transaction2> batch) throws SQLException;
    }

    private record MigrationState(String sourceTable, long lastTxId, long startedAt) {
    }
}

