package link.locutus.discord.db;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jpson.PSON;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.conflict.OffDefStatGroup;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.IOException;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Conflict {
    private final int id;
    private String wiki;
    private String name;
    private long turnStart;
    private long turnEnd;
    private final Map<Integer, Long> startTime = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Long> endTime = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, Integer>> warsVsAlliance = new Int2ObjectOpenHashMap<>();
    private final CoalitionSide coalition1;
    private final CoalitionSide coalition2;
    private byte[] bsonCompressed;
    private volatile boolean dirtyWars = false;
    private volatile boolean dirtyJson = true;


    public Conflict(int id, String name, String col1, String col2, String wiki, long turnStart, long turnEnd) {
        this.id = id;
        this.name = name;
        this.wiki = wiki;
        this.turnStart = turnStart;
        this.turnEnd = turnEnd;
        this.coalition1 = new CoalitionSide(this, col1, true);
        this.coalition2 = new CoalitionSide(this, col2, false);
        this.coalition1.setOther(coalition2);
        this.coalition2.setOther(coalition1);
    }

    public void setWiki(String wiki) {
        this.wiki = wiki;
        dirtyJson = true;
        getManager().updateConflictWiki(id, wiki);
    }

    public void setName(String name, boolean isPrimary) {
        if (isPrimary) {
            coalition1.setName(name);
        } else {
            coalition2.setName(name);
        }
        dirtyJson = true;
        getManager().updateConflictName(id, name, isPrimary);
    }

    public void updateGraphsLegacy(ConflictManager manager) throws IOException, ParseException {
        Set<Integer> nationIds = new IntOpenHashSet();
        nationIds.addAll(coalition1.getNationIds());
        nationIds.addAll(coalition2.getNationIds());

        System.out.println("Graph 1");

        long startMs = TimeUtil.getTimeFromTurn(turnStart);
        long endMs = turnEnd == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(turnEnd);

        System.out.println("Graph 2");

        Map<Long, Map<Integer, Map<MilitaryUnit, Integer>>> milHistory = Locutus.imp().getNationDB().getMilitaryHistoryByTurn(nationIds, startMs, endMs);
        Map<Long, Map<Integer, DBNation>> nationsByDay = new HashMap<>();

        DataDumpParser parser = Locutus.imp().getDataDumper(true);

        System.out.println("Graph 3");

        long dayStart = TimeUtil.getDay(TimeUtil.getTimeFromTurn(getStartTurn()));
        long dayEnd = getEndTurn() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getDay(TimeUtil.getTimeFromTurn(getEndTurn() + 11));

        parser.iterateAll(new Predicate<Long>() {
            @Override
            public boolean test(Long day) {
                if (day >= dayStart && day <= dayEnd) {
                    return true;
                }
                return false;
            }
        }, new TriConsumer<Long, DataDumpParser.NationHeader, CsvRow>() {
            @Override
            public void consume(Long day, DataDumpParser.NationHeader header, CsvRow row) {
                int nationId = Integer.parseInt(row.getField(header.nation_id));
                if (!nationIds.contains(nationId)) {
                    int position = Integer.parseInt(row.getField(header.alliance_position));
                    if (position <= Rank.APPLICANT.id) return;
                    int allianceId = Integer.parseInt(row.getField(header.alliance_id));
                    if (!coalition1.hasAlliance(allianceId) && !coalition2.hasAlliance(allianceId)) return;
                }
                long currentTimeMs = TimeUtil.getTimeFromDay(day);
                try {
                    DBNationSnapshot nation = parser.loadNation(header, row, f -> true, f -> true, false, true, currentTimeMs);
                    nationsByDay.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>()).put(nation.getId(), nation);


                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        }, new TriConsumer<Long, DataDumpParser.CityHeader, CsvRow>() {
            @Override
            public void consume(Long day, DataDumpParser.CityHeader cityHeader, CsvRow csvRow) {
                Map<Integer, DBNation> nationMap = nationsByDay.get(day);
                if (nationMap == null) return;
                int nationId = Integer.parseInt(csvRow.getField(cityHeader.nation_id));
                DBNationSnapshot nation = (DBNationSnapshot) nationMap.get(nationId);
                if (nation == null) return;
                try {
                    nation.addCity(parser.loadCity(cityHeader, csvRow, nationId));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        }, null);

        System.out.println("Graph 4");

        long currentTurn = TimeUtil.getTurn();

        long lastDay = nationsByDay.keySet().stream().max(Long::compareTo).orElse(0L);
        Map<Integer, DBNation> latest;
        if (turnEnd == Long.MAX_VALUE || lastDay == 0 || TimeUtil.getTimeFromTurn(turnEnd) > TimeUtil.getTimeFromDay(lastDay)) {
            latest = new Int2ObjectOpenHashMap<>();
            for (int id : nationIds) {
                DBNation nation = DBNation.getById(id);
                if (nation != null) {
                latest.put(id, nation);
                }
            }
            long nextDay = TimeUtil.getDay(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() + 11));
            nationsByDay.put(nextDay, latest);
        } else {
            latest = nationsByDay.get(lastDay);
        }

        System.out.println("Graph 5");

        // save day data
        {
            for (Map.Entry<Long, Map<Integer, DBNation>> entry : nationsByDay.entrySet()) {
                long day = entry.getKey();
                Map<Integer, DBNation> nations = entry.getValue();
                Set<DBNation> col1Nations = new ObjectOpenHashSet<>();
                Set<DBNation> col2Nations = new ObjectOpenHashSet<>();
                for (Map.Entry<Integer, DBNation> nationEntry : nations.entrySet()) {
                    DBNation nation = nationEntry.getValue();
                    if (nation.getVm_turns() > 0 || nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
                    if (nation instanceof DBNationSnapshot snap && !snap.hasCityData()) continue;
                    int aaId = nation.getAlliance_id();
                    long startTurn = getStartTurn(aaId);
                    if (TimeUtil.getDay(TimeUtil.getTimeFromTurn(startTurn)) > day) continue;
                    long endTurn = getEndTurn(aaId);
                    if (endTurn != Long.MAX_VALUE && TimeUtil.getDay(TimeUtil.getTimeFromTurn(endTurn + 11)) < day) continue;
                    if (coalition1.hasAlliance(nation.getAlliance_id())) {
                        col1Nations.add(nation);
                    } else if (coalition2.hasAlliance(nation.getAlliance_id())) {
                        col2Nations.add(nation);
                    }
                }
                coalition1.updateDayTierGraph(manager, day, col1Nations, true, false);
                coalition2.updateDayTierGraph(manager, day, col2Nations, true, false);
            }
        }

        System.out.println("Graph 6");

        {
            MilitaryUnit[] units = {MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP};

            long latestTurn = Math.min(getEndTurn(), currentTurn);
            long latestDay = TimeUtil.getDay(TimeUtil.getTimeFromTurn(latestTurn));
            Map<Integer, Map<MilitaryUnit, Integer>> milCountByNation = new Int2ObjectOpenHashMap<>();

            Consumer<Map<Integer, DBNation>> setMilCount = (nations) -> {
                for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
                    DBNation nation = entry.getValue();
                    Map<MilitaryUnit, Integer> counts = new EnumMap<>(MilitaryUnit.class);
                    for (MilitaryUnit unit : MilitaryUnit.values()) {
                        int count = nation.getUnits(unit);
                        if (count > 0) {
                            counts.put(unit, count);
                        }
                    }
                    milCountByNation.put(entry.getKey(), counts);
                }
            };
            setMilCount.accept(latest);

            for (long turn = latestTurn; turn >= turnStart; turn--) {
                long newDay = TimeUtil.getDay(TimeUtil.getTimeFromTurn(turn));
                boolean setViaParser = false;
                if (turn != latestTurn) {
                    if (newDay != latestDay) {
                        latestDay = newDay;
                        Map<Integer, DBNation> newLatest = nationsByDay.get(latestDay);
                        if (newLatest != null) {
                            latest = newLatest;
                            milCountByNation.clear();
                            setMilCount.accept(latest);
                            setViaParser = true;
                        }
                    }
                    if (!setViaParser) {
                        Map<Integer, Map<MilitaryUnit, Integer>> turnMilHistory = milHistory.get(turn);
                        if (turnMilHistory != null) {
                            for (Map.Entry<Integer, Map<MilitaryUnit, Integer>> entry : turnMilHistory.entrySet()) {
                                int nationId = entry.getKey();
                                Map<MilitaryUnit, Integer> counts = entry.getValue();
                                for (Map.Entry<MilitaryUnit, Integer> milEntry : counts.entrySet()) {
                                    milCountByNation.computeIfAbsent(nationId, k -> new EnumMap<>(MilitaryUnit.class)).put(milEntry.getKey(), milEntry.getValue());
                                }
                            }
                        }
                    }
                }

                Map<Byte, Map<MilitaryUnit, Integer>> unitsByCityCol1 = new Byte2ObjectOpenHashMap<>();
                Map<Byte, Map<MilitaryUnit, Integer>> unitsByCityCol2 = new Byte2ObjectOpenHashMap<>();

                long finalTurn = turn;
                Set<Integer> allowedNations = latest.values().stream().filter(f -> {
                    if (f.getPositionEnum().id <= Rank.APPLICANT.id) return false;
                    int allianceId = f.getAlliance_id();
                    if (!coalition1.hasAlliance(allianceId) && !coalition2.hasAlliance(allianceId)) return false;
                    if (f.getVm_turns() > 0) return false;
                    long turnStart = getStartTurn(allianceId);
                    long turnEnd = getEndTurn(allianceId);
                    return finalTurn >= turnStart && finalTurn <= turnEnd;
                }).map(DBNation::getNation_id).collect(Collectors.toSet());

                for (int id : allowedNations) {
                    DBNation nation = latest.get(id);
                    boolean isPrimary = coalition1.hasAlliance(nation.getAlliance_id());
                    Map<Byte, Map<MilitaryUnit, Integer>> unitsByCity = isPrimary ? unitsByCityCol1 : unitsByCityCol2;
                    byte cities = (byte) nation.getCities();
                    for (MilitaryUnit unit : units) {
                        int count = nation.getUnits(unit);
                        unitsByCity.computeIfAbsent(cities, k -> new EnumMap<>(MilitaryUnit.class)).merge(unit, count, Integer::sum);
                    }
                }

                coalition1.updateTurnTierGraph(manager, turn, unitsByCityCol1, true, false);
                coalition2.updateTurnTierGraph(manager, turn, unitsByCityCol2, true, false);
            }
        }

        System.out.println("Graph 7");
        manager.deleteGraphData(getId());
        System.out.println("Graph 8");
        List<ConflictMetric.Entry> entries = getGraphEntries();
        System.out.println("Entries " + entries.size());
        manager.addGraphData(entries);
        System.out.println("Graph 9");
    }

    public List<ConflictMetric.Entry> getGraphEntries() {
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        entries.addAll(coalition1.getGraphEntries());
        entries.addAll(coalition2.getGraphEntries());
        return entries;
    }

    public synchronized byte[] getGraphPsonGzip(ConflictManager manager) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        root.put("name", getName());
        List<String> metricNames = new ObjectArrayList<>();
        List<Integer> metricsDay = new IntArrayList();
        List<Integer> metricsTurn = new IntArrayList();
        for (ConflictMetric metric : ConflictMetric.values) {
            metricNames.add(metric.name());
            (metric.isDay() ? metricsDay : metricsTurn).add(metric.ordinal());
        }
        root.put("metric_names", metricNames);
        root.put("metrics_turn", metricsTurn);
        root.put("metrics_day", metricsDay);

        List<Map<String, Object>> coalitions = new ObjectArrayList<>();
        coalitions.add(coalition1.toGraphMap(manager));
        coalitions.add(coalition2.toGraphMap(manager));

        root.put("coalitions", coalitions);

        Map mapCopy = (Map) PSON.decode(JteUtil.toBinary(root));
        System.out.println(mapCopy);

        return bsonCompressed = JteUtil.compress(JteUtil.toBinary(root));
    }

    public synchronized byte[] getPsonGzip(ConflictManager manager) {
        if (dirtyWars) {
            // TODO
        }
        if (dirtyJson || bsonCompressed == null) {
            try {
                Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
                root.put("name", getName());
                List<Object> coalitions = new ObjectArrayList<>();
                coalitions.add(coalition1.toMap(manager));
                coalitions.add(coalition2.toMap(manager));
                root.put("coalitions", coalitions);

                Map<ConflictColumn, Function<OffDefStatGroup, Object>> offDefHeader = OffDefStatGroup.createHeader();
                root.put("counts_header", new ObjectArrayList<>(offDefHeader.keySet().stream().map(ConflictColumn::getName).toList()));
                Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
                root.put("damage_header", new ObjectArrayList<>(damageHeader.keySet().stream().map(ConflictColumn::getName).toList()));

                Gson gson = new Gson();
                JsonObject jsonObject = gson.toJsonTree(root).getAsJsonObject();
                byte[] b64Legacy = JteUtil.compress(JteUtil.toB64(jsonObject));
                byte[] compressed = JteUtil.compress(JteUtil.toBinary(root));

                System.out.println("Length: " + b64Legacy.length + " " + compressed.length);

                bsonCompressed = compressed;
                return compressed;
            } finally {
                dirtyJson = false;
            }
        }
        return bsonCompressed;
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
        warsVsAlliance.computeIfAbsent(current.getAttacker_aa(), k -> new Int2ObjectOpenHashMap<>()).merge(current.getDefender_aa(), 1, Integer::sum);
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
        getManager().updateConflictName(id, name);
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

    public CoalitionSide getSide(boolean isPrimary) {
        return isPrimary ? coalition1 : coalition2;
    }

    public Boolean isSide(int allianceId) {
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