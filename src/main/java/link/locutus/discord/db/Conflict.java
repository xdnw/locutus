package link.locutus.discord.db;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.jooby.JteUtil;

import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Conflict {
    private final int id;
    private String name;
    private long turnStart;
    private long turnEnd;
    private final Map<Integer, Long> startTime = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Long> endTime = new Int2ObjectOpenHashMap<>();
    private final CoalitionSide coalition1 = new CoalitionSide(this, "Coalition 1");
    private final CoalitionSide coalition2 = new CoalitionSide(this, "Coalition 2");
    private String b64;
    private volatile boolean dirtyWars = false;
    private volatile boolean dirtyJson = true;

    public synchronized String getJsonB64(ConflictManager manager) {
        if (dirtyWars) {
            // TODO
        }
        if (dirtyJson || b64 == null) {
            try {
                JsonObject root = new JsonObject();
                JsonArray coalitions = new JsonArray();
                coalitions.add(coalition1.toJson(manager));
                coalitions.add(coalition2.toJson(manager));
                root.add("coalitions", coalitions);


                Map<String, Function<OffDefStatGroup, Object>> offDefHeader = OffDefStatGroup.createHeader();
                root.add("counts_header", JteUtil.createArrayCol(offDefHeader.keySet()));
                Map<String, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
                root.add("damage_header", JteUtil.createArrayCol(damageHeader.keySet()));

                return b64 = Base64.getEncoder().encodeToString(root.toString().getBytes());
            } finally {
                System.out.println(b64.length());
                dirtyJson = false;
            }
        }
        return b64;
    }

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
        dirtyJson = true;
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
        dirtyJson = true;
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
        dirtyJson = true;
    }

    private ConflictManager getManager() {
        return Locutus.imp().getWarDb().getConflicts();
    }

    public Conflict setName(String name) {
        this.name = name;
        getManager().updateConflict(id, turnStart, turnEnd);
        dirtyJson = true;
        return this;
    }

    public Conflict setStart(long time) {
        this.turnStart = TimeUtil.getTurn(time);
        getManager().updateConflict(id, turnStart, turnEnd);
        dirtyJson = true;
        return this;
    }

    public Conflict setEnd(long time) {
        this.turnEnd = TimeUtil.getTurn(time) + 1;
        getManager().updateConflict(id, turnStart, turnEnd);
        dirtyJson = true;
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
            dirtyWars = true;
        }
        dirtyJson = true;
        return this;
    }

    public Conflict removeParticipant(int allianceId) {
        coalition1.remove(allianceId);
        coalition2.remove(allianceId);
        startTime.remove(allianceId);
        endTime.remove(allianceId);
        getManager().removeParticipant(allianceId, id);
        dirtyWars = true;
        dirtyJson = true;
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
        return coalition1.getAllianceIds();
    }

    public Set<Integer> getCoalition2() {
        return coalition2.getAllianceIds();
    }

    public Set<DBAlliance> getCoalition1Obj() {
        return coalition1.getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getCoalition2Obj() {
        return coalition2.getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Boolean getSide(int allianceId) {
        if (coalition1.hasAlliance(allianceId)) return true;
        if (coalition2.hasAlliance(allianceId)) return false;
        return null;
    }

    public Set<Integer> getAllianceIds() {
        Set<Integer> ids = new IntOpenHashSet(coalition1.getAllianceIds().size() + coalition2.getAllianceIds().size());
        ids.addAll(coalition1.getAllianceIds());
        ids.addAll(coalition2.getAllianceIds());
        return ids;
    }

    public int getActiveWars() {
        return coalition1.getOffensiveStats(null, false).activeWars + coalition2.getOffensiveStats(null, false).activeWars;
    }

    public int getTotalWars() {
        return coalition1.getOffensiveStats(null, false).totalWars + coalition2.getOffensiveStats(null, false).totalWars;
    }

    public double getDamageConverted(boolean isPrimary) {
        return (isPrimary ? coalition1 : coalition2).getInflicted().getTotalConverted();
    }
}