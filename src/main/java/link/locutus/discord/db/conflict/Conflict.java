package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.CachedSupplier;
import link.locutus.discord.web.jooby.CloudStorage;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.db.conflict.ConflictField.*;

public class Conflict {
    private String name;
    private final int id;
    private final int ordinal;
    private long turnStart;
    private long turnEnd;

    private final CachedSupplier<ConflictMeta> metaSupplier;

    private long pushedIndex;
    private long pushedPage;
    private long pushedGraph;
    private volatile boolean recalcGraph;
    private long latestWarOrAttack;

    public Conflict(int id, int ordinal, String name, long turnStart, long turnEnd, long pushedIndex, long pushedPage, long pushedGraph, boolean recalcGraph) {
        this.id = id;
        this.ordinal = ordinal;
        this.name = name;
        this.turnStart = turnStart;
        this.turnEnd = turnEnd;

        this.pushedIndex = pushedIndex;
        this.pushedPage = pushedPage;
        this.pushedGraph = pushedGraph;
        this.recalcGraph = recalcGraph;

        this.metaSupplier = CachedSupplier.of(() -> ConflictMeta.create(this));
    }

    public long getPushedIndex() {
        return pushedIndex;
    }

    public long getPushedPage() {
        return pushedPage;
    }

    public long getPushedGraph() {
        return pushedGraph;
    }

    public void setPushedIndex(long pushedIndex) {
        this.pushedIndex = pushedIndex;
    }

    public void setPushedPage(long pushedPage) {
        this.pushedPage = pushedPage;
    }

    public void setPushedGraph(long pushedGraph) {
        this.pushedGraph = pushedGraph;
    }

    public long getLatestWarAttack() {
        return latestWarOrAttack;
    }

