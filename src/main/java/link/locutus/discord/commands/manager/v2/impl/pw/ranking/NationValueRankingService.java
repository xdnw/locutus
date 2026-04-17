package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        public AttributeRequest {
            nations = Objects.requireNonNull(nations, "nations");
            attribute = Objects.requireNonNull(attribute, "attribute");
            if (total && !groupByAlliance) {
                throw new IllegalArgumentException("total requires groupByAlliance");
            }
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
        public ProductionRequest {
            resources = Set.copyOf(Objects.requireNonNull(resources, "resources"));
            nationList = Objects.requireNonNull(nationList, "nationList");
            highlights = Objects.requireNonNull(highlights, "highlights");
            if (listAverage && listByNation) {
                throw new IllegalArgumentException("listAverage requires alliance rows");
            }
        }
    }

    public record HighlightSelection(Set<Integer> nationIds, Set<Integer> allianceIds) {
        public HighlightSelection {
            nationIds = Set.copyOf(Objects.requireNonNull(nationIds, "nationIds"));
            allianceIds = Set.copyOf(Objects.requireNonNull(allianceIds, "allianceIds"));
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

        return RankingBuilders.singleMetricRanking(
                RankingKind.NATION_ATTRIBUTE,
                entityType,
                RankingValueFormat.NUMBER,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        request.ascending() ? RankingSortDirection.ASC : RankingSortDirection.DESC,
                        values
                )),
                Set.of(),
                request.snapshotDate() == null ? System.currentTimeMillis() : request.snapshotDate()
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

        return RankingBuilders.singleMetricRanking(
                RankingKind.PRODUCTION,
                entityType,
                valueFormat,
                List.of(RankingBuilders.singleMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        RankingSortDirection.DESC,
                        values
                )),
                request.highlights().idsFor(entityType),
                request.snapshotDate() == null ? System.currentTimeMillis() : request.snapshotDate()
        );
    }

    private static Set<DBNation> snapshotNations(NationList nationList, Long snapshotDate, Guild snapshotGuild) {
        Set<DBNation> snapshot = PW.getNationsSnapshot(
                nationList.getNations(),
                nationList.getFilter(),
                snapshotDate,
                snapshotGuild
        );
        return new LinkedHashSet<>(snapshot);
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
