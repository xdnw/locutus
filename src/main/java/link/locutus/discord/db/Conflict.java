package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
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
    private final CoalitionSide coalition1 = new CoalitionSide(this);
    private final CoalitionSide coalition2 = new CoalitionSide(this);

    public long getStartTurn(int allianceId) {
        return startTime.getOrDefault(allianceId, turnStart);
    }
    public long getEndTurn(int allianceId) {
        return endTime.getOrDefault(allianceId, turnEnd);
    }

    private CoalitionSide getCoalition(int aaId1, int aaId2, long turn) {
        if (coalition1.hasAlliance(aaId1)) {
            if (coalition2.hasAlliance(aaId2) &&
                    getStartTurn(aaId1) <= turn && getEndTurn(aaId1) > turn &&
                    getStartTurn(aaId2) <= turn && getEndTurn(aaId2) > turn) {
                    return coalition1;
            }
        } else if (coalition2.hasAlliance(aaId1)) {
            if (coalition1.hasAlliance(aaId2) &&
                    getStartTurn(aaId1) <= turn && getEndTurn(aaId1) > turn &&
                    getStartTurn(aaId2) <= turn && getEndTurn(aaId2) > turn) {
                return coalition2;
            }
        }
        return null;
    }

    public void updateWar(DBWar previous, DBWar current, long turn) {
        CoalitionSide side = getCoalition(current.getAttacker_aa(), current.getDefender_aa(), turn);
        if (side == null) return;
        CoalitionSide otherSide = side.getOther();
        side.updateWar(previous, current, true);
        otherSide.updateWar(previous, current, false);
    }

    public void updateAttack(DBWar war, AbstractCursor attack, long turn) {
        int attackerAA, defenderAA;
        if (attack.getAttacker_id() == war.getAttacker_id()) {
            attackerAA = war.getAttacker_aa();
            defenderAA = war.getDefender_aa();
        } else {
            attackerAA = war.getDefender_aa();
            defenderAA = war.getAttacker_aa();
        }
        CoalitionSide side = getCoalition(attackerAA, defenderAA, turn);
        if (side == null) return;
        CoalitionSide otherSide = side.getOther();
        side.updateAttack(war, attack, true);
        otherSide.updateAttack(war, attack, false);
    }

    public Conflict(int id, String name, long turnStart, long turnEnd) {
        this.id = id;
        this.name = name;
        this.turnStart = turnStart;
        this.turnEnd = turnEnd;
        this.coalition1.setOther(coalition2);
        this.coalition2.setOther(coalition1);
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
        return coalition1.get();
    }

    public Set<Integer> getCoalition2() {
        return coalition2.get();
    }

    public Set<DBAlliance> getCoalition1Obj() {
        return coalition1.get().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getCoalition2Obj() {
        return coalition2.get().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Boolean getSide(int allianceId) {
        if (coalition1.hasAlliance(allianceId)) return true;
        if (coalition2.hasAlliance(allianceId)) return false;
        return null;
    }

    public Set<Integer> getAllianceIds() {
        Set<Integer> ids = new IntOpenHashSet(coalition1.get().size() + coalition2.get().size());
        ids.addAll(coalition1.get());
        ids.addAll(coalition2.get());
        return ids;
    }
}