package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.util.scheduler.CachedSupplier;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.ThrowingFunction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static link.locutus.discord.db.conflict.ConflictField.*;

public final class ConflictMeta {
    public final int id;

    public ConflictCategory category;
    public String wiki = "";
    public String casusBelli = "";
    public String statusDesc = "";
    public long createdByServer;

    public String col1, col2;

    private volatile CoalitionSides sidesTmp = null;
    private final CachedSupplier<CoalitionSides> sides;

    private volatile Map<Integer, Long> startTime2 = null;
    private volatile Map<Integer, Long> endTime2 = null;

    private volatile Map<String, DBTopic> announcements2 = null;

    ConflictMeta(Conflict conflict) {
        this.id = conflict.getId();
        this.sides = CachedSupplier.of(() -> {
            if (sidesTmp != null) return sidesTmp;
            CoalitionSides container = sidesTmp = new CoalitionSides(conflict, col1, col2);
            if (id > 0) {
                synchronized (this) {
                    if (startTime2 == null) startTime2 = new Int2LongOpenHashMap();
                    if (endTime2 == null) endTime2 = new Int2LongOpenHashMap();
                    ConflictManager.get().loadParticipantStartEndTimes(id, startTime2, endTime2, container.col1, container.col2);
                }
            }
            sidesTmp = null;
            return container;
        });
    }

    public void addAnnouncement(String desc, DBTopic topic, boolean init) {
        if (announcements2 == null) {
            if (!init && id > 0) return;
            announcements2 = new Object2ObjectOpenHashMap<>();
        }
        announcements2.entrySet().removeIf(f -> f.getValue().topic_id == topic.topic_id);
        announcements2.put(desc, topic);
    }

    public CoalitionSides getSides() {
        return sides.get();
    }

    public CoalitionSides getSidesOrNull() {
        return sides.getOrNull();
    }

    public void setLoaded(Conflict conflict, boolean initWars) {
        synchronized (this) {
            if (startTime2 == null) startTime2 = new Int2LongOpenHashMap();
            if (endTime2 == null) endTime2 = new Int2LongOpenHashMap();
            CoalitionSides tmp = sides.setValueIfAbsent(new CoalitionSides(conflict, col1, col2));
            if (initWars) {
                tmp.col1.setLoaded();
                tmp.col2.setLoaded();
            }
        }
    }

    public void setNameRaw(String col1, boolean side) {
        if (side) {
            this.col1 = col1;
        } else {
            this.col2 = col1;
        }
        CoalitionSides tmp = sides.getOrNull();
        if (tmp != null) {
            if (side) {
                tmp.col1.setNameRaw(this.col1);
            } else {
                tmp.col2.setNameRaw(this.col2);
            }
        }
    }

    public static ConflictMeta create(Conflict conflict, ResultSet rs) throws SQLException {
        ConflictMeta cd = new ConflictMeta(conflict);
        cd.init(rs);
        return cd;
    }

    public Map<Integer, Long> getStartTimeRaw() {
        if (id <= 0) return null;
        return startTime2;
    }

    public Map<Integer, Long> getEndTimeRaw() {
        if (id <= 0) return null;
        return endTime2;
    }

    public long getStartTimeForAlliance(int allianceId, long defValue) {
        if (id >= 0) initStartEndTimes();
        else return startTime2 == null ? defValue : startTime2.getOrDefault(allianceId, defValue);
        synchronized (startTime2) {
            return startTime2.getOrDefault(allianceId, defValue);
        }
    }

    public long getEndTimeForAlliance(int allianceId, long defValue) {
        if (id >= 0) initStartEndTimes();
        else return endTime2 == null ? defValue : endTime2.getOrDefault(allianceId, defValue);
        synchronized (endTime2) {
            return endTime2.getOrDefault(allianceId, defValue);
        }
    }

    private void initStartEndTimes() {
        if (startTime2 == null) {
            synchronized (this) {
                if (startTime2 == null) {
                    startTime2 = new Int2LongOpenHashMap();
                    endTime2 = new Int2LongOpenHashMap();
                    CoalitionSides tmp = sides.getOrNull();
                    CoalitionSide coalition_1 = tmp != null ? tmp.col1 : null;
                    CoalitionSide coalition_2 = tmp != null ? tmp.col2 : null;
                    Locutus.imp().getWarDb().getConflicts().loadParticipantStartEndTimes(id, startTime2, endTime2, coalition_1, coalition_2);
                }
            }
        }
    }

