package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.unimi.dsi.fastutil.bytes.Byte2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.db.entities.conflict.ConflictColumn;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.web.jooby.ICloudStorage;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.db.conflict.ConflictField.*;

public class Conflict {
    private String name;
    private final int id;
    private final int ordinal;
    private long turnStart;
    private long turnEnd;

    private volatile ConflictMeta data;

    private long pushedIndex;
    private long pushedPage;
    private long pushedGraph;
    private boolean recalcGraph;
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
        ConflictMeta tmp = data;
        if (tmp != null) {
            CoalitionSide coal1 = tmp.coalition_1;
            if (coal1 != null) {
                return Math.max(coal1.getLatestGraphMs(), tmp.coalition_2.getLatestGraphMs());
            }
        }
        return 0L;
    }

    public ConflictMeta getData(boolean create) {
        ConflictMeta tmp = data;
        if (tmp != null) return tmp;
        if (create) {
            synchronized (this) {
                tmp = data = ConflictMeta.create(this);
            }
        }
        return tmp;
    }

    public ConflictMeta getDataWithWars() {
        ConflictMeta tmp = getData(true);
        CoalitionSide coal1 = tmp.coalition_1;
        if (id > 0 && !coal1.isLoaded()) {
            getManager().ensureLoadedFully(Set.of(this));
            tmp = getData(false);
        }
        return tmp;
    }

    public synchronized void trySetData(String col1, String col2, long creator, ConflictCategory category, String wiki, String cb, String status) {
        ConflictMeta tmp = data;
        if (tmp == null) return;
        if (creator != 0) tmp.createdByServer = creator;
        if (category != null) tmp.category = category;
        if (wiki != null) tmp.wiki = wiki;
        if (cb != null) tmp.casusBelli = cb;
        if (status != null) tmp.statusDesc = status;
        if (col1 != null && !col1.isEmpty()) tmp.col1 = col1;
        if (col2 != null && !col2.isEmpty()) tmp.col2 = col2;

    }

    public ConflictMeta initData(ResultSet rs) throws SQLException {
        ConflictMeta tmp = data;
        if (tmp == null) {
            synchronized (this) {
                if (data == null) {
                    tmp = data = ConflictMeta.create(id, rs);
                } else {
                    tmp = data;
                    tmp.init(rs);
                }
            }
        } else {
            tmp.init(rs);
        }
        return tmp;
    }

    public CoalitionSide getCoalition(boolean side, boolean create, boolean loadWars) {
        if (!create && loadWars) throw new IllegalArgumentException("Cannot load wars if not creating data");
        ConflictMeta tmp = getData(create);
        if (tmp != null) {
            CoalitionSide coal = tmp.getCoalition(this, side);
            if (id > 0 && !coal.isLoaded() && loadWars) {
                getManager().ensureLoadedFully(Set.of(this));
                tmp = getData(false);
                if (tmp != null) {
                    coal = tmp.getCoalition(this, side);
                }
            }
            return coal;
        }
        return null;
    }

    public boolean isLoaded() {
        ConflictMeta tmp = data;
        if (tmp == null) return false;
        CoalitionSide coal1 = tmp.coalition_1;
        if (coal1 == null) return false;
        return coal1.isLoaded();
    }

    public static final class ConflictMeta {
        public final int id;

        private ConflictCategory category;
        private String wiki = "";

        private String col1, col2;

        private volatile CoalitionSide coalition_1;
        private volatile CoalitionSide coalition_2;

        private volatile Map<Integer, Long> startTime2 = null;
        private volatile Map<Integer, Long> endTime2 = null;

        private Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance2 = null;
        private volatile Map<String, DBTopic> announcements2 = null;

        private String casusBelli = "";
        private String statusDesc = "";
        private long createdByServer;

        private ConflictMeta(int id) {
            this.id = id;
        }

        public static ConflictMeta create(int id, ResultSet rs) throws SQLException {
            ConflictMeta cd = new ConflictMeta(id);
            cd.init(rs);
            return cd;
        }

        public Map<Integer, Long> getStartTime() {
            if (id <= 0) return new Int2LongOpenHashMap();
            initStartEndTimes();
            synchronized (startTime2) {
                return new Int2LongOpenHashMap(startTime2);
            }
        }

        public Map<Integer, Long> getEndTime() {
            if (id <= 0) return new Int2LongOpenHashMap();
            initStartEndTimes();
            synchronized (endTime2) {
                return new Int2LongOpenHashMap(endTime2);
            }
        }

        private void initStartEndTimes() {
            if (startTime2 == null) {
                synchronized (this) {
                    if (startTime2 == null) {
                        startTime2 = new Int2LongOpenHashMap();
                        endTime2 = new Int2LongOpenHashMap();
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
        }

        public static ConflictMeta create(Conflict conflict) {
            if (conflict.getId() <= 0) {
                return new ConflictMeta(conflict.getId());
            }
            return conflict.getManager().getDb().select(
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
                    (ThrowingFunction<ResultSet, ConflictMeta>) rs -> create(conflict.getId(), rs)
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

        private void initCoalitions(Conflict conflict) {
            if (coalition_1 == null) {
                synchronized (this) {
                    if (coalition_1 == null) {
                        coalition_1 = new CoalitionSide(conflict, col1, true, false);
                        coalition_2 = new CoalitionSide(conflict, col2, false, false);
                        coalition_1.setOther(coalition_2);
                        coalition_2.setOther(coalition_1);
                        warsVsAlliance2 = new Int2ObjectOpenHashMap<>();
                    }
                }
            }

        }

        public CoalitionSide getCoalition(Conflict conflict, boolean side) {
            initCoalitions(conflict);
            return side ? coalition_1 : coalition_2;
        }
    }

    public void clearWarData() {
        ConflictMeta tmp = data;
        if (tmp != null) {
            synchronized (tmp) {
                if (tmp.coalition_1 != null) tmp.coalition_1.clearWarData();
                if (tmp.coalition_2 != null) tmp.coalition_2.clearWarData();
                if (tmp.warsVsAlliance2 != null) tmp.warsVsAlliance2 = null;
            }
        }
    }

    public synchronized void tryUnload() {
        if (isActive()) return;
        data = null;
    }

    private <T> T tryGet(ConflictField field, Function<ConflictMeta, T> onData) {
        ConflictMeta tmp = data;
        if (tmp != null) {
            return onData.apply(tmp);
        }
        if (id > 0 && field != null) {
            return (T) getManager().getConflictField(id, field);
        }
        return null;
    }

    public String getCoalitionName(boolean side) {
        return tryGet(side ? COL1 : COL2, f -> side ? f.col1 : f.col2);
    }

    public void setCasusBelli(String casusBelli) {
        ConflictMeta tmp = data;
        if (tmp != null) {
            tmp.casusBelli = casusBelli;
        }
        if (id > 0) {
            getManager().setCb(id, casusBelli);
        }
    }

    private void trySet(Consumer<ConflictMeta> onData) {
        ConflictMeta tmp = data;
        if (tmp != null) {
            onData.accept(tmp);
        }
    }

    public void setStatus(String status) {
        trySet(tmp -> tmp.statusDesc = status);
        if (id > 0) getManager().setStatus(id, status);
    }

    @Command(desc = "The conflict category")
    public ConflictCategory getCategory() {
        return tryGet(CATEGORY, f -> f.category);
    }

    public void setCategory(ConflictCategory category) {
        trySet(tmp -> tmp.category = category);
        if (id > 0) getManager().updateConflictCategory(id, category);
    }

    public void setWiki(String wiki) {
        trySet(tmp -> tmp.wiki = wiki);
        if (id > 0) getManager().updateConflictWiki(id, wiki);
    }

    public void setName(String name, boolean isPrimary) {
        trySet(tmp -> {
            if (isPrimary) {
                tmp.col1 = name;
                if (tmp.coalition_1 != null)
                    tmp.coalition_1.setNameRaw(name);
            } else {
                tmp.col2 = name;
                if (tmp.coalition_2 != null)
                    tmp.coalition_2.setNameRaw(name);
            }
        });
        if (id > 0) getManager().updateConflictName(id, name, isPrimary);
    }

    public void updateGraphsLegacy(ConflictManager manager) throws IOException, ParseException {
        ConflictMeta tmp = getData(true);
        CoalitionSide coalition1 = tmp.getCoalition(this, true);
        CoalitionSide coalition2 = tmp.getCoalition(this, false);

        Set<Integer> nationIds = new IntOpenHashSet();
        nationIds.addAll(coalition1.getNationIds());
        nationIds.addAll(coalition2.getNationIds());

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
                coalition1.updateDayTierGraph(manager, day, col1Nations, true, false);
                coalition2.updateDayTierGraph(manager, day, col2Nations, true, false);
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

                coalition1.updateTurnTierGraph(manager, turn, unitsByCityCol1, true, false);
                coalition2.updateTurnTierGraph(manager, turn, unitsByCityCol2, true, false);
            }
        }

        manager.deleteGraphData(getId());
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        entries.addAll(coalition1.getGraphEntries());
        entries.addAll(coalition2.getGraphEntries());
        manager.addGraphData(entries);
    }

    private synchronized byte[] getGraphBytesZip(ConflictManager manager) throws IOException, ParseException {
        if (recalcGraph) {
            recalcGraph = false;
            if (Locutus.imp().getDataDumper(false) != null) {
                updateGraphsLegacy(getManager());
            }
        }

        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        root.put("name", getName());
        root.put("start", TimeUtil.getTimeFromTurn(turnStart));
        root.put("end", turnEnd == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(turnEnd));

        List<String> metricNames = new ObjectArrayList<>();

        List<Integer> metricsDay = new IntArrayList();
        List<Integer> metricsTurn = new IntArrayList();

        for (ConflictMetric metric : ConflictMetric.values) {
            (metric.isDay() ? metricsDay : metricsTurn).add(metricNames.size());
            metricNames.add(metric.name().toLowerCase(Locale.ROOT));
        }
        Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeaders = DamageStatGroup.createRanking();
        List<ConflictColumn> columns = new ObjectArrayList<>(damageHeaders.keySet());
        List<Function<DamageStatGroup, Object>> valueFuncs = columns.stream().map(damageHeaders::get).toList();

        int columnMetricOffset = metricNames.size();

        for (ConflictColumn column : columns) {
            metricsDay.add(metricNames.size());
            String defPrefix = column.isCount() ? "def:" : "loss:";
            metricNames.add(defPrefix + column.getName());
            metricsDay.add(metricNames.size());
            String attPrefix = column.isCount() ? "off:" : "dealt:";
            metricNames.add(attPrefix + column.getName());
        }
        root.put("metric_names", metricNames);
        root.put("metrics_turn", metricsTurn);
        root.put("metrics_day", metricsDay);

        List<Map<String, Object>> coalitions = new ObjectArrayList<>();
        coalitions.add(coalition1.toGraphMap(manager, metricsTurn, metricsDay, valueFuncs, columnMetricOffset));
        coalitions.add(coalition2.toGraphMap(manager, metricsTurn, metricsDay, valueFuncs, columnMetricOffset));

        root.put("coalitions", coalitions);
        try {
            return JteUtil.compress(JteUtil.getSerializer().writeValueAsBytes(root));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> warsVsAllianceJson(CoalitionSide coalition1, CoalitionSide coalition2, Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance) {
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

    private synchronized byte[] getWarBytesZip(ConflictManager manager, boolean forceUpdateMeta, boolean forceUpdateStats) {
        // get cached war bytes if available
        Set<HeaderGroup> fetchGroups = new ObjectOpenHashSet<>();
        if (!forceUpdateMeta) {
            fetchGroups.add(HeaderGroup.PAGE_META);
        }
        if (!forceUpdateStats) {
            fetchGroups.add(HeaderGroup.PAGE_STATS);
        }
        Map<HeaderGroup, Map.Entry<Long, byte[]>> cachedMap = fetchGroups.isEmpty() ? Collections.emptyMap() : manager.loadConflictRowCache(id, fetchGroups);

        try {
            CoalitionSide coalition1 = getCoalition(true, true, true);
            CoalitionSide coalition2 = getCoalition(false, true, true);

            Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
            root.put("name", getName());
            root.put("start", TimeUtil.getTimeFromTurn(turnStart));
            root.put("end", turnEnd == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(turnEnd));
            root.put("wiki", getWiki());
            root.put("status", getStatusDesc());
            root.put("cb", getCasusBelli());
            root.put("posts", getAnnouncementsList());

            List<Object> coalitions = new ObjectArrayList<>();
            coalitions.add(coalition1.toMap(manager));
            coalitions.add(coalition2.toMap(manager));
            root.put("coalitions", coalitions);

            Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
            root.put("damage_header", new ObjectArrayList<>(damageHeader.keySet().stream().map(ConflictColumn::getName).toList()));
            root.put("header_desc", new ObjectArrayList<>(damageHeader.keySet().stream().map(ConflictColumn::getDescription).toList()));
            root.put("header_group", new ObjectArrayList<>(damageHeader.keySet().stream().map(f -> f.getType().name()).toList()));
            root.put("header_type", new ObjectArrayList<>(damageHeader.keySet().stream().map(f -> f.isCount() ? 1 : 0).toList()));
            root.put("war_web", warsVsAllianceJson(coalition1, coalition2, data.warsVsAlliance2));

            root.put("update_ms", System.currentTimeMillis());

            byte[] compressed = JteUtil.compress(JteUtil.getSerializer().writeValueAsBytes(root));
            return compressed;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public long getStartTurn(int allianceId) {
        return getData(true).getStartTime().getOrDefault(allianceId, turnStart);
    }
    public long getEndTurn(int allianceId) {
        return getData(true).getEndTime().getOrDefault(allianceId, turnEnd);
    }

    private CoalitionSide getCoalition(int aaId1, int aaId2, long turn) {
        CoalitionSide coalition1 = getCoalition(true, true, true);
        CoalitionSide coalition2 = getCoalition(true, true, true);
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
        return getDataWithWars().warsVsAlliance2.computeIfAbsent(aaId1, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(aaId2, k -> new DamageStatGroup());
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

    private ConflictManager getManager() {
        return Locutus.imp().getWarDb().getConflicts();
    }

    public Conflict setName(String name) {
        this.name = name;
        if (id > 0) getManager().updateConflictName(id, name);
        markDirtyIndexMeta();
        return this;
    }

    public Conflict setStart(long time) {
        long newTurn = TimeUtil.getTurn(time);
        if (newTurn == turnStart) return this;
        if (id > 0) { getManager().updateConflict(this, turnStart, turnEnd);
            markDirtyIndexMeta();
            markGraphsInvalid();
        }
        this.turnStart = newTurn;
        return this;
    }

    public Conflict setEnd(long time) {
        long newTurn = time == Long.MAX_VALUE ? time : TimeUtil.getTurn(time) + 1;
        if (newTurn == turnEnd) return this;
        if (id > 0) {
            getManager().updateConflict(this, turnStart, turnEnd);
            markDirtyIndexMeta();
            markDirtyWar();
            markGraphsInvalid();
        }
        this.turnEnd = newTurn;
        return this;
    }

    public Conflict addParticipant(int allianceId, boolean side, Long start, Long end) {
        return addParticipant(allianceId, side, true, false, start, end);
    }

    public Conflict addParticipant(int allianceId, boolean side, boolean save, boolean init, Long start, Long end) {
        trySet(f -> {
            if (init) {
                if (f.startTime2 == null) {
                    f.startTime2 = new Int2LongOpenHashMap();
                }
                if (f.endTime2 == null) {
                    f.endTime2 = new Int2LongOpenHashMap();
                }
                CoalitionSide coal1 = f.getCoalition1();
                CoalitionSide coal2 = f.getCoalition2();
                coal1.setOther(f.coalition_2);
                coal2.setOther(f.coalition_1);
            }
            if (f.startTime2 != null) {
                if (start != null && start > 0 && start != Long.MAX_VALUE) {
                    f.startTime2.put(allianceId, start);
                } else {
                    f.startTime2.remove(allianceId);
                }
            }
            if (f.endTime2 != null) {
                if (end != null && end != Long.MAX_VALUE) {
                    f.endTime2.put(allianceId, end);
                } else {
                    f.endTime2.remove(allianceId);
                }
            }
            CoalitionSide coal = (side ? f.coalition_1 : f.coalition_2);
            if (coal != null) {
                coal.add(allianceId);
            }
        });


        if (save) {
            long startFinal = start == null ? 0L : start;
            long endFinal = end == null ? Long.MAX_VALUE : end;
            if (id > 0) {
                getManager().addParticipant(this, allianceId, side, startFinal, endFinal);
                markDirtyWar();
                markGraphsInvalid();
            }
        }

        return this;
    }

    public Conflict removeParticipant(int allianceId) {
        trySet(tmp -> {
            if (tmp.coalition_1 != null) tmp.coalition_1.remove(allianceId);
            if (tmp.coalition_2 != null) tmp.coalition_2.remove(allianceId);
            if (tmp.startTime2 != null) tmp.startTime2.remove(allianceId);
            if (tmp.endTime2 != null) tmp.endTime2.remove(allianceId);
        });
        if (id > 0) {
            getManager().removeParticipant(this, allianceId);
            markDirtyWar();
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
        return Settings.INSTANCE.WEB.S3.SITE + "/conflict?id=" + id;
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
        return coalition1.getAllianceIds();
    }

    public Set<Integer> getCoalition2() {
        return coalition2.getAllianceIds();
    }

    @Command(desc = "If an alliance is a participant in the conflict")
    public boolean isParticipant(DBAlliance alliance) {
        return coalition1.hasAlliance(alliance.getId()) || coalition2.hasAlliance(alliance.getId());
    }

    @Command(desc = "A number representing the side an alliance is on in the conflict\n" +
            "0 = No side, 1 = Primary/Coalition 1, 2 = Secondary/Coalition 2")
    public int getSide(DBAlliance alliance) {
        // cd.coalition1 = new CoalitionSide(conflict, cd.col1, true);
        //            cd.coalition2 = new CoalitionSide(conflict, cd.col2, false);
        //            cd.coalition1.setOther(cd.coalition2);
        //            cd.coalition2.setOther(cd.coalition1);
        if (coalition1.hasAlliance(alliance.getId())) return 1;
        if (coalition2.hasAlliance(alliance.getId())) return 2;
        return 0;
    }

    @Command(desc = "The start turn of the conflict in milliseconds since unix epoch")
    public long getStartMS() {
        return TimeUtil.getTimeFromTurn(turnStart);
    }

    @Command(desc = "The end turn of the conflict in milliseconds since unix epoch")
    public long getEndMS() {
        return turnEnd == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(turnEnd);
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
        return coalition1.getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Set<DBAlliance> getCoalition2Obj() {
        return coalition2.getAllianceIds().stream().map(DBAlliance::getOrCreate).collect(Collectors.toSet());
    }

    public Boolean isSide(int allianceId) {
        if (coalition1.hasAlliance(allianceId)) return true;
        if (coalition2.hasAlliance(allianceId)) return false;
        return null;
    }

    public Set<Integer> getAllianceIds() {
        CoalitionSide coalition1 = getCoalition(true, true, false);
        CoalitionSide coalition2 = getCoalition(false, true, false);
        Set<Integer> ids = new IntOpenHashSet(coalition1.getAllianceIds().size() + coalition2.getAllianceIds().size());
        ids.addAll(coalition1.getAllianceIds());
        ids.addAll(coalition2.getAllianceIds());
        return ids;
    }

    @Command(desc = "The number of active wars in the conflict")
    public int getActiveWars() {
        if (!isActive()) return 0;
        CoalitionSide coalition1 = getCoalition(true, true, true);
        CoalitionSide coalition2 = getCoalition(false, true, true);
        return coalition1.getOffensiveStats(null, false).activeWars + coalition2.getOffensiveStats(null, false).activeWars;
    }

    @Command(desc = "The number of total wars in the conflict")
    public int getTotalWars() {
        CoalitionSide coalition1 = getCoalition(true, true, true);
        CoalitionSide coalition2 = getCoalition(false, true, true);
        return coalition1.getOffensiveStats(null, false).totalWars + coalition2.getOffensiveStats(null, false).totalWars;
    }

    @Command(desc = "The current damage dealt between the conflict's participants, using market prices")
    public double getDamageConverted(boolean isPrimary) {
        return getCoalition(isPrimary, true, true).getInflicted().getTotalConverted();
    }

    public void addAnnouncement(String desc, DBTopic topic, boolean saveToDB, boolean init) {
        trySet(tmp -> {
            if (tmp.announcements2 == null) {
                if (!init) return;
                tmp.announcements2 = new Object2ObjectOpenHashMap<>();
            }
            tmp.announcements2.entrySet().removeIf(f -> f.getValue().topic_id == topic.topic_id);
            tmp.announcements2.put(desc, topic);
        });
        if (saveToDB) {
            if (id > 0) getManager().addAnnouncement(id, topic.topic_id, desc);
        }
    }

    public Map<String, List> getAnnouncementsList() {
        ConflictMeta tmp = getData(false);
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
    public List<String> pushChanges(ConflictManager manager, String webIdOrNull, boolean includeMainPage, boolean includeGraphs, boolean updateIndex) {
        ICloudStorage aws = manager.getCloud();
        if (webIdOrNull == null) {
            if (getId() == -1) throw new IllegalArgumentException("Conflict has no id");
            webIdOrNull = Integer.toString(getId());
        }
        long ttl = isActive() ? TimeUnit.MINUTES.toSeconds(1) : TimeUnit.MINUTES.toSeconds(5);
        List<String> urls = new ArrayList<>();

        if (includeMainPage) {
            String key = "conflicts/" + webIdOrNull + ".gzip";
            byte[] value = getWarBytesZip(manager);
            aws.putObject(key, value, ttl);
            urls.add(aws.getLink(key));
        }

        if (includeGraphs) {
            String graphKey = "conflicts/graphs/" + webIdOrNull + ".gzip";
            byte[] graphValue = getGraphBytesZip(manager);
            aws.putObject(graphKey, graphValue, ttl);
            urls.add(aws.getLink(graphKey));
        }
        if (updateIndex) {
            manager.pushIndex();
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
        ConflictMeta tmp = wikiConflict.getData(false);
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
        if (id > 0) getManager().removeAnnouncement(id, topicId);
        trySet(tmp -> {
            Map<String, DBTopic> annMap = tmp.announcements2;
            if (annMap != null) {
                annMap.entrySet().removeIf(f -> f.getValue().topic_id == topicId);
            }
        });
        markDirtyPageMeta();
    }

    private void markGraphsInvalid() {
        this.recalcGraph = true;
    }
}