package link.locutus.discord.db.conflict;

import link.locutus.discord.util.StringMan;

import java.util.Map;

public enum HeaderGroup {
INDEX_META,

//spec("id", HeaderGroup.INDEX_META, Conflict::getId),
//spec("name", HeaderGroup.INDEX_META, Conflict::getName),
//spec("c1_name", HeaderGroup.INDEX_META, f -> f.getCoalitionName(true)),
//spec("c2_name", HeaderGroup.INDEX_META, f -> f.getCoalitionName(false)),
//spec("start", HeaderGroup.INDEX_META, f -> TimeUtil.getTimeFromTurn(f.getStartTurn())),
//spec("end", HeaderGroup.INDEX_META, f -> f.getEndTurn() == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(f.getEndTurn())),
//spec("c1", HeaderGroup.INDEX_META, f -> new IntArrayList(f.getCoalition1())),
//spec("c2", HeaderGroup.INDEX_META, f -> new IntArrayList(f.getCoalition2())),
//spec("wiki", HeaderGroup.INDEX_META, Conflict::getWiki),
//spec("status", HeaderGroup.INDEX_META, Conflict::getStatusDesc),
//spec("cb", HeaderGroup.INDEX_META, Conflict::getCasusBelli),
//spec("posts", HeaderGroup.INDEX_META, Conflict::getAnnouncementsList),
//spec("source", HeaderGroup.INDEX_META, Conflict::getGuildId),
//spec("category", HeaderGroup.INDEX_META, f -> f.getCategory().name())

INDEX_STATS,
//spec("wars", HeaderGroup.INDEX_STATS, Conflict::getTotalWars),
//spec("active_wars", HeaderGroup.INDEX_STATS, Conflict::getActiveWars),
//spec("c1_dealt", HeaderGroup.INDEX_STATS, f -> (long) f.getDamageConverted(true)),
//spec("c2_dealt", HeaderGroup.INDEX_STATS, f -> (long) f.getDamageConverted(false))

PAGE_META,
//CoalitionSide coalition1 = getCoalition(true, true, true);
//CoalitionSide coalition2 = getCoalition(false, true, true);
//Map<String, Object> meta = new Object2ObjectLinkedOpenHashMap<>();
//meta.put("name", getName());
//meta.put("start", TimeUtil.getTimeFromTurn(turnStart));
//meta.put("end", turnEnd == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(turnEnd));
//meta.put("wiki", getWiki());
//meta.put("status", getStatusDesc());
//meta.put("cb", getCasusBelli());
//meta.put("posts", getAnnouncementsList());
//List<Object> coalitions = new ObjectArrayList<>();
//coalitions.add(coalition1.toMap(manager, true, false));
//coalitions.add(coalition2.toMap(manager, true, false));
//meta.put("coalitions", coalitions);
PAGE_STATS,
//CoalitionSide coalition1 = getCoalition(true, true, true);
//CoalitionSide coalition2 = getCoalition(false, true, true);
//Map<String, Object> stats = new Object2ObjectLinkedOpenHashMap<>();
//Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeader = DamageStatGroup.createHeader();
//stats.put("damage_header", new ObjectArrayList<>(damageHeader.keySet().stream().map(ConflictColumn::getName).toList()));
//stats.put("header_desc", new ObjectArrayList<>(damageHeader.keySet().stream().map(ConflictColumn::getDescription).toList()));
//stats.put("header_group", new ObjectArrayList<>(damageHeader.keySet().stream().map(f -> f.getType().name()).toList()));
//stats.put("header_type", new ObjectArrayList<>(damageHeader.keySet().stream().map(f -> f.isCount() ? 1 : 0).toList()));
//Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance = getDataWithWars().warsVsAlliance2;
//if (warsVsAlliance == null) warsVsAlliance = new Int2ObjectOpenHashMap<>();
//stats.put("war_web", warsVsAllianceJson(coalition1, coalition2, warsVsAlliance));
//List<Object> coalitions = new ObjectArrayList<>();
//coalitions.add(coalition1.toMap(manager, false, true));
//coalitions.add(coalition2.toMap(manager, false, true));
//stats.put("coalitions", coalitions);

GRAPH_META,
// CoalitionSide coalition1 = getCoalition(true, true, false);
//CoalitionSide coalition2 = getCoalition(false, true, false);
//Map<String, Object> graphMeta = new Object2ObjectLinkedOpenHashMap<>();
//graphMeta.put("name", getName());
//graphMeta.put("start", TimeUtil.getTimeFromTurn(turnStart));
//graphMeta.put("end", turnEnd == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(turnEnd));
//List<Map<String, Object>> coalitions = new ObjectArrayList<>();
//coalitions.add(coalition1.toMap(manager, true, false));
//coalitions.add(coalition2.toMap(manager, true, false));
//graphMeta.put("coalitions", coalitions);
GRAPH_DATA
//List<String> metricNames = new ObjectArrayList<>();
//
//List<Integer> metricsDay = new IntArrayList();
//List<Integer> metricsTurn = new IntArrayList();
//
//for (ConflictMetric metric : ConflictMetric.values) {
//    (metric.isDay() ? metricsDay : metricsTurn).add(metricNames.size());
//    metricNames.add(metric.name().toLowerCase(Locale.ROOT));
//}
//
//Map<ConflictColumn, Function<DamageStatGroup, Object>> damageHeaders = DamageStatGroup.createRanking();
//List<ConflictColumn> columns = new ObjectArrayList<>(damageHeaders.keySet());
//List<Function<DamageStatGroup, Object>> valueFuncs = columns.stream().map(damageHeaders::get).toList();
//
//int columnMetricOffset = metricNames.size();
//
//for (ConflictColumn column : columns) {
//    metricsDay.add(metricNames.size());
//    String defPrefix = column.isCount() ? "def:" : "loss:";
//    metricNames.add(defPrefix + column.getName());
//    metricsDay.add(metricNames.size());
//    String attPrefix = column.isCount() ? "off:" : "dealt:";
//    metricNames.add(attPrefix + column.getName());
//}
//
//graphData.put("metric_names", metricNames);
//graphData.put("metrics_turn", metricsTurn);
//graphData.put("metrics_day", metricsDay);
//
//// Build coalition graph maps
//CoalitionSide coalition1 = getCoalition(true, true, true);
//CoalitionSide coalition2 = getCoalition(false, true, true);
//
//List<Map<String, Object>> coalitions = new ObjectArrayList<>();
//coalitions.add(coalition1.toGraphMap(manager, metricsTurn, metricsDay, valueFuncs, columnMetricOffset));
//coalitions.add(coalition2.toGraphMap(manager, metricsTurn, metricsDay, valueFuncs, columnMetricOffset));
//graphData.put("coalitions", coalitions);

;

public static final HeaderGroup[] values = values();

public abstract long getHash(); // StringMan.hash(List<String>) - of the header names

public abstract Map<String, Object> write(ConflictManager manager, Conflict conflict);

}
