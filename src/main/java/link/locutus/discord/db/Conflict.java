package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.TimeUtil;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Conflict {
    private final int id;
    private String name;
    private long turnStart;
    private long turnEnd;
    private final Map<Integer, Long> startTime = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Long> endTime = new Int2ObjectOpenHashMap<>();
    private final Set<Integer> coalition1;
    private final Set<Integer> coalition2;

    public Conflict(int id, String name, long turnStart, long turnEnd) {
        this.id = id;
        this.name = name;
        this.turnStart = turnStart;
        this.turnEnd = turnEnd;
        coalition1 = new IntOpenHashSet();
        coalition2 = new IntOpenHashSet();
    }

    private void setParticipantTime(int allianceId, long start, long end) {
        if (start > 0) {
            startTime.put(allianceId, start);
        }
        if (end != Long.MAX_VALUE) {
            endTime.put(allianceId, end);
        }
    }

    private ConflictManager getManager() {
        return Locutus.imp().getWarDb().getConflicts();
    }

    public Conflict setName(String name) {
        this.name = name;
        getManager().updateConflict(id, turnStart, turnEnd);
        return this;
    }

    public Conflict setStart(long time) {
        this.turnStart = TimeUtil.getTurn(time);
        getManager().updateConflict(id, turnStart, turnEnd);
        return this;
    }

    public Conflict setEnd(long time) {
        this.turnEnd = TimeUtil.getTurn(time) + 1;
        getManager().updateConflict(id, turnStart, turnEnd);
        return this;
    }

    public Conflict addParticipant(int allianceId, boolean side, Long start, Long end) {
        return addParticipant(allianceId, side, true, start, end);
    }

    public Conflict addParticipant(int allianceId, boolean side, boolean save, Long start, Long end) {
        if (start != null && start > 0) {
            startTime.put(allianceId, start);
        }
        if (end != null && end != Long.MAX_VALUE) {
            endTime.put(allianceId, end);
        }
        if (side) coalition1.add(allianceId);
        else coalition2.add(allianceId);
        if (save) {
            getManager().addParticipant(allianceId, id, side, startTime.getOrDefault(allianceId, 0L), endTime.getOrDefault(allianceId, Long.MAX_VALUE));
        }
        return this;
    }

    public Conflict removeParticipant(int allianceId) {
        coalition1.remove(allianceId);
        coalition2.remove(allianceId);
        startTime.remove(allianceId);
        endTime.remove(allianceId);
        getManager().removeParticipant(allianceId, id);
        return this;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getStartTurn() {
        return turnStart;
    }

    public long getEndTurn() {
        return turnEnd;
    }

    public Set<Integer> getCoalition1() {
        return coalition1;
    }

    public Set<Integer> getCoalition2() {
        return coalition2;
    }

    public Set<DBAlliance> getCoalition1Obj() {
        return coalition1.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getCoalition2Obj() {
        return coalition2.stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Boolean getSide(int allianceId) {
        if (coalition1.contains(allianceId)) return true;
        if (coalition2.contains(allianceId)) return false;
        return null;
    }

    public Set<Integer> getAllianceIds() {
        Set<Integer> ids = new IntOpenHashSet(coalition1);
        ids.addAll(coalition2);
        return ids;
    }
}