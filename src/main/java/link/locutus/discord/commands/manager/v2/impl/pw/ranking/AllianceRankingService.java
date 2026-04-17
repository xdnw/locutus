package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.util.TimeUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class AllianceRankingService {
    private AllianceRankingService() {
    }

    public record MetricRequest(Set<DBAlliance> alliances, AllianceMetric metric, boolean ascending, Set<Integer> highlightedAllianceIds) {
        public static MetricRequest normalize(Set<DBAlliance> alliances, AllianceMetric metric, boolean ascending, Set<DBAlliance> highlight) {
            return new MetricRequest(normalizeAlliances(alliances), metric, ascending, normalizeHighlight(highlight));
        }
    }

    public record AttributeRequest(Set<DBAlliance> alliances, TypedFunction<DBAlliance, Double> attribute, boolean ascending, Set<Integer> highlightedAllianceIds) {
        public static AttributeRequest normalize(Set<DBAlliance> alliances, TypedFunction<DBAlliance, Double> attribute, boolean ascending, Set<DBAlliance> highlight) {
            return new AttributeRequest(normalizeAlliances(alliances), attribute, ascending, normalizeHighlight(highlight));
        }
    }

    public record DeltaRequest(Set<DBAlliance> alliances, AllianceMetric metric, long timeStart, long timeEnd, boolean ascending, Set<Integer> highlightedAllianceIds) {
        public static DeltaRequest normalize(Set<DBAlliance> alliances, AllianceMetric metric, long timeStart, long timeEnd, boolean ascending, Set<DBAlliance> highlight) {
            if (timeEnd < timeStart) {
                throw new IllegalArgumentException("timeEnd must be >= timeStart");
            }
            return new DeltaRequest(normalizeAlliances(alliances), metric, timeStart, timeEnd, ascending, normalizeHighlight(highlight));
        }
    }

    public record LootRequest(long timeMs, boolean showTotal, Double minScore, Double maxScore, Set<Integer> highlightedAllianceIds) {
        public static LootRequest normalize(long timeMs, boolean showTotal, Double minScore, Double maxScore, Set<DBAlliance> highlight) {
            if (minScore != null && maxScore != null && minScore > maxScore) {
                throw new IllegalArgumentException("minScore must be <= maxScore");
            }
            return new LootRequest(timeMs, showTotal, minScore, maxScore, normalizeHighlight(highlight));
        }
    }

    public static RankingResult metricRanking(MetricRequest request) {
        long turn = TimeUtil.getTurn();
        Set<Integer> allianceIds = request.alliances().stream()
                .map(DBAlliance::getAlliance_id)
                .collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap =
                Locutus.imp().getNationDB().getAllianceMetrics(allianceIds, request.metric(), turn);

        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metricMap.entrySet()) {
            Double value = metricValueAtOrBefore(entry.getValue().get(request.metric()), turn);
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            values.put(entry.getKey().getAlliance_id(), value);
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.ALLIANCE_METRIC,
                RankingEntityType.ALLIANCE,
                metricFormat(request.metric()),
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                        values
                )),
                request.highlightedAllianceIds(),
                TimeUtil.getTimeFromTurn(turn)
        );
    }

    public static RankingResult attributeRanking(AttributeRequest request) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (DBAlliance alliance : request.alliances()) {
            Double value = request.attribute().apply(alliance);
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            values.put(alliance.getAlliance_id(), value);
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.ALLIANCE_ATTRIBUTE,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.NUMBER,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                        values
                )),
                request.highlightedAllianceIds(),
                System.currentTimeMillis()
        );
    }

    public static RankingResult deltaRanking(DeltaRequest request) {
        long turnStart = TimeUtil.getTurn(request.timeStart());
        long turnEnd = TimeUtil.getTurn(request.timeEnd());
        Set<Integer> allianceIds = request.alliances().stream()
                .map(DBAlliance::getAlliance_id)
                .collect(Collectors.toSet());

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsStart =
                Locutus.imp().getNationDB().getAllianceMetrics(allianceIds, request.metric(), turnStart);
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricsEnd =
                Locutus.imp().getNationDB().getAllianceMetrics(allianceIds, request.metric(), turnEnd);

        Map<Integer, Double> startValuesByAllianceId = new HashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metricsStart.entrySet()) {
            Double value = metricValueAtOrBefore(entry.getValue().get(request.metric()), turnStart);
            if (value != null && Double.isFinite(value)) {
                startValuesByAllianceId.put(entry.getKey().getAlliance_id(), value);
            }
        }

        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Map.Entry<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> entry : metricsEnd.entrySet()) {
            int allianceId = entry.getKey().getAlliance_id();
            Double startValue = startValuesByAllianceId.get(allianceId);
            Double endValue = metricValueAtOrBefore(entry.getValue().get(request.metric()), turnEnd);
            if (startValue == null || endValue == null || !Double.isFinite(endValue)) {
                continue;
            }

            double delta = endValue - startValue;
            if (!Double.isFinite(delta)) {
                continue;
            }
            values.put(allianceId, delta);
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.ALLIANCE_METRIC_DELTA,
                RankingEntityType.ALLIANCE,
                metricFormat(request.metric()),
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                        values
                )),
                request.highlightedAllianceIds(),
                TimeUtil.getTimeFromTurn(turnEnd)
        );
    }

    public static RankingResult lootRanking(LootRequest request) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances()) {
            double score = alliance.getScore();
            if (score <= 0) {
                continue;
            }
            if (request.minScore() != null || request.maxScore() != null) {
                boolean hasMatchingNation = false;
                for (DBNation nation : alliance.getNations(true, 0, true)) {
                    double nationScore = nation.getScore();
                    if (request.minScore() != null && nationScore < request.minScore()) {
                        continue;
                    }
                    if (request.maxScore() != null && nationScore > request.maxScore()) {
                        continue;
                    }
                    hasMatchingNation = true;
                    break;
                }
                if (!hasMatchingNation) {
                    continue;
                }
            }
            LootEntry loot = alliance.getLoot();
            if (loot == null || loot.getDate() < request.timeMs()) {
                continue;
            }
            double value = request.showTotal()
                    ? loot.convertedTotal()
                    : ResourceType.convertedTotal(loot.getAllianceLootValue(1));
            if (!Double.isFinite(value)) {
                continue;
            }
            values.put(alliance.getAlliance_id(), value);
        }

        return RankingBuilders.singleMetricRanking(
                RankingKind.ALLIANCE_LOOT,
                RankingEntityType.ALLIANCE,
                RankingValueFormat.MONEY,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.ALLIANCES,
                        RankingSortDirection.DESC,
                        values
                )),
                request.highlightedAllianceIds(),
                System.currentTimeMillis()
        );
    }

    private static Set<DBAlliance> normalizeAlliances(Set<DBAlliance> alliances) {
        Set<DBAlliance> source = alliances == null || alliances.isEmpty()
                ? Locutus.imp().getNationDB().getAlliances()
                : alliances;

        Set<DBAlliance> resolved = new LinkedHashSet<>();
        for (DBAlliance alliance : source) {
            if (alliance != null) {
                resolved.add(alliance);
            }
        }
        return Set.copyOf(resolved);
    }

    private static Set<Integer> normalizeHighlight(Set<DBAlliance> highlight) {
        if (highlight == null || highlight.isEmpty()) {
            return Set.of();
        }
        Set<Integer> result = new IntOpenHashSet();
        for (DBAlliance alliance : highlight) {
            if (alliance != null) {
                result.add(alliance.getAlliance_id());
            }
        }
        return Set.copyOf(result);
    }

    private static RankingValueFormat metricFormat(AllianceMetric metric) {
        TableNumberFormat format = metric.getFormat();
        if (format == null) {
            return RankingValueFormat.NUMBER;
        }
        return switch (format) {
            case SI_UNIT, DECIMAL_ROUNDED -> RankingValueFormat.NUMBER;
            case PERCENTAGE_ONE, PERCENTAGE_100 -> RankingValueFormat.PERCENT;
        };
    }

    private static Double metricValueAtOrBefore(Map<Long, Double> valuesByTurn, long requestedTurn) {
        if (valuesByTurn == null || valuesByTurn.isEmpty()) {
            return null;
        }

        Long bestTurn = null;
        Double bestValue = null;
        for (Map.Entry<Long, Double> entry : valuesByTurn.entrySet()) {
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value)) {
                continue;
            }

            long turn = entry.getKey();
            if (turn <= requestedTurn && (bestTurn == null || turn > bestTurn)) {
                bestTurn = turn;
                bestValue = value;
            }
        }
        return bestValue;
    }
}
