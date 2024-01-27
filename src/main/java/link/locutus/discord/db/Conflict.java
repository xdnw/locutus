package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;

import java.util.Set;
import java.util.stream.Collectors;

public class Conflict {
    private final int id;
    private String name;
    private long start;
    private long end;
    private final Set<Integer> coalition1;
    private final Set<Integer> coalition2;

    public Conflict(int id, String name, long start, long end) {
        this.id = id;
        this.name = name;
        this.start = start;
        this.end = end;
        coalition1 = new IntOpenHashSet();
        coalition2 = new IntOpenHashSet();
    }

    private ConflictManager getManager() {
        return Locutus.imp().getWarDb().getConflicts();
    }

    public Conflict setName(String name) {
        this.name = name;
        getManager().updateConflict(id, start, end);
        return this;
    }

    public Conflict setStart(long time) {
        this.start = time;
        getManager().updateConflict(id, start, end);
        return this;
    }

    public Conflict setEnd(long time) {
        this.end = time;
        getManager().updateConflict(id, start, end);
        return this;
    }

    public Conflict addParticipant(int allianceId, boolean side) {
        return addParticipant(allianceId, side, true);
    }

    public Conflict addParticipant(int allianceId, boolean side, boolean save) {
        if (side) coalition1.add(allianceId);
        else coalition2.add(allianceId);
        if (save) {
            getManager().addParticipant(allianceId, id, side);
        }
        return this;
    }

    public Conflict removeParticipant(int allianceId) {
        coalition1.remove(allianceId);
        coalition2.remove(allianceId);
        getManager().removeParticipant(allianceId, id);
        return this;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
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
}