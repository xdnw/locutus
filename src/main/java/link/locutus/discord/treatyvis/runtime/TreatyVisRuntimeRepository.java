package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.DBMainV2;
import link.locutus.discord.db.entities.DBTreatyChange;
import link.locutus.discord.db.entities.TreatyChangeAction;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

public final class TreatyVisRuntimeRepository {
    private static final String CREATE_TREATY_CHANGES_UNIFIED = "CREATE TABLE IF NOT EXISTS TREATY_CHANGES_UNIFIED (timestamp BIGINT NOT NULL, action TINYINT NOT NULL, treaty_type TINYINT NOT NULL, from_alliance_id INT NOT NULL, to_alliance_id INT NOT NULL, turns_remaining INT NOT NULL, PRIMARY KEY(timestamp, from_alliance_id, to_alliance_id, treaty_type, action))";
    private static final String DROP_TREATY_CHANGES_UNIFIED = "DROP TABLE IF EXISTS TREATY_CHANGES_UNIFIED";
    private static final String DROP_LEGACY_TREATY_CHANGES = "DROP TABLE IF EXISTS LEGACY_TREATY_CHANGES";
    private static final String CREATE_TREATY_CHECKPOINTS = "CREATE TABLE IF NOT EXISTS TREATY_CHECKPOINTS (day INT NOT NULL PRIMARY KEY, source_cursor_ms BIGINT NOT NULL, payload BLOB NOT NULL)";
    private static final String CREATE_TOP_N_SCORE_BY_DAY = "CREATE TABLE IF NOT EXISTS TOP_N_SCORE_BY_DAY (day INT NOT NULL PRIMARY KEY, payload BLOB NOT NULL)";
    private static final String CREATE_FLAG_CHANGES = "CREATE TABLE IF NOT EXISTS FLAG_CHANGES (alliance_id INT NOT NULL, day INT NOT NULL, flag_hash BLOB NULL, PRIMARY KEY(alliance_id, day))";
    private static final String CREATE_FLAG_ICONS = "CREATE TABLE IF NOT EXISTS FLAG_ICONS (flag_hash BLOB NOT NULL PRIMARY KEY, icon_png BLOB NOT NULL)";
    private static final String CREATE_FLAG_ATLAS_STATE = "CREATE TABLE IF NOT EXISTS FLAG_ATLAS_STATE (flag_hash BLOB NOT NULL PRIMARY KEY, tile_index INT NOT NULL)";
    private static final String CREATE_LAST_FLAG_URLS = "CREATE TABLE IF NOT EXISTS LAST_FLAG_URLS (alliance_id INT NOT NULL PRIMARY KEY, flag_url VARCHAR(512) NULL, flag_hash BLOB NULL)";
    private static final String CREATE_LAST_ALLIANCE_SCORES = "CREATE TABLE IF NOT EXISTS LAST_ALLIANCE_SCORES (alliance_id INT NOT NULL PRIMARY KEY, quantized_score INT NOT NULL)";
    private static final String CREATE_RUNTIME_BOOTSTRAP_STATE = "CREATE TABLE IF NOT EXISTS RUNTIME_BOOTSTRAP_STATE ("
            + "singleton TINYINT NOT NULL PRIMARY KEY, "
            + "treaty_import_complete TINYINT NOT NULL DEFAULT 0, "
            + "score_import_complete TINYINT NOT NULL DEFAULT 0, "
            + "flag_import_complete TINYINT NOT NULL DEFAULT 0, "
            + "validation_complete TINYINT NOT NULL DEFAULT 0, "
            + "imported_treaty_min_day INT NULL, "
            + "imported_treaty_max_day INT NULL, "
            + "imported_score_min_day INT NULL, "
            + "imported_score_max_day INT NULL, "
            + "imported_flag_min_day INT NULL, "
            + "imported_flag_max_day INT NULL, "
            + "bridge_score_start_day INT NULL, "
            + "bridge_score_end_day INT NULL, "
            + "last_payload_sha256 BLOB NULL)";
    private static final String ENSURE_BOOTSTRAP_ROW = "INSERT OR IGNORE INTO RUNTIME_BOOTSTRAP_STATE(singleton, treaty_import_complete, score_import_complete, flag_import_complete, validation_complete) VALUES(1, 0, 0, 0, 0)";

