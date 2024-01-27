package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class ConflictManager {
    private final WarDB db;
    private Map<Integer, Conflict> conflictMap;

    public ConflictManager(WarDB db) {
        this.db = db;
        conflictMap = new Int2ObjectOpenHashMap<>();
    }

    public void createTables() {
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflicts (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR, start BIGINT, end BIGINT)");
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER, alliance_id INTEGER, side BOOLEAN, PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
    }

    public void addConflict(String name, long start) {
        addConflict(name, start, Long.MAX_VALUE);
    }

    public Conflict addConflict(String name, long start, long end) {
        String query = "INSERT INTO conflicts (name, start, end) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setLong(2, start);
            stmt.setLong(3, end);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                Conflict conflict = new Conflict(id, name, start, end);
                conflictMap.put(id, conflict);
                return conflict;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateConflict(int conflictId, long start, long end) {
        db.update("UPDATE conflicts SET start = ?, end = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, start);
            stmt.setLong(2, end);
            stmt.setInt(3, conflictId);
        });
    }

    protected void addParticipant(int allianceId, int conflictId, boolean side) {
        db.update("INSERT INTO conflict_participant (conflict_id, alliance_id, side) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setInt(2, allianceId);
            stmt.setBoolean(3, side);
        });
    }

    protected void removeParticipant(int allianceId, int conflictId) {
        db.update("DELETE FROM conflict_participant WHERE alliance_id = ? AND conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, conflictId);
        });
    }

    protected void loadConflicts() {
        conflictMap.clear();
        db.query("SELECT * FROM conflicts", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long start = rs.getLong("start");
                long end = rs.getLong("end");
                conflictMap.put(id, new Conflict(id, name, start, end));
            }
        });
        db.query("SELECT * FROM conflict_participant", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int conflictId = rs.getInt("conflict_id");
                int allianceId = rs.getInt("alliance_id");
                boolean side = rs.getBoolean("side");
                Conflict conflict = conflictMap.get(conflictId);
                if (conflict != null) {
                    conflict.addParticipant(allianceId, side, false);
                }
            }
        });
    }

    public Map<Integer, Conflict> getConflictMap() {
        return conflictMap;
    }

    public List<Conflict> getActiveConflicts() {
        return conflictMap.values().stream().filter(conflict -> conflict.getEnd() == Long.MAX_VALUE).toList();
    }

    public Conflict getConflict(String conflictName) {
        for (Conflict conflict : getConflictMap().values()) {
            if (conflict.getName().equalsIgnoreCase(conflictName)) {
                return conflict;
            }
        }
        return null;
    }
}
