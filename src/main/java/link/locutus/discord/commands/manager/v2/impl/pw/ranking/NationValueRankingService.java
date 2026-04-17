package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NationValueRankingService {
    private NationValueRankingService() {
    }

    public record AttributeRequest(
            Guild snapshotGuild,
            NationList nations,
            TypedFunction<DBNation, Double> attribute,
            boolean groupByAlliance,
            boolean ascending,
            Long snapshotDate,
            boolean total
    ) {
        public static AttributeRequest normalize(
                Guild snapshotGuild,
                NationList nations,
                TypedFunction<DBNation, Double> attribute,
                boolean groupByAlliance,
                boolean ascending,
                Long snapshotDate,
                boolean total
        ) {
            boolean totalMode = groupByAlliance && total;
            return new AttributeRequest(snapshotGuild, nations, attribute, groupByAlliance, ascending, snapshotDate, totalMode);
        }
    }

    public record ProductionRequest(
            Guild snapshotGuild,
            Set<ResourceType> resources,
            NationList nationList,
            boolean ignoreMilitaryUpkeep,
            boolean ignoreTradeBonus,
            boolean ignoreNationBonus,
            boolean includeNegative,
            boolean includeInactive,
            boolean listByNation,
            boolean listAverage,
            Long snapshotDate,
            HighlightSelection highlights
    ) {
        public static ProductionRequest normalize(
                Guild snapshotGuild,
                Set<ResourceType> resources,
                NationList nationList,
                boolean ignoreMilitaryUpkeep,
                boolean ignoreTradeBonus,
                boolean ignoreNationBonus,
                boolean includeNegative,
                boolean includeInactive,
                boolean listByNation,
                boolean listAverage,
                Long snapshotDate,
                Set<NationOrAlliance> highlight
        ) {
            Set<ResourceType> resolvedResources = resources == null || resources.isEmpty()
                    ? EnumSet.allOf(ResourceType.class)
                    : EnumSet.copyOf(resources);
            NationList resolvedNationList = nationList == null
                    ? new SimpleNationList(Locutus.imp().getNationDB().getAllNations()).setFilter("*")
                    : nationList;
            return new ProductionRequest(
                    snapshotGuild,
                    Set.copyOf(resolvedResources),
                    resolvedNationList,
                    ignoreMilitaryUpkeep,
                    ignoreTradeBonus,
                    ignoreNationBonus,
                    includeNegative,
                    includeInactive,
                    listByNation,
                    !listByNation && listAverage,
                    snapshotDate,
                    HighlightSelection.normalize(highlight)
            );
        }
    }

    public record HighlightSelection(Set<Integer> nationIds, Set<Integer> allianceIds) {
        public static HighlightSelection normalize(Set<NationOrAlliance> highlight) {
            if (highlight == null || highlight.isEmpty()) {
                return new HighlightSelection(Set.of(), Set.of());
            }
            Set<Integer> nationIds = new IntOpenHashSet();
            Set<Integer> allianceIds = new IntOpenHashSet();
            for (NationOrAlliance entry : highlight) {
                if (entry == null) {
                    continue;
                }
                if (entry.isAlliance()) {
                    allianceIds.add(entry.getId());
                } else if (entry.isNation()) {
                    nationIds.add(entry.getId());
                }
            }
            return new HighlightSelection(Set.copyOf(nationIds), Set.copyOf(allianceIds));
        }

        public Set<Integer> idsFor(RankingEntityType entityType) {
            return entityType == RankingEntityType.ALLIANCE ? allianceIds : nationIds;
        }
    }

    public static RankingResult attributeRanking(AttributeRequest request) {
        Set<DBNation> snapshotNations = snapshotNations(request.nations(), request.snapshotDate(), request.snapshotGuild());
        Map<Integer, Double> valuesByNationId = new LinkedHashMap<>();
        for (DBNation nation : snapshotNations) {
            Double value = request.attribute().apply(nation);
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            valuesByNationId.put(nation.getNation_id(), value);
        }

        RankingAggregationMode aggregationMode = request.groupByAlliance()
                ? request.total() ? RankingAggregationMode.SUM : RankingAggregationMode.AVERAGE
                : RankingAggregationMode.IDENTITY;
        RankingEntityType entityType = request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        Map<Integer, Double> values = resolveSectionValues(snapshotNations, valuesByNationId, entityType, aggregationMode);

        String attributeLabel = request.attribute().getName();
        Map<String, Object> queryMetadata = new LinkedHashMap<>();
        queryMetadata.put("attribute", attributeLabel);
        if (request.snapshotDate() != null) {
            queryMetadata.put("snapshot_ms", request.snapshotDate());
        }

        return RankingBuilders.singleMetricRanking(
                "nation_attribute_ranking",
                entityType,
                RankingSupport.machineKey(attributeLabel),
                RankingValueFormat.NUMBER,
                RankingNumericType.DECIMAL,
                List.of(RankingBuilders.singleMetricSection(
                        entityType == RankingEntityType.ALLIANCE ? "alliances" : "nations",
                        RankingEntityType.NATION.name(),
                        aggregationMode,
                        request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                        values,
                        Map.of()
                )),
                queryMetadata,
                Set.of(),
                request.snapshotDate() == null ? System.currentTimeMillis() : request.snapshotDate(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );
    }

    public static RankingResult productionRanking(ProductionRequest request) {
        Set<DBNation> snapshotNations = snapshotNations(request.nationList(), request.snapshotDate(), request.snapshotGuild());
        if (!request.includeInactive()) {
            snapshotNations.removeIf(nation -> !nation.isTaxable());
        }

        Map<Integer, Integer> treasureByAllianceId = new HashMap<>();
        for (DBNation nation : snapshotNations) {
            if (nation.getAlliance_id() == 0) {
                continue;
            }
            for (DBTreasure ignored : nation.getTreasures()) {
                treasureByAllianceId.merge(nation.getAlliance_id(), 1, Integer::sum);
            }
        }

        Map<Integer, Double> valuesByNationId = new LinkedHashMap<>();
        Map<Integer, double[]> profitsByNationId = new LinkedHashMap<>();
        for (DBNation nation : snapshotNations) {
            int treasures = treasureByAllianceId.getOrDefault(nation.getAlliance_id(), 0);
            Set<DBTreasure> nationTreasures = nation.getTreasures();
            double treasureBonus = ((treasures == 0 ? 0 : Math.sqrt(treasures * 4))
                    + nationTreasures.stream().mapToDouble(DBTreasure::getBonus).sum()) * 0.01;

            double[] profit = nation.getRevenue(null, 12, true, !request.ignoreMilitaryUpkeep(), !request.ignoreTradeBonus(),
                    !request.ignoreNationBonus(), false, false, treasureBonus, false);
            double value = productionValue(request.resources(), profit);
            if (value > 0 || request.includeNegative()) {
                valuesByNationId.put(nation.getNation_id(), value);
                profitsByNationId.put(nation.getNation_id(), profit);
            }
        }

        RankingAggregationMode aggregationMode = request.listByNation()
                ? RankingAggregationMode.IDENTITY
                : request.listAverage() ? RankingAggregationMode.AVERAGE : RankingAggregationMode.SUM;
        RankingEntityType entityType = request.listByNation() ? RankingEntityType.NATION : RankingEntityType.ALLIANCE;
        Map<Integer, Double> values = entityType == RankingEntityType.ALLIANCE
            && aggregationMode == RankingAggregationMode.SUM
            && request.resources().size() > 1
            ? resolveAllianceSummedConvertedValues(snapshotNations, profitsByNationId, request.resources())
            : resolveSectionValues(snapshotNations, valuesByNationId, entityType, aggregationMode);
        RankingValueFormat valueFormat = productionValueFormat(request.resources());
        String metricKey = request.resources().size() == 1
                ? RankingSupport.machineKey(request.resources().iterator().next().name())
                : "market_value";
        Map<String, Object> queryMetadata = new LinkedHashMap<>();
        queryMetadata.put("resources", request.resources().stream().map(Enum::name).toList());
        queryMetadata.put("ignore_military_upkeep", request.ignoreMilitaryUpkeep());
        queryMetadata.put("ignore_trade_bonus", request.ignoreTradeBonus());
        queryMetadata.put("ignore_nation_bonus", request.ignoreNationBonus());
        queryMetadata.put("include_negative", request.includeNegative());
        queryMetadata.put("include_inactive", request.includeInactive());
        if (request.snapshotDate() != null) {
            queryMetadata.put("snapshot_ms", request.snapshotDate());
        }

        return RankingBuilders.singleMetricRanking(
                "producer_ranking",
                entityType,
                metricKey,
                valueFormat,
                RankingNumericType.DECIMAL,
                List.of(RankingBuilders.singleMetricSection(
                        entityType == RankingEntityType.ALLIANCE ? "alliances" : "nations",
                        RankingEntityType.NATION.name(),
                        aggregationMode,
                        RankingSortDirection.DESC,
                        values,
                        Map.of()
                )),
                queryMetadata,
                request.highlights().idsFor(entityType),
                request.snapshotDate() == null ? System.currentTimeMillis() : request.snapshotDate(),
                RankingEmptySectionPolicy.INCLUDE_EMPTY_SECTIONS
        );
    }

    private static Set<DBNation> snapshotNations(NationList nationList, Long snapshotDate, Guild snapshotGuild) {
        Set<DBNation> snapshot = PW.getNationsSnapshot(
                nationList.getNations(),
                nationList.getFilter(),
                snapshotDate,
                snapshotGuild
        );
        return new java.util.LinkedHashSet<>(snapshot);
    }

    private static Map<Integer, Double> resolveSectionValues(
            Set<DBNation> snapshotNations,
            Map<Integer, Double> valuesByNationId,
            RankingEntityType entityType,
            RankingAggregationMode aggregationMode
    ) {
        if (entityType == RankingEntityType.NATION) {
            return new LinkedHashMap<>(valuesByNationId);
        }

        Map<Integer, DBNation> nationById = new HashMap<>();
        for (DBNation nation : snapshotNations) {
            nationById.put(nation.getNation_id(), nation);
        }
        return RankingAggregations.aggregateByGroup(
                valuesByNationId,
                nationId -> {
                    DBNation nation = nationById.get(nationId);
                    if (nation == null || nation.getAlliance_id() == 0) {
                        return null;
                    }
                    return nation.getAlliance_id();
                },
                aggregationMode
        );
    }

    private static Map<Integer, Double> resolveAllianceSummedConvertedValues(
            Set<DBNation> snapshotNations,
            Map<Integer, double[]> profitsByNationId,
            Set<ResourceType> resources
    ) {
        Map<Integer, DBNation> nationById = new HashMap<>();
        for (DBNation nation : snapshotNations) {
            nationById.put(nation.getNation_id(), nation);
        }

        Map<Integer, double[]> totalsByAllianceId = new LinkedHashMap<>();
        for (Map.Entry<Integer, double[]> entry : profitsByNationId.entrySet()) {
            DBNation nation = nationById.get(entry.getKey());
            if (nation == null) {
                continue;
            }
            int allianceId = nation.getAlliance_id();
            if (allianceId == 0) {
                continue;
            }
            double[] allianceTotal = totalsByAllianceId.computeIfAbsent(allianceId,
                    ignored -> new double[ResourceType.values.length]);
            double[] profit = entry.getValue();
            for (ResourceType resource : resources) {
                int ordinal = resource.ordinal();
                allianceTotal[ordinal] += profit[ordinal];
            }
        }

        Map<Integer, Double> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, double[]> entry : totalsByAllianceId.entrySet()) {
            values.put(entry.getKey(), productionValue(resources, entry.getValue()));
        }
        return values;
    }

    private static double productionValue(Set<ResourceType> resources, double[] profit) {
        if (resources.size() == 1) {
            return profit[resources.iterator().next().ordinal()];
        }
        double value = 0;
        for (ResourceType type : resources) {
            value += ResourceType.convertedTotal(type, profit[type.ordinal()]);
        }
        return value;
    }

    private static RankingValueFormat productionValueFormat(Set<ResourceType> resources) {
        if (resources.size() != 1) {
            return RankingValueFormat.MONEY;
        }
        return resources.iterator().next() == ResourceType.MONEY ? RankingValueFormat.MONEY : RankingValueFormat.NUMBER;
    }
}