    private final DBMainV2 db;
    private volatile boolean initialized;

    public TreatyVisRuntimeRepository(DBMainV2 db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public synchronized void ensureTables() {
        if (initialized) {
            return;
        }
        db.executeStmt(DROP_LEGACY_TREATY_CHANGES);
        db.executeStmt(CREATE_TREATY_CHANGES_UNIFIED);
        db.executeStmt(CREATE_TREATY_CHECKPOINTS);
        db.executeStmt(CREATE_TOP_N_SCORE_BY_DAY);
        db.executeStmt(CREATE_FLAG_CHANGES);
        db.executeStmt(CREATE_FLAG_ICONS);
        db.executeStmt(CREATE_FLAG_ATLAS_STATE);
        db.executeStmt(CREATE_LAST_FLAG_URLS);
        db.executeStmt(CREATE_LAST_ALLIANCE_SCORES);
        db.executeStmt(CREATE_RUNTIME_BOOTSTRAP_STATE);
        db.executeStmt(ENSURE_BOOTSTRAP_ROW);
        initialized = true;
    }

    public synchronized void replaceTopNScoreRows(Map<Integer, byte[]> payloadByDay) {
        ensureTables();
        db.executeBatch(payloadByDay.entrySet(), "INSERT OR REPLACE INTO TOP_N_SCORE_BY_DAY(day, payload) VALUES(?, ?)", (entry, stmt) -> {
            try {
                stmt.setInt(1, entry.getKey());
                stmt.setBytes(2, entry.getValue());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized int clearImportedScoreHistory() {
        ensureTables();
        int deletedDays = countTopNScoreRows();
        db.executeStmt("DELETE FROM TOP_N_SCORE_BY_DAY");
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET score_import_complete = 0, imported_score_min_day = NULL, imported_score_max_day = NULL, bridge_score_start_day = NULL, bridge_score_end_day = NULL WHERE singleton = 1", stmt -> {
        });
        return deletedDays;
    }

    public synchronized void replaceUnifiedTreatyChanges(List<UnifiedTreatyChangeRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM TREATY_CHANGES_UNIFIED");
        db.executeBatch(rows, "INSERT OR REPLACE INTO TREATY_CHANGES_UNIFIED(timestamp, action, treaty_type, from_alliance_id, to_alliance_id, turns_remaining) VALUES(?, ?, ?, ?, ?, ?)", (row, stmt) -> {
            try {
                stmt.setLong(1, row.timestamp());
                stmt.setInt(2, row.action().ordinal());
                stmt.setInt(3, row.treatyType().ordinal());
                stmt.setInt(4, row.fromAllianceId());
                stmt.setInt(5, row.toAllianceId());
                stmt.setInt(6, row.turnsRemaining());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized int clearUnifiedTreatyHistory() {
        ensureTables();
        int deletedRows = countUnifiedTreatyChanges();
        db.executeStmt(DROP_TREATY_CHANGES_UNIFIED);
        db.executeStmt(DROP_LEGACY_TREATY_CHANGES);
        initialized = false;
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET treaty_import_complete = 0, imported_treaty_min_day = NULL, imported_treaty_max_day = NULL WHERE singleton = 1", stmt -> {
        });
        return deletedRows;
    }

    public synchronized void replaceTreatyCheckpoint(TreatyCheckpoint checkpoint) {
        ensureTables();
        byte[] payload = TreatyVisRuntimeTreatyCheckpointCodec.encode(checkpoint.entries());
        db.update("INSERT OR REPLACE INTO TREATY_CHECKPOINTS(day, source_cursor_ms, payload) VALUES(?, ?, ?)", stmt -> {
            try {
                stmt.setInt(1, checkpoint.day());
                stmt.setLong(2, checkpoint.sourceCursorMs());
                stmt.setBytes(3, payload);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized int clearTreatyCheckpoints() {
        ensureTables();
        int deletedRows = countTreatyCheckpoints();
        db.executeStmt("DELETE FROM TREATY_CHECKPOINTS");
        return deletedRows;
    }

    public synchronized void replaceFlagChanges(List<FlagChangeRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM FLAG_CHANGES");
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_CHANGES(alliance_id, day, flag_hash) VALUES(?, ?, ?)", (row, stmt) -> {
            try {
                stmt.setInt(1, row.allianceId());
                stmt.setInt(2, row.day());
                if (row.flagHash() == null) {
                    stmt.setNull(3, java.sql.Types.BLOB);
                } else {
                    stmt.setBytes(3, row.flagHash());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void appendFlagChanges(List<FlagChangeRow> rows) {
        ensureTables();
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_CHANGES(alliance_id, day, flag_hash) VALUES(?, ?, ?)", (row, stmt) -> {
            try {
                stmt.setInt(1, row.allianceId());
                stmt.setInt(2, row.day());
                if (row.flagHash() == null) {
                    stmt.setNull(3, java.sql.Types.BLOB);
                } else {
                    stmt.setBytes(3, row.flagHash());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void replaceLastAllianceScores(List<LastAllianceScoreRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM LAST_ALLIANCE_SCORES");
        db.executeBatch(rows, "INSERT OR REPLACE INTO LAST_ALLIANCE_SCORES(alliance_id, quantized_score) VALUES(?, ?)", (row, stmt) -> {
            try {
                stmt.setInt(1, row.allianceId());
                stmt.setInt(2, row.quantizedScore());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void replaceFlagIcons(List<FlagIconRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM FLAG_ICONS");
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_ICONS(flag_hash, icon_png) VALUES(?, ?)", (row, stmt) -> {
            try {
                stmt.setBytes(1, row.flagHash());
                stmt.setBytes(2, row.iconBytes());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void upsertFlagIcons(List<FlagIconRow> rows) {
        ensureTables();
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_ICONS(flag_hash, icon_png) VALUES(?, ?)", (row, stmt) -> {
            try {
                stmt.setBytes(1, row.flagHash());
                stmt.setBytes(2, row.iconBytes());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void replaceFlagAtlasState(List<FlagAtlasStateRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM FLAG_ATLAS_STATE");
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_ATLAS_STATE(flag_hash, tile_index) VALUES(?, ?)", (row, stmt) -> {
            try {
                stmt.setBytes(1, row.flagHash());
                stmt.setInt(2, row.tileIndex());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void upsertFlagAtlasState(List<FlagAtlasStateRow> rows) {
        ensureTables();
        db.executeBatch(rows, "INSERT OR REPLACE INTO FLAG_ATLAS_STATE(flag_hash, tile_index) VALUES(?, ?)", (row, stmt) -> {
            try {
                stmt.setBytes(1, row.flagHash());
                stmt.setInt(2, row.tileIndex());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void replaceLastFlagUrls(List<LastFlagUrlRow> rows) {
        ensureTables();
        db.executeStmt("DELETE FROM LAST_FLAG_URLS");
        db.executeBatch(rows, "INSERT OR REPLACE INTO LAST_FLAG_URLS(alliance_id, flag_url, flag_hash) VALUES(?, ?, ?)", (row, stmt) -> {
            try {
                stmt.setInt(1, row.allianceId());
                stmt.setString(2, row.flagUrl());
                if (row.flagHash() == null) {
                    stmt.setNull(3, java.sql.Types.BLOB);
                } else {
                    stmt.setBytes(3, row.flagHash());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void upsertLastFlagUrls(List<LastFlagUrlRow> rows) {
        ensureTables();
        db.executeBatch(rows, "INSERT OR REPLACE INTO LAST_FLAG_URLS(alliance_id, flag_url, flag_hash) VALUES(?, ?, ?)", (row, stmt) -> {
            try {
                stmt.setInt(1, row.allianceId());
                stmt.setString(2, row.flagUrl());
                if (row.flagHash() == null) {
                    stmt.setNull(3, java.sql.Types.BLOB);
                } else {
                    stmt.setBytes(3, row.flagHash());
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized int clearImportedFlagHistory() {
        ensureTables();
        int deletedChanges = countFlagChanges();
        db.executeStmt("DELETE FROM FLAG_CHANGES");
        db.executeStmt("DELETE FROM FLAG_ICONS");
        db.executeStmt("DELETE FROM FLAG_ATLAS_STATE");
        db.executeStmt("DELETE FROM LAST_FLAG_URLS");
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET flag_import_complete = 0, imported_flag_min_day = NULL, imported_flag_max_day = NULL WHERE singleton = 1", stmt -> {
        });
        return deletedChanges;
    }

    public synchronized int clearLastAllianceScores() {
        ensureTables();
        int deletedRows = countLastAllianceScores();
        db.executeStmt("DELETE FROM LAST_ALLIANCE_SCORES");
        return deletedRows;
    }

    public synchronized void markHistoricalScoreImportComplete(int minDay, int maxDay) {
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET score_import_complete = 1, imported_score_min_day = ?, imported_score_max_day = ? WHERE singleton = 1", stmt -> {
            try {
                stmt.setInt(1, minDay);
                stmt.setInt(2, maxDay);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void markHistoricalFlagImportComplete(int minDay, int maxDay) {
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET flag_import_complete = 1, imported_flag_min_day = ?, imported_flag_max_day = ? WHERE singleton = 1", stmt -> {
            try {
                stmt.setInt(1, minDay);
                stmt.setInt(2, maxDay);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void markHistoricalTreatyImportComplete(int minDay, int maxDay) {
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET treaty_import_complete = 1, imported_treaty_min_day = ?, imported_treaty_max_day = ? WHERE singleton = 1", stmt -> {
            try {
                stmt.setInt(1, minDay);
                stmt.setInt(2, maxDay);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized RuntimeBootstrapState loadBootstrapState() {
        ensureTables();
        return db.select("SELECT treaty_import_complete, imported_treaty_min_day, imported_treaty_max_day, score_import_complete, imported_score_min_day, imported_score_max_day, flag_import_complete, imported_flag_min_day, imported_flag_max_day, validation_complete FROM RUNTIME_BOOTSTRAP_STATE WHERE singleton = 1", stmt -> {
        }, rs -> {
            try {
                if (!rs.next()) {
                    return new RuntimeBootstrapState(false, null, null, false, null, null, false, null, null, false);
                }
                boolean treatyImportComplete = rs.getInt(1) != 0;
                Integer importedTreatyMinDay = getNullableInt(rs, 2);
                Integer importedTreatyMaxDay = getNullableInt(rs, 3);
                boolean scoreImportComplete = rs.getInt(4) != 0;
                Integer minDay = getNullableInt(rs, 5);
                Integer maxDay = getNullableInt(rs, 6);
                boolean flagImportComplete = rs.getInt(7) != 0;
                Integer importedFlagMinDay = getNullableInt(rs, 8);
                Integer importedFlagMaxDay = getNullableInt(rs, 9);
                boolean validationComplete = rs.getInt(10) != 0;
                return new RuntimeBootstrapState(treatyImportComplete, importedTreatyMinDay, importedTreatyMaxDay, scoreImportComplete, minDay, maxDay, flagImportComplete, importedFlagMinDay, importedFlagMaxDay, validationComplete);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void setValidationComplete(boolean validationComplete) {
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET validation_complete = ? WHERE singleton = 1", stmt -> {
            try {
                stmt.setInt(1, validationComplete ? 1 : 0);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void setLastPayloadSha256(byte[] payloadSha256) {
        ensureTables();
        db.update("UPDATE RUNTIME_BOOTSTRAP_STATE SET last_payload_sha256 = ? WHERE singleton = 1", stmt -> {
            try {
                if (payloadSha256 == null) {
                    stmt.setNull(1, java.sql.Types.BLOB);
                } else {
                    stmt.setBytes(1, payloadSha256);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void resetBootstrapState() {
        ensureTables();
        db.executeStmt(DROP_LEGACY_TREATY_CHANGES);
        db.update(
                "UPDATE RUNTIME_BOOTSTRAP_STATE SET treaty_import_complete = 0, score_import_complete = 0, flag_import_complete = 0, validation_complete = 0, imported_treaty_min_day = NULL, imported_treaty_max_day = NULL, imported_score_min_day = NULL, imported_score_max_day = NULL, imported_flag_min_day = NULL, imported_flag_max_day = NULL, bridge_score_start_day = NULL, bridge_score_end_day = NULL, last_payload_sha256 = NULL WHERE singleton = 1",
                stmt -> {
                }
        );
    }

    synchronized Map<Integer, byte[]> loadTopNScoreRows() {
        ensureTables();
        return db.select("SELECT day, payload FROM TOP_N_SCORE_BY_DAY ORDER BY day ASC", stmt -> {
        }, rs -> {
            try {
                Map<Integer, byte[]> rows = new TreeMap<>();
                while (rs.next()) {
                    rows.put(rs.getInt(1), rs.getBytes(2));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized byte[] loadLastPayloadSha256() {
        ensureTables();
        return db.select("SELECT last_payload_sha256 FROM RUNTIME_BOOTSTRAP_STATE WHERE singleton = 1", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getBytes(1) : null;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized int countTopNScoreRows() {
        ensureTables();
        return db.select("SELECT COUNT(*) FROM TOP_N_SCORE_BY_DAY", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized int countLastAllianceScores() {
        ensureTables();
        return db.select("SELECT COUNT(*) FROM LAST_ALLIANCE_SCORES", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized Map<Integer, Integer> loadLastAllianceScores() {
        ensureTables();
        return db.select("SELECT alliance_id, quantized_score FROM LAST_ALLIANCE_SCORES ORDER BY alliance_id ASC", stmt -> {
        }, rs -> {
            try {
                Map<Integer, Integer> rows = new LinkedHashMap<>();
                while (rs.next()) {
                    rows.put(rs.getInt(1), rs.getInt(2));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized int countUnifiedTreatyChanges() {
        ensureTables();
        return db.select("SELECT COUNT(*) FROM TREATY_CHANGES_UNIFIED", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized int countTreatyCheckpoints() {
        ensureTables();
        return db.select("SELECT COUNT(*) FROM TREATY_CHECKPOINTS", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized TreatyCheckpoint loadLatestTreatyCheckpoint() {
        ensureTables();
        return db.select("SELECT day, source_cursor_ms, payload FROM TREATY_CHECKPOINTS ORDER BY day DESC LIMIT 1", stmt -> {
        }, rs -> {
            try {
                if (!rs.next()) {
                    return null;
                }
                int day = rs.getInt(1);
                long sourceCursorMs = rs.getLong(2);
                byte[] payload = rs.getBytes(3);
                return new TreatyCheckpoint(day, sourceCursorMs, TreatyVisRuntimeTreatyCheckpointCodec.decode(payload));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized TreatyCheckpoint loadEarliestTreatyCheckpoint() {
        ensureTables();
        return db.select("SELECT day, source_cursor_ms, payload FROM TREATY_CHECKPOINTS ORDER BY day ASC LIMIT 1", stmt -> {
        }, rs -> {
            try {
                if (!rs.next()) {
                    return null;
                }
                int day = rs.getInt(1);
                long sourceCursorMs = rs.getLong(2);
                byte[] payload = rs.getBytes(3);
                return new TreatyCheckpoint(day, sourceCursorMs, TreatyVisRuntimeTreatyCheckpointCodec.decode(payload));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized List<DBTreatyChange> loadUnifiedTreatyChangesSince(long timestamp) {
        ensureTables();
        return db.select(
                "SELECT timestamp, action, treaty_type, from_alliance_id, to_alliance_id, turns_remaining FROM TREATY_CHANGES_UNIFIED WHERE timestamp >= ? ORDER BY timestamp ASC",
                stmt -> {
                    try {
                        stmt.setLong(1, timestamp);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                rs -> {
                    try {
                        List<DBTreatyChange> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(readTreatyChange(rs));
                        }
                        return rows;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    synchronized List<DBTreatyChange> loadLegacyTreatyChangesSource() {
        ensureTables();
        boolean hasLegacyTreatyChanges;
        try {
            hasLegacyTreatyChanges = db.tableExists("TREATY_CHANGES");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (!hasLegacyTreatyChanges) {
            return List.of();
        }
        return db.select(
                "SELECT timestamp, action, treaty_type, from_alliance_id, to_alliance_id, turns_remaining FROM TREATY_CHANGES ORDER BY timestamp ASC",
                stmt -> {
                },
                rs -> {
                    try {
                        List<DBTreatyChange> rows = new ArrayList<>();
                        while (rs.next()) {
                            rows.add(readTreatyChange(rs));
                        }
                        return rows;
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    synchronized int countFlagChanges() {
        ensureTables();
        return db.select("SELECT COUNT(*) FROM FLAG_CHANGES", stmt -> {
        }, rs -> {
            try {
                return rs.next() ? rs.getInt(1) : 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized List<FlagChangeRow> loadFlagChangeRows() {
        ensureTables();
        return db.select("SELECT alliance_id, day, flag_hash FROM FLAG_CHANGES ORDER BY day ASC, alliance_id ASC", stmt -> {
        }, rs -> {
            try {
                List<FlagChangeRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new FlagChangeRow(rs.getInt(1), rs.getInt(2), rs.getBytes(3)));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized Map<Integer, byte[]> loadLastFlagHashesByAlliance() {
        ensureTables();
        return db.select("SELECT alliance_id, flag_hash FROM LAST_FLAG_URLS ORDER BY alliance_id ASC", stmt -> {
        }, rs -> {
            try {
                Map<Integer, byte[]> rows = new LinkedHashMap<>();
                while (rs.next()) {
                    rows.put(rs.getInt(1), rs.getBytes(2));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized List<FlagIconRow> loadFlagIconRows() {
        ensureTables();
        return db.select("SELECT flag_hash, icon_png FROM FLAG_ICONS ORDER BY flag_hash ASC", stmt -> {
        }, rs -> {
            try {
                List<FlagIconRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new FlagIconRow(rs.getBytes(1), rs.getBytes(2)));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized Map<Integer, LastFlagUrlRow> loadLastFlagUrlRows() {
        ensureTables();
        return db.select("SELECT alliance_id, flag_url, flag_hash FROM LAST_FLAG_URLS ORDER BY alliance_id ASC", stmt -> {
        }, rs -> {
            try {
                Map<Integer, LastFlagUrlRow> rows = new LinkedHashMap<>();
                while (rs.next()) {
                    rows.put(rs.getInt(1), new LastFlagUrlRow(rs.getInt(1), rs.getString(2), rs.getBytes(3)));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    synchronized List<FlagAtlasStateRow> loadFlagAtlasStateRows() {
        ensureTables();
        return db.select("SELECT flag_hash, tile_index FROM FLAG_ATLAS_STATE ORDER BY tile_index ASC", stmt -> {
        }, rs -> {
            try {
                List<FlagAtlasStateRow> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new FlagAtlasStateRow(rs.getBytes(1), rs.getInt(2)));
                }
                return rows;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Integer getNullableInt(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static DBTreatyChange readTreatyChange(ResultSet rs) throws SQLException {
        return new DBTreatyChange(
                rs.getLong(1),
                TreatyChangeAction.values[rs.getInt(2)],
                TreatyType.values[rs.getInt(3)],
                rs.getInt(4),
                rs.getInt(5),
                rs.getInt(6)
        );
    }

    public record RuntimeBootstrapState(
            boolean treatyImportComplete,
            Integer importedTreatyMinDay,
            Integer importedTreatyMaxDay,
            boolean scoreImportComplete,
            Integer importedScoreMinDay,
            Integer importedScoreMaxDay,
            boolean flagImportComplete,
            Integer importedFlagMinDay,
            Integer importedFlagMaxDay,
            boolean validationComplete
    ) {
    }

        public record UnifiedTreatyChangeRow(
            long timestamp,
            TreatyChangeAction action,
            TreatyType treatyType,
            int fromAllianceId,
            int toAllianceId,
            int turnsRemaining
        ) {
        }

        public record FlagChangeRow(int allianceId, int day, byte[] flagHash) {
        }

        public record TreatyCheckpoint(int day, long sourceCursorMs, List<TreatyCheckpointEntry> entries) {
        }

        public record TreatyCheckpointEntry(int fromAllianceId, int toAllianceId, TreatyType treatyType, int turnsRemaining) {
        }

        public record FlagIconRow(byte[] flagHash, byte[] iconBytes) {
        }

        public record FlagAtlasStateRow(byte[] flagHash, int tileIndex) {
        }

        public record LastFlagUrlRow(int allianceId, String flagUrl, byte[] flagHash) {
        }

        public record LastAllianceScoreRow(int allianceId, int quantizedScore) {
        }
}