    public long getLatestGraphMs() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        if (tmp != null) {
            CoalitionSides sides = tmp.getSidesOrNull();
            if (sides != null) {
                return TimeUtil.getTimeFromTurn(sides.getLatestGraphTurn());
            }
        }
        return 0L;
    }

    public ConflictMeta getData() {
        return metaSupplier.get();
    }

    public ConflictMeta setLoaded(boolean initSides, boolean initWars) {
        if (initWars && !initSides) throw new IllegalArgumentException("Cannot init wars without sides");
        ConflictMeta meta = this.metaSupplier.setValueIfAbsent(() -> new ConflictMeta(this));
        if (initSides) {
            meta.setLoaded(this, initWars);
        }
        return meta;
    }

    public ConflictMeta setLoaded(ResultSet rs) {
        return this.metaSupplier.setValueIfAbsentElseApply(() -> {
            try {
                return ConflictMeta.create(this, rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }, f -> {
            try {
                f.init( rs);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public synchronized void setLoaded(String col1, String col2, long creator, ConflictCategory category, String wiki, String cb, String status) {
        ConflictMeta tmp = metaSupplier.setValueIfAbsent(() -> new ConflictMeta(this));
        if (creator != 0) tmp.createdByServer = creator;
        if (category != null) tmp.category = category;
        if (wiki != null) tmp.wiki = wiki;
        if (cb != null) tmp.casusBelli = cb;
        if (status != null) tmp.statusDesc = status;
        if (col1 != null && !col1.isEmpty()) tmp.setNameRaw(col1, true);
        if (col2 != null && !col2.isEmpty()) tmp.setNameRaw(col2, false);
    }

    public CoalitionSide getSide(boolean isPrimary) {
        return isPrimary ? getSide1() : getSide2();
    }

    public CoalitionSide getSide1() {
        return this.metaSupplier.get().getCoalition1();
    }

    public CoalitionSide getSide2() {
        return this.metaSupplier.get().getCoalition2();
    }

    public CoalitionSide getSide1OrNull() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        return tmp != null ? tmp.getCoalition1() : null;
    }

    public CoalitionSide getSide2OrNull() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        return tmp != null ? tmp.getCoalition2() : null;
    }

    public boolean isWarsLoaded() {
        ConflictMeta tmp = this.metaSupplier.getOrNull();
        return tmp != null && tmp.isWarsLoaded();
    }

    public void clearWarData() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        if (tmp != null) {
            synchronized (tmp) {
                tmp.clearWarData();
            }
        }
    }

    public synchronized void tryUnload() {
        if (isActive()) return;
        if (metaSupplier.unload()) {
            System.err.println("[conflict] Unloading(1) confict: " + getName() + "/" + getId() + "\n" +
                    StringMan.stacktraceToString(new Exception().getStackTrace()));
        }
    }

    private <T> T tryGet(ConflictField field, Function<ConflictMeta, T> onData) {
        ConflictMeta tmp = metaSupplier.getOrNull();
        if (tmp != null) {
            return onData.apply(tmp);
        }
        if (id > 0 && field != null) {
            return (T) ConflictManager.get().getConflictField(id, field);
        }
        return null;
    }

    public String getCoalitionName(boolean side) {
        return tryGet(side ? COL1 : COL2, f -> side ? f.col1 : f.col2);
    }

    public void setCasusBelli(String casusBelli) {
        trySet(f -> f.casusBelli = casusBelli);
        if (id > 0) {
            ConflictManager.get().setCb(id, casusBelli);
        }
    }

    private void trySet(Consumer<ConflictMeta> onData) {
        checkSet(f -> {
            onData.accept(f);
            return true;
        });
    }

    private Boolean checkSet(Predicate<ConflictMeta> onData) {
        ConflictMeta tmp = metaSupplier.getOrNull();
        if (tmp != null) {
            return onData.test(tmp);
        }
        return null;
    }

    public void setStatus(String status) {
        if (checkSet(tmp -> {
            if (!tmp.statusDesc.equals(status)) {
                tmp.statusDesc = status;
                return true;
            }
            return false;
        }) != Boolean.FALSE){
            if (id > 0) ConflictManager.get().setStatus(id, status);
        }
    }

    @Command(desc = "The conflict category")
    public ConflictCategory getCategory() {
        Object obj = tryGet(CATEGORY, f -> f.category);
        if (obj instanceof Number n) {
            return ConflictCategory.values[n.intValue()];
        }
        return (ConflictCategory) obj;
    }

    public void setCategory(ConflictCategory category) {
        if (checkSet(tmp -> {
            if (tmp.category != category) {
                tmp.category = category;
                return true;
            }
            return false;
        }) != Boolean.FALSE) {
            if (id > 0) ConflictManager.get().updateConflictCategory(id, category);
        }
    }

    public void setWiki(String wiki) {
        trySet(tmp -> tmp.wiki = wiki);
        if (id > 0) ConflictManager.get().updateConflictWiki(id, wiki);
    }

    public void setName(String name, boolean isPrimary) {
        trySet(tmp -> {
            if (isPrimary) {
                tmp.setNameRaw(name, true);
            } else {
                tmp.setNameRaw(name, false);
            }
        });
        if (id > 0) ConflictManager.get().updateConflictName(id, name, isPrimary);
    }

    public void updateGraphsLegacy(ConflictManager manager) throws IOException, ParseException {
        CoalitionSide coalition1 = getSide1();
        CoalitionSide coalition2 = getSide2();
        WarStatistics col1Wars = coalition1.get();
        WarStatistics col2Wars = coalition2.get();

        Set<Integer> nationIds = new IntOpenHashSet();
        nationIds.addAll(col1Wars.getNationIds());
        nationIds.addAll(col2Wars.getNationIds());

        long startMs = TimeUtil.getTimeFromTurn(turnStart);
        long endMs = turnEnd == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(turnEnd);

        Map<Long, Map<Integer, Map<MilitaryUnit, Integer>>> milHistory = Locutus.imp().getNationDB().getMilitaryHistoryByTurn(nationIds, startMs, endMs);
        Map<Long, Map<Integer, DBNation>> nationsByDay = new HashMap<>();

        DataDumpParser parser = Locutus.imp().getDataDumper(true).load();

        long dayStart = TimeUtil.getDayFromTurn(getStartTurn());
        long dayEnd = getEndTurn() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getDayFromTurn(getEndTurn() + 11);

        for (Long day : parser.getDays(true, false)) {
            if (day < dayStart || day > dayEnd) continue;
            Map<Integer, DBNationSnapshot> nations = parser.getNations(day);
            for (Map.Entry<Integer, DBNationSnapshot> entry : nations.entrySet()) {
                int nationId = entry.getKey();
                if (!nationIds.contains(nationId)) {
                    Rank position = entry.getValue().getPositionEnum();
                    if (position.id <= Rank.APPLICANT.id) continue;
                    int allianceId = entry.getValue().getAlliance_id();
                    if (!coalition1.hasAlliance(allianceId) && !coalition2.hasAlliance(allianceId)) continue;
                }
                nationsByDay.computeIfAbsent(day, k -> new Int2ObjectOpenHashMap<>()).put(nationId, entry.getValue());
            }
        }

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
            long nextDay = TimeUtil.getDayFromTurn(TimeUtil.getTurn() + 11);
            nationsByDay.put(nextDay, latest);
        } else {
            latest = nationsByDay.get(lastDay);
        }

        // save day data
        {
            for (Map.Entry<Long, Map<Integer, DBNation>> entry : nationsByDay.entrySet()) {
                long day = entry.getKey();
                long dayStartTurn = TimeUtil.getTurn(TimeUtil.getTimeFromDay(day));
                long dayEndTurn = dayStartTurn + 11;
                Map<Integer, DBNation> nations = entry.getValue();
                Set<DBNation> col1Nations = new ObjectOpenHashSet<>();
                Set<DBNation> col2Nations = new ObjectOpenHashSet<>();
                for (Map.Entry<Integer, DBNation> nationEntry : nations.entrySet()) {
                    DBNation nation = nationEntry.getValue();
                    if (nation.getVm_turns() > 0 || nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
                    int aaId = nation.getAlliance_id();
                    long startTurn = getStartTurn(aaId);
                    if (startTurn != turnStart && startTurn > dayEndTurn) continue;
                    long endTurn = getEndTurn(aaId);
                    if (endTurn != turnEnd && endTurn < dayStartTurn) continue;
                    if (coalition1.hasAlliance(aaId)) {
                        col1Nations.add(nation);
                    } else if (coalition2.hasAlliance(aaId)) {
                        col2Nations.add(nation);
                    }
                }
                col1Wars.updateDayTierGraph(manager, day, col1Nations, true, false);
                col2Wars.updateDayTierGraph(manager, day, col2Nations, true, false);
            }
        }

        {
            MilitaryUnit[] units = {MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP};

            long latestTurn = Math.min(getEndTurn(), currentTurn);
            long latestDay = TimeUtil.getDayFromTurn(latestTurn);
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

                Map<Integer, Map<Byte, Map<MilitaryUnit, Integer>>> unitsByCityCol1 = new Int2ObjectOpenHashMap<>();
                Map<Integer, Map<Byte, Map<MilitaryUnit, Integer>>> unitsByCityCol2 = new Int2ObjectOpenHashMap<>();

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
                    int aaId = nation.getAlliance_id();
                    boolean isPrimary = coalition1.hasAlliance(aaId);
                    Map<Byte, Map<MilitaryUnit, Integer>> unitsByCity = (isPrimary ? unitsByCityCol1 : unitsByCityCol2)
                            .computeIfAbsent(aaId, f -> new Byte2ObjectOpenHashMap<>());
                    byte cities = (byte) nation.getCities();
                    Map<MilitaryUnit, Integer> historicalCounts = milCountByNation.get(id);

                    Map<MilitaryUnit, Integer> cityMap = unitsByCity.computeIfAbsent(cities, k -> new EnumMap<>(MilitaryUnit.class));
                    if (historicalCounts == null) {
                        for (MilitaryUnit unit : units) {
                            int count = nation.getUnits(unit);
                            if (count > 0) cityMap.merge(unit, count, Integer::sum);
                        }
                    } else {
                        for (MilitaryUnit unit : units) {
                            Integer hist = historicalCounts.get(unit);
                            int count = (hist != null) ? hist : nation.getUnits(unit);
                            if (count > 0) cityMap.merge(unit, count, Integer::sum);
                        }
                    }
                }

                col1Wars.updateTurnTierGraph(manager, turn, unitsByCityCol1, true, false);
                col2Wars.updateTurnTierGraph(manager, turn, unitsByCityCol2, true, false);
            }
        }

        manager.deleteGraphData(getId());
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        entries.addAll(col1Wars.getGraphEntries());
        entries.addAll(col2Wars.getGraphEntries());
        manager.addGraphData(entries);
    }

    private Conflict checkRecalcGraph() {
        if (recalcGraph) {
            synchronized (this) {
                if (recalcGraph) {
                    recalcGraph = false;
                    try {
                        updateGraphsLegacy(ConflictManager.get());
                    } catch (IOException | ParseException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return this;
    }

    Map<String, Object> warsVsAllianceJson(CoalitionSide coalition1, CoalitionSide coalition2, Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance) {
        Map<ConflictColumn, Function<DamageStatGroup, Object>> combined = DamageStatGroup.createRanking();

        List<Map.Entry<ConflictColumn, Function<DamageStatGroup, Object>>> combinedList = new ObjectArrayList<>(combined.entrySet());

        Map<String, Object> result = new Object2ObjectLinkedOpenHashMap<>();
        // headers
        result.put("headers", combinedList.stream().map(e -> e.getKey().getName()).toList());
        result.put("header_desc", combinedList.stream().map(e -> e.getKey().getDescription()).toList());
        // alliance ids
        List<Integer> aaIdsFull = new IntArrayList();
        aaIdsFull.addAll(coalition1.getAllianceIdsSorted());
        aaIdsFull.addAll(coalition2.getAllianceIdsSorted());

        List<List<List<Long>>> tables = new ArrayList<>();
        for (Map.Entry<ConflictColumn, Function<DamageStatGroup, Object>> entry : combinedList) {
            List<List<Long>> table = new ArrayList<>();
            Function<DamageStatGroup, Object> function = entry.getValue();

            for (int aaId1 : aaIdsFull) {
                List<Long> row = new LongArrayList();
                Map<Integer, DamageStatGroup> statsByAA = warsVsAlliance.get(aaId1);
                if (statsByAA != null) {
                    for (int aaId2 : aaIdsFull) {
                        DamageStatGroup stats = statsByAA.get(aaId2);
                        if (stats == null) {
                            row.add(0L);
                        } else {
                            Object value = function.apply(stats);
                            row.add(((Number) value).longValue());
                        }
                    }
                }
                table.add(row);
            }
            tables.add(table);
        }
        result.put("data", tables);
        return result;
    }

    @Command(desc = "The status of the conflict")
    public String getStatusDesc() {
        return tryGet(STATUS, f -> f.statusDesc);
    }

    @Command(desc = "The casus belli of the conflict")
    public String getCasusBelli() {
        return tryGet(CB, f -> f.casusBelli);
    }

    public long getStartTurn(int allianceId) {
        return metaSupplier.get().getStartTimeForAlliance(allianceId, turnStart);
    }
    public long getEndTurn(int allianceId) {
        return metaSupplier.get().getEndTimeForAlliance(allianceId, turnEnd);
    }

    private CoalitionSide getCoalition(int aaId1, int aaId2, long turn) {
        CoalitionSide coalition1 = getSide1();
        CoalitionSide coalition2 = getSide2();
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

    private DamageStatGroup getWarWebEntry(int aaId1, int aaId2) {
        return getData().getSides().getDataWithWars().computeIfAbsent(aaId1, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(aaId2, k -> new DamageStatGroup());
    }

    public boolean updateWar(DBWar previous, DBWar current, long turn, long date) {
        CoalitionSide side = getCoalition(current.getAttacker_aa(), current.getDefender_aa(), turn);
        if (side == null) return false;
        latestWarOrAttack = date;

        CoalitionSide otherSide = side.getOther();
        side.updateWar(previous, current, true);
        otherSide.updateWar(previous, current, false);

        if (previous == null) {
            getWarWebEntry(current.getAttacker_aa(), current.getDefender_aa()).newWar(current, true);
        }

        return true;
    }

    public void updateAttack(DBWar war, AbstractCursor attack, long turn, Function<IAttack, AttackTypeSubCategory> getCached) {
        int attackerAA, defenderAA, attCities, defCities;
        if (attack.getAttacker_id() == war.getAttacker_id()) {
            attackerAA = war.getAttacker_aa();
            defenderAA = war.getDefender_aa();
            attCities = war.getAttCities();
            defCities = war.getDefCities();
        } else {
            attackerAA = war.getDefender_aa();
            defenderAA = war.getAttacker_aa();
            attCities = war.getDefCities();
            defCities = war.getAttCities();
        }
        CoalitionSide side = getCoalition(attackerAA, defenderAA, turn);
        if (side == null) return;

        long date = attack.getDate();
        latestWarOrAttack = date;
        long day = TimeUtil.getDay(date);

        CoalitionSide otherSide = side.getOther();
        AttackTypeSubCategory subCategory = getCached.apply(attack);

        side.updateAttack(war, attack, attackerAA, attack.getAttacker_id(), attCities, day, true, subCategory);
        otherSide.updateAttack(war, attack, defenderAA, attack.getDefender_id(), defCities, day, false, subCategory);

        getWarWebEntry(attackerAA, defenderAA).newAttack(war, attack, null);
        getWarWebEntry(attackerAA, defenderAA).apply(attack, war, true);
        getWarWebEntry(defenderAA, attackerAA).apply(attack, war, false);


    }

    public Conflict setName(String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            if (id > 0) ConflictManager.get().updateConflictName(id, name);
        }
        return this;
    }

    public Conflict setStart(long time) {
        long newTurn = TimeUtil.getTurn(time);
        if (newTurn == turnStart) return this;
        if (id > 0) { ConflictManager.get().updateConflict(this, turnStart, turnEnd);
            if (newTurn < turnStart) {
                markGraphsInvalid();
            } else {
                deleteGraphDataOutside(newTurn, turnEnd);
            }
        }
        this.turnStart = newTurn;
        return this;
    }

    public Conflict setEnd(long time) {
        long newTurn = time == Long.MAX_VALUE ? time : TimeUtil.getTurn(time) + 1;
        if (newTurn == turnEnd) return this;
        if (id > 0) {
            ConflictManager.get().updateConflict(this, turnStart, turnEnd);
            if (newTurn > this.turnEnd) {
                markGraphsInvalid();
            } else if (newTurn < TimeUtil.getTurn()) {
                deleteGraphDataOutside(turnStart, newTurn);
            }
        }
        this.turnEnd = newTurn;
        return this;
    }

    public Conflict addParticipant(int allianceId, boolean side, Long start, Long end) {
        return addParticipant(allianceId, side, true, false, start, end);
    }

    public Conflict addParticipant(int allianceId, boolean side, boolean save, boolean init, Long start, Long end) {
        AtomicBoolean changedTime = new AtomicBoolean(false);
        AtomicBoolean didNotExistBefore = new AtomicBoolean(false);

        trySet(f -> {
            Map<Integer, Long> startMap = f.getStartTimeRaw();
            Map<Integer, Long> endMap = f.getEndTimeRaw();

            Function<Map<Integer, Long>, Long> prevValue = map -> {
                if (map == null) return null;
                synchronized (map) {
                    return map.get(allianceId);
                }
            };

            Long prevStart = prevValue.apply(startMap);
            Long prevEnd   = prevValue.apply(endMap);

            BiConsumer<Map<Integer, Long>, Long> store = (map, value) -> {
                if (map == null) return;
                synchronized (map) {
                    if (value == null || value <= 0 || value == Long.MAX_VALUE) {
                        map.remove(allianceId);
                    } else {
                        map.put(allianceId, value);
                    }
                }
            };

            store.accept(startMap, start);
            store.accept(endMap, end);

            CoalitionSide coal = side ? f.getCoalition1() : f.getCoalition2();
            boolean coalInitializedBefore = coal != null;
            boolean hadAllianceBefore = coalInitializedBefore && coal.hasAlliance(allianceId);
            if (coal != null) {
                coal.add(allianceId);
            }
            boolean hasAllianceAfter = coal != null && coal.hasAlliance(allianceId);

            if (!Objects.equals(prevStart, start) || !Objects.equals(prevEnd, end)) {
                changedTime.set(true);
            }

            if (coalInitializedBefore && !hadAllianceBefore && hasAllianceAfter) {
                didNotExistBefore.set(true);
            }
        });

        if (save) {
            long startFinal = start == null ? 0L : start;
            long endFinal = end == null ? java.lang.Long.MAX_VALUE : end;
            if (id > 0) {
                ConflictManager.get().addParticipant(this, allianceId, side, startFinal, endFinal);
                if (changedTime.get()) {
                    clearWarData();
                } else if (didNotExistBefore.get()) {
                    ConflictManager.get().loadConflictWars(this, allianceId, start, end);
                }
                markGraphsInvalid();
            }
        }

        return this;
    }

    public Conflict removeParticipant(int allianceId) {
        Boolean changed = checkSet(tmp -> {
            tmp.removeTimeForAlliance(allianceId);

            CoalitionSides sides = tmp.getSidesOrNull();
            if (sides == null) return false;
            boolean flag = false;
            flag |= sides.col1.remove(allianceId);
            flag |= sides.col2.remove(allianceId);
            if (flag) {
                tmp.clearWarData();
            }
            return flag;
        });
        if (id > 0) {
            if (changed != Boolean.FALSE) ConflictManager.get().removeParticipant(this, allianceId);
            markGraphsInvalid();
        }
        return this;
    }

    @Command(desc = "The id of the conflict in the database and website url")
    public int getId() {
        return id;
    }

    @Command(desc = "The url of this conflict (if pushed live)")
    public String getUrl() {
        return Settings.INSTANCE.WEB.CONFLICTS.SITE + "/conflict?id=" + id;
    }

    @Command(desc = "The ordinal of the conflict (load order)")
    public int getOrdinal() {
        return ordinal;
    }

    @Command(desc = "The name of the conflict")
    public String getName() {
        return name;
    }

    @Command(desc = "The turn the conflict started\n" +
            "Measured in 2h turns from unix epoch")
    public long getStartTurn() {
        return turnStart;
    }

    @Command(desc = """
            The turn the conflict ends
            Measured in 2h turns from unix epoch
            If the conflict is ongoing, this will be Long.MAX_VALUE""")
    public long getEndTurn() {
        return turnEnd;
    }

    public Set<Integer> getCoalition1() {
        return getSide1().getAllianceIds();
    }

    public Set<Integer> getCoalition2() {
        return getSide2().getAllianceIds();
    }

    @Command(desc = "If an alliance is a participant in the conflict")
    public boolean isParticipant(DBAlliance alliance) {
        return getSide1().hasAlliance(alliance.getId()) || getSide2().hasAlliance(alliance.getId());
    }

    @Command(desc = "A number representing the side an alliance is on in the conflict\n" +
            "0 = No side, 1 = Primary/Coalition 1, 2 = Secondary/Coalition 2")
    public int getSide(DBAlliance alliance) {
        if (getSide1().hasAlliance(alliance.getId())) return 1;
        if (getSide2().hasAlliance(alliance.getId())) return 2;
        return 0;
    }

    @Command(desc = "The start turn of the conflict in milliseconds since unix epoch")
    public long getStartMS() {
        return TimeUtil.getTimeFromTurn(turnStart);
    }

    @Command(desc = "The end turn of the conflict in milliseconds since unix epoch")
    public long getEndMS() {
        return turnEnd == Long.MAX_VALUE ? java.lang.Long.MAX_VALUE : TimeUtil.getTimeFromTurn(turnEnd);
    }

    @Command(desc = "The alliance list of for the first coalition")
    public AllianceList getCol1List() {
        return new AllianceList(getCoalition1());
    }

    @Command(desc = "The alliance list of for the second coalition")
    public AllianceList getCol2List() {
        return new AllianceList(getCoalition2());
    }

    @Command(desc = "The alliance list for ALL participants")
    public AllianceList getAllianceList() {
        return new AllianceList(getAllianceIds());
    }

    public Set<DBAlliance> getCoalition1Obj() {
        return getSide1().getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getCoalition2Obj() {
        return getSide2().getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Boolean isSide(int allianceId) {
        if (getSide1().hasAlliance(allianceId)) return true;
        if (getSide2().hasAlliance(allianceId)) return false;
        return null;
    }

    public Set<Integer> getAllianceIds() {
        CoalitionSide coalition1 = getSide1();
        CoalitionSide coalition2 = getSide2();
        Set<Integer> ids = new IntOpenHashSet(coalition1.getAllianceIds().size() + coalition2.getAllianceIds().size());
        ids.addAll(coalition1.getAllianceIds());
        ids.addAll(coalition2.getAllianceIds());
        return ids;
    }

    @Command(desc = "The number of active wars in the conflict")
    public int getActiveWars() {
        if (!isActive()) return 0;
        CoalitionSide coalition1 = getSide1();
        CoalitionSide coalition2 = getSide2();
        return coalition1.getOffensiveStats(null, false).activeWars + coalition2.getOffensiveStats(null, false).activeWars;
    }

    @Command(desc = "The number of total wars in the conflict")
    public int getTotalWars() {
        CoalitionSide coalition1 = getSide1();
        CoalitionSide coalition2 = getSide2();
        return coalition1.getOffensiveStats(null, false).totalWars + coalition2.getOffensiveStats(null, false).totalWars;
    }

    @Command(desc = "The current damage dealt between the conflict's participants, using market prices")
    public double getDamageConverted(boolean isPrimary) {
        return getSide(isPrimary).getInflicted().getTotalConverted();
    }

    public void addAnnouncement(String desc, DBTopic topic, boolean saveToDB, boolean init) {
        trySet(tmp -> {
            tmp.addAnnouncement(desc, topic, init);
        });
        if (saveToDB) {
            if (id > 0) ConflictManager.get().addAnnouncement(id, topic.topic_id, desc);
        }
    }

    public Map<String, List> getAnnouncementsList() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        Map<String, DBTopic> announcements;
        if (tmp == null) {
            announcements = Locutus.imp().getWarDb().getConflicts().loadAnnouncements(id);
        } else {
            announcements = tmp.getAnnouncement();
        }
        synchronized (announcements) {
            Map<String, List> map = new Object2ObjectLinkedOpenHashMap<>();
            for (Map.Entry<String, DBTopic> entry : announcements.entrySet()) {
                DBTopic topic = entry.getValue();
                map.put(entry.getKey(), List.of(topic.topic_id, topic.topic_urlname, topic.timestamp));
            }
            return map;
        }
    }

    @Command(desc = "The wiki link for this conflict (or null)")
    public String getWiki() {
        return tryGet(WIKI, f -> f.wiki);
    }

    @Command(desc = "The discord guild that created this conflict (or null)")
    public GuildDB getGuild() {
        Long serverId = tryGet(CREATOR, f -> f.createdByServer);
        return serverId == null || serverId <= 0 ? null : Locutus.imp().getGuildDB(serverId);
    }

    @Command(desc = "The id of the discord guild that created this conflict")
    public long getGuildId() {
        Long serverId = tryGet(CREATOR, f -> f.createdByServer);
        return serverId == null ? -1 : serverId;
    }

    public boolean isActive() {
        if (this.turnEnd == Long.MAX_VALUE) return true;
        long turn = TimeUtil.getTurn();
        return turn <= this.turnEnd + 60;
    }

    /**
     * Pushes the conflict to the AWS S3 bucket
     * @param manager
     * @param webIdOrNull
     * @param includeGraphs
     * @return List of URLs
     */
    public List<String> pushChanges(ConflictManager manager, String webIdOrNull, boolean updatePageMeta, boolean updatePageStats, boolean updateGraphMeta, boolean updateGraphStats, boolean updateIndex, long now) {
        CloudStorage aws = manager.getCloud();
        if (webIdOrNull == null) {
            if (getId() == -1) throw new IllegalArgumentException("Conflict has no id");
            webIdOrNull = Integer.toString(getId());
        }
        long ttl = isActive() ? TimeUnit.MINUTES.toSeconds(1) : TimeUnit.MINUTES.toSeconds(5);
        List<String> urls = new ArrayList<>();

        if (updatePageMeta || updatePageStats) {
            String key = "conflicts/" + webIdOrNull + ".gzip";
            byte[] value = HeaderGroup.getBytesZip(manager, this, Map.of(HeaderGroup.PAGE_META, updatePageMeta, HeaderGroup.PAGE_STATS, updatePageStats), now);
            aws.putObject(key, value, ttl);
            urls.add(aws.getLink(key));
        }

        if (updateGraphMeta || updateGraphStats) {
            checkRecalcGraph();
            String graphKey = "conflicts/graphs/" + webIdOrNull + ".gzip";
            byte[] value = HeaderGroup.getBytesZip(manager, this, Map.of(HeaderGroup.GRAPH_META, updateGraphMeta, HeaderGroup.GRAPH_DATA, updateGraphStats), now);
            aws.putObject(graphKey, value, ttl);
            urls.add(aws.getLink(graphKey));
        }
        if (updateIndex && id > 0) {
            manager.pushIndex(now);
        }
        return urls;
    }

    public void set(Conflict wikiConflict, boolean setName) {
        if (!wikiConflict.getStatusDesc().equalsIgnoreCase(getStatusDesc())) {
            setStatus(wikiConflict.getStatusDesc());
        }
        if (!wikiConflict.getCasusBelli().equalsIgnoreCase(getCasusBelli())) {
            setCasusBelli(wikiConflict.getCasusBelli());
        }
        if (setName && !wikiConflict.getName().equalsIgnoreCase(getName())) {
            setName(wikiConflict.getName());
        }
        ConflictMeta tmp = wikiConflict.metaSupplier.getOrNull();
        if (tmp != null) {
            Map<Integer, String> annByIds = tmp.getAnnouncementsById();
            for (Map.Entry<String, DBTopic> entry : tmp.getAnnouncement().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(annByIds.get(entry.getValue().topic_id))) continue;
                addAnnouncement(entry.getKey(), entry.getValue(), true, false);
            }
        }
        if (getCategory() != wikiConflict.getCategory()) {
            setCategory(wikiConflict.getCategory());
        }
        if (getAllianceIds().isEmpty()) {
            for (int aaId : wikiConflict.getCoalition1()) addParticipant(aaId, true, null, null);
            for (int aaId : wikiConflict.getCoalition2()) addParticipant(aaId, false, null, null);
        }
    }

    public void deleteAnnouncement(int topicId) {
        if (id > 0) ConflictManager.get().removeAnnouncement(id, topicId);
        trySet(tmp -> {
            tmp.removeAnnouncementRaw(topicId);
        });
    }

    private void markGraphsInvalid() {
        this.recalcGraph = true;
    }

    private void deleteGraphDataOutside(long turnStart, long turnEnd) {
        if (turnEnd == -1) turnEnd = Long.MAX_VALUE;
        long finalTurnEnd = turnEnd;
        trySet(f -> {
            CoalitionSides tmp = f.getSidesOrNull();
            if (tmp != null) {
                WarStatistics ws1 = tmp.col1.getWarDataOrNull();
                if (ws1 != null) ws1.clearGraphDataOutside(turnStart, finalTurnEnd);
                WarStatistics ws2 = tmp.col2.getWarDataOrNull();
                if (ws2 != null) ws2.clearGraphDataOutside(turnStart, finalTurnEnd);
            }
        });
    }

    public boolean hasMetaLoaded() {
        return metaSupplier.hasValue();
    }

    public boolean hasParticipantsLoaded() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        return tmp != null && tmp.hasParticipantsLoaded();
    }

    public boolean hasAnnouncementsLoaded() {
        ConflictMeta tmp = metaSupplier.getOrNull();
        return tmp != null && tmp.hasAnnouncementsLoaded();
    }
}