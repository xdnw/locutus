package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.db.conflict.LegacyAllianceNames;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AllianceNameHistoryRepository {
    public static final String TABLE_NAME = "legacy_names2";

    private final WarDB db;
    private final Map<Integer, String> namesById = new Int2ObjectOpenHashMap<>();
    private final Map<String, Map<Long, Integer>> idsByDate = new ConcurrentHashMap<>();

    public AllianceNameHistoryRepository(WarDB db) {
        this.db = db;
        db.executeStmt("CREATE TABLE IF NOT EXISTS " + TABLE_NAME
                + " (id INTEGER NOT NULL, name VARCHAR NOT NULL, date BIGINT DEFAULT 0, PRIMARY KEY (id, name, date))");
        reload();
    }

    public synchronized void reload() {
        synchronized (namesById) {
            namesById.clear();
        }
        synchronized (idsByDate) {
            idsByDate.clear();
        }

        db.query("SELECT * FROM " + TABLE_NAME, stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                mergeNameEntry(rs.getInt("id"), rs.getString("name"), rs.getLong("date"));
            }
        });

        for (Map.Entry<String, Integer> entry : LegacyAllianceNames.get().entrySet()) {
            int id = entry.getValue();
            String name = entry.getKey();
            synchronized (namesById) {
                namesById.putIfAbsent(id, name);
            }
            String nameLower = name.toLowerCase(Locale.ROOT);
            synchronized (idsByDate) {
                Map<Long, Integer> byDate = idsByDate.computeIfAbsent(nameLower, k -> new Long2IntOpenHashMap());
                if (byDate.isEmpty()) {
                    byDate.put(Long.MAX_VALUE, id);
                }
            }
        }
    }

    public void importAllianceNamesFromDataDump() throws IOException, ParseException {
        DataDumpParser parser = Locutus.imp().getDataDumper(true).load();
        List<Long> days = new ArrayList<>(parser.getDays(false, false, true));
        days.sort(Long::compareTo);
        for (long day : days) {
            long date = TimeUtil.getTimeFromDay(day);
            for (DBAlliance alliance : parser.getAlliances(day).values()) {
                int allianceId = alliance.getAlliance_id();
                if (allianceId == 0) {
                    continue;
                }
                String name = alliance.getName();
                if (name != null && !name.isEmpty()) {
                    addName(allianceId, name, date);
                }
            }
        }
    }

    public void addName(int id, String name, long date) {
        if (id == 0 || name == null || name.isBlank()) {
            return;
        }
        String normalizedName = name.trim();
        String nameLower = normalizedName.toLowerCase(Locale.ROOT);
        boolean shouldInsert = false;
        synchronized (idsByDate) {
            Map<Long, Integer> byDate = idsByDate.computeIfAbsent(nameLower, f -> new Long2IntOpenHashMap());
            Integer otherId = null;
            Long lastDate = null;
            for (Map.Entry<Long, Integer> entry : byDate.entrySet()) {
                long otherDate = entry.getKey();
                if (otherDate <= date && (lastDate == null || otherDate > lastDate)) {
                    lastDate = otherDate;
                    otherId = entry.getValue();
                }
            }
            if (!Objects.equals(otherId, id)) {
                byDate.put(date, id);
                shouldInsert = true;
            }
        }
        if (!shouldInsert) {
            return;
        }
        synchronized (namesById) {
            namesById.putIfAbsent(id, normalizedName);
        }
        db.update("INSERT OR IGNORE INTO " + TABLE_NAME + " (id, name, date) VALUES (?, ?, ?)",
                (ThrowingConsumer<PreparedStatement>) stmt -> {
                    stmt.setInt(1, id);
                    stmt.setString(2, normalizedName);
                    stmt.setLong(3, date);
                });
    }

    public Integer getAllianceId(String name, long date, boolean parseInt) {
        Integer id = getAllianceId(name, date);
        if (id == null && parseInt && MathMan.isInteger(name)) {
            id = Integer.parseInt(name);
        }
        return id;
    }

    public Integer getAllianceId(String name, long date) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        synchronized (idsByDate) {
            Map<Long, Integer> ids = idsByDate.get(nameLower);
            if (ids != null && !ids.isEmpty()) {
                if (ids.size() == 1) {
                    return ids.values().iterator().next();
                }
                Long lastDate = null;
                Integer lastId = null;
                for (Map.Entry<Long, Integer> entry : ids.entrySet()) {
                    long otherDate = entry.getKey();
                    if (lastDate == null || (otherDate <= date && otherDate > lastDate)) {
                        lastDate = otherDate;
                        lastId = entry.getValue();
                    }
                }
                return lastId;
            }
        }
        DBAlliance alliance = DBAlliance.parse(name, false);
        if (alliance != null) {
            addName(alliance.getId(), name, 0);
            return alliance.getId();
        }
        return null;
    }

    public String getAllianceName(int id) {
        String name = getAllianceNameOrNull(id);
        return name == null ? "AA:" + id : name;
    }

    public String getAllianceNameOrNull(int id) {
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance != null) {
            return alliance.getName();
        }
        synchronized (namesById) {
            return namesById.get(id);
        }
    }

    public Map.Entry<String, Double> getMostSimilar(String name) {
        double distance = Integer.MAX_VALUE;
        String similar = null;
        synchronized (namesById) {
            for (Map.Entry<Integer, String> entry : namesById.entrySet()) {
                double currentDistance = StringMan.distanceWeightedQwertSift4(name, entry.getValue());
                if (currentDistance < distance) {
                    distance = currentDistance;
                    similar = entry.getValue();
                }
            }
        }
        return distance == Integer.MAX_VALUE ? null : KeyValue.of(similar, distance);
    }

    private void mergeNameEntry(int id, String name, long date) {
        if (id == 0 || name == null || name.isBlank()) {
            return;
        }
        synchronized (namesById) {
            namesById.putIfAbsent(id, name);
        }
        String nameLower = name.toLowerCase(Locale.ROOT);
        synchronized (idsByDate) {
            idsByDate.computeIfAbsent(nameLower, k -> new Long2IntOpenHashMap()).put(date, id);
        }
    }
}