    public void init(ResultSet rs) throws SQLException {
        createdByServer = rs.getLong(CREATOR.toString());
        int categoryIdx = rs.getInt(CATEGORY.toString());
        category = ConflictCategory.values()[categoryIdx];

        String wikiTmp = rs.getString(WIKI.toString());
        wiki = wikiTmp == null ? "" : wikiTmp;

        String cbTmp = rs.getString(CB.toString());
        casusBelli = cbTmp == null ? "" : cbTmp;

        String statusTmp = rs.getString(STATUS.toString());
        statusDesc = statusTmp == null ? "" : statusTmp;

        String col1Tmp = rs.getString(COL1.toString());
        String col2Tmp = rs.getString(COL2.toString());
        col1 = col1Tmp == null ? "" : col1Tmp;
        col2 = col2Tmp == null ? "" : col2Tmp;

        if (col1.isEmpty()) col1 = "Coalition 1";
        if (col2.isEmpty()) col2 = "Coalition 2";

        CoalitionSides tmp = sides.getOrNull();
        if (tmp != null) {
            tmp.col1.setNameRaw(col1);
            tmp.col2.setNameRaw(col2);
        }
    }

    public static ConflictMeta create(Conflict conflict) {
        if (conflict.getId() <= 0) {
            return new ConflictMeta(conflict);
        }
        return ConflictManager.get().getDb().select(
                "SELECT " + String.join(", ",
                        COL1.toString(),
                        COL2.toString(),
                        WIKI.toString(),
                        CB.toString(),
                        STATUS.toString(),
                        CATEGORY.toString(),
                        CREATOR.toString()
                ) + " FROM conflicts WHERE id = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflict.getId()),
                (ThrowingFunction<ResultSet, ConflictMeta>) rs -> create(conflict, rs)
        );
    }

    public Map<String, DBTopic> getAnnouncement() {
        if (id <= 0) return new Object2ObjectOpenHashMap<>();
        if (announcements2 == null) {
            synchronized (this) {
                if (announcements2 == null) {
                    announcements2 = Locutus.imp().getWarDb().getConflicts().loadAnnouncements(id);
                }
            }
        }
        synchronized (announcements2) {
            return new Object2ObjectOpenHashMap<>(announcements2);
        }
    }

    public Map<Integer, String> getAnnouncementsById() {
        Map<Integer, String> map = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<String, DBTopic> entry : getAnnouncement().entrySet()) {
            map.put(entry.getValue().topic_id, entry.getKey());
        }
        return map;
    }

    public CoalitionSide getCoalition1() {
        return sides.get().col1;
    }

    public CoalitionSide getCoalition2() {
        return sides.get().col2;
    }

    public void clearWarData() {
        CoalitionSides tmp = sides.getOrNull();
        if (tmp != null) tmp.clearWarData();
    }

    public boolean isWarsLoaded() {
        CoalitionSides tmp = sides.getOrNull();
        return tmp != null && tmp.col1.isWarsLoaded();
    }

    public boolean hasParticipantsLoaded() {
        return sides.hasValue();
    }

    public boolean hasAnnouncementsLoaded() {
        return announcements2 != null;
    }

    public void removeAnnouncementRaw(int topicId) {
        Map<String, DBTopic> annMap = announcements2;
        if (annMap != null) {
            synchronized (annMap) {
                annMap.entrySet().removeIf(f -> f.getValue().topic_id == topicId);
            }
        }
    }

    public void removeTimeForAlliance(int allianceId) {
        Map<Integer, Long> tmp1 = startTime2;
        Map<Integer, Long> tmp2 = endTime2;
        if (tmp1 != null) {
            synchronized (tmp1) {
                tmp1.remove(allianceId);
            }
        }
        if (tmp2 != null) {
            synchronized (tmp2) {
                tmp2.remove(allianceId);
            }
        }
    }
}