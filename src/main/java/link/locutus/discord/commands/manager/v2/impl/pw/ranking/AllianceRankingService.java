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

import java.util.ArrayList;
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

    public record AttributeRequest(Set<DBAlliance> alliances, TypedFunction<DBAlliance, Double> attribute, String attributeLabel, boolean ascending, Set<Integer> highlightedAllianceIds) {
        public static AttributeRequest normalize(Set<DBAlliance> alliances, TypedFunction<DBAlliance, Double> attribute, String attributeLabel, boolean ascending, Set<DBAlliance> highlight) {
            String label = attributeLabel == null || attributeLabel.isBlank() ? attribute.getName() : attributeLabel;
            return new AttributeRequest(normalizeAlliances(alliances), attribute, label, ascending, normalizeHighlight(highlight));
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

        String label = request.metric().name();
        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(
                request.metric().name(),
                label,
                metricFormat(request.metric()),
                RankingNumericType.DECIMAL
        );

        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                values,
                request.highlightedAllianceIds(),
                AllianceRankingService::allianceName,
                RankingSupport.sectionMetadata(RankingEntityType.ALLIANCE.name(), RankingAggregationMode.IDENTITY),
                List.of()
        );

        return new RankingResult(
                "alliance_metric_ranking",
                "Top " + label + " by alliance",
                List.of(
                        RankingSupport.field("metric", "Metric", request.metric().name()),
                        RankingSupport.field("as_of_turn", "As Of Turn", turn)
                ),
                RankingBuilders.totalRowCount(List.of(section)),
                TimeUtil.getTimeFromTurn(turn),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
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

        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(request.attributeLabel(), request.attributeLabel(), RankingValueFormat.NUMBER, RankingNumericType.DECIMAL);
        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                values,
                request.highlightedAllianceIds(),
                AllianceRankingService::allianceName,
                RankingSupport.sectionMetadata(RankingEntityType.ALLIANCE.name(), RankingAggregationMode.IDENTITY),
                List.of()
        );
        return new RankingResult(
                "alliance_attribute_ranking",
                "Top " + request.attributeLabel() + " by alliance",
                List.of(RankingSupport.field("attribute", "Attribute", request.attributeLabel())),
                RankingBuilders.totalRowCount(List.of(section)),
                System.currentTimeMillis(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
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

        String label = request.metric().name();
        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(
                request.metric().name(),
                label,
                metricFormat(request.metric()),
                RankingNumericType.DECIMAL
        );

        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                values,
                request.highlightedAllianceIds(),
                AllianceRankingService::allianceName,
                RankingSupport.sectionMetadata(RankingEntityType.ALLIANCE.name(), RankingAggregationMode.IDENTITY),
                List.of("Values are deltas between the requested start and end turns.")
        );

        return new RankingResult(
                "alliance_metric_delta_ranking",
                "Change in " + label + " by alliance",
                List.of(
                        RankingSupport.field("metric", "Metric", request.metric().name()),
                        RankingSupport.field("start_ms", "Start", request.timeStart()),
                        RankingSupport.field("end_ms", "End", request.timeEnd())
                ),
                RankingBuilders.totalRowCount(List.of(section)),
                TimeUtil.getTimeFromTurn(turnEnd),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
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

        String label = request.showTotal() ? "Bank Total" : "Loot / Score";
        RankingMetricDescriptor metric = RankingSupport.metricDescriptor(label, label, RankingValueFormat.MONEY, RankingNumericType.DECIMAL);
        RankingSection section = RankingBuilders.singleMetricSection(
                "alliances",
                "Alliances",
                RankingEntityType.ALLIANCE,
                metric,
                RankingSortDirection.DESC,
                values,
                request.highlightedAllianceIds(),
                AllianceRankingService::allianceName,
                RankingSupport.sectionMetadata(RankingEntityType.ALLIANCE.name(), RankingAggregationMode.IDENTITY),
                List.of()
        );

        List<RankingQueryField> querySummary = new ArrayList<>();
        querySummary.add(RankingSupport.field("cutoff_ms", "Loot Since", request.timeMs()));
        querySummary.add(RankingSupport.field("mode", "Mode", request.showTotal() ? "total" : "per_score"));
        if (request.minScore() != null) {
            querySummary.add(RankingSupport.field("min_score", "Min Score", request.minScore()));
        }
        if (request.maxScore() != null) {
            querySummary.add(RankingSupport.field("max_score", "Max Score", request.maxScore()));
        }

        return new RankingResult(
                "alliance_loot_ranking",
                request.showTotal() ? "AA bank total" : "AA loot/score",
                List.copyOf(querySummary),
                RankingBuilders.totalRowCount(List.of(section)),
                System.currentTimeMillis(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS,
                List.of(section)
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
            case SI_UNIT,DECIMAL_ROUNDED -> RankingValueFormat.NUMBER;
            case PERCENTAGE_ONE, PERCENTAGE_100 -> RankingValueFormat.PERCENT;
        };
    }

    private static String allianceName(int allianceId) {
        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
        return alliance == null ? null : alliance.getName();
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
