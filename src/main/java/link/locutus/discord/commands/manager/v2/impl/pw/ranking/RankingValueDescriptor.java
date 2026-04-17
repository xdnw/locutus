package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import com.fasterxml.jackson.annotation.JsonInclude;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.db.entities.metric.AllianceMetric;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingValueDescriptor(
        RankingValueKind kind,
        RankingValueFormat format,
        RankingNumericType numericType,
        RankingAggregationMode aggregationMode,
        String attributeName,
        AllianceMetric allianceMetric,
        ResourceType resource,
        RankingLootMode lootMode,
        WarCostMode warCostMode,
        WarCostStat warCostStat,
        AttackType attackType,
        RankingNormalizationMode normalizationMode
) {
    public RankingValueDescriptor {
        kind = Objects.requireNonNull(kind, "kind");
        format = Objects.requireNonNull(format, "format");
        numericType = Objects.requireNonNull(numericType, "numericType");
        aggregationMode = Objects.requireNonNull(aggregationMode, "aggregationMode");

        switch (kind) {
            case ALLIANCE_METRIC -> allianceMetric = Objects.requireNonNull(allianceMetric, "allianceMetric");
            case ATTRIBUTE -> attributeName = requireText(attributeName, "attributeName");
            case ALLIANCE_LOOT -> lootMode = Objects.requireNonNull(lootMode, "lootMode");
            case PRODUCTION -> {
            }
            case RECRUITMENT -> {
            }
            case WAR_COUNT -> normalizationMode = Objects.requireNonNull(normalizationMode, "normalizationMode");
            case WAR_COST -> {
                warCostMode = Objects.requireNonNull(warCostMode, "warCostMode");
                warCostStat = Objects.requireNonNull(warCostStat, "warCostStat");
                normalizationMode = Objects.requireNonNull(normalizationMode, "normalizationMode");
            }
            case ATTACK_TYPE -> attackType = Objects.requireNonNull(attackType, "attackType");
        }
    }

    public static RankingValueDescriptor allianceMetric(AllianceMetric metric, RankingValueFormat format, RankingNumericType numericType) {
        return new RankingValueDescriptor(
                RankingValueKind.ALLIANCE_METRIC,
                format,
                numericType,
                RankingAggregationMode.IDENTITY,
                null,
                metric,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RankingValueDescriptor attribute(String attributeName, RankingValueFormat format, RankingNumericType numericType, RankingAggregationMode aggregationMode) {
        return new RankingValueDescriptor(
                RankingValueKind.ATTRIBUTE,
                format,
                numericType,
                aggregationMode,
                attributeName,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RankingValueDescriptor allianceLoot(RankingLootMode lootMode) {
        return new RankingValueDescriptor(
                RankingValueKind.ALLIANCE_LOOT,
                RankingValueFormat.MONEY,
                RankingNumericType.DECIMAL,
                RankingAggregationMode.IDENTITY,
                null,
                null,
                null,
                lootMode,
                null,
                null,
                null,
                null
        );
    }

    public static RankingValueDescriptor production(ResourceType resource, RankingValueFormat format, RankingAggregationMode aggregationMode) {
        return new RankingValueDescriptor(
                RankingValueKind.PRODUCTION,
                format,
                RankingNumericType.DECIMAL,
                aggregationMode,
                null,
                null,
                resource,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RankingValueDescriptor recruitment() {
        return new RankingValueDescriptor(
                RankingValueKind.RECRUITMENT,
                RankingValueFormat.COUNT,
                RankingNumericType.INTEGER,
                RankingAggregationMode.COUNT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static RankingValueDescriptor warCount(RankingValueFormat format, RankingNumericType numericType, RankingNormalizationMode normalizationMode) {
        return new RankingValueDescriptor(
                RankingValueKind.WAR_COUNT,
                format,
                numericType,
                RankingAggregationMode.COUNT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                normalizationMode
        );
    }

    public static RankingValueDescriptor warCost(
            WarCostMode warCostMode,
            WarCostStat warCostStat,
            RankingValueFormat format,
            RankingNumericType numericType,
            RankingNormalizationMode normalizationMode
    ) {
        return new RankingValueDescriptor(
                RankingValueKind.WAR_COST,
                format,
                numericType,
                RankingAggregationMode.SUM,
                null,
                null,
                null,
                null,
                warCostMode,
                warCostStat,
                null,
                normalizationMode
        );
    }

    public static RankingValueDescriptor attackType(AttackType attackType, RankingValueFormat format, RankingNumericType numericType) {
        return new RankingValueDescriptor(
                RankingValueKind.ATTACK_TYPE,
                format,
                numericType,
                RankingAggregationMode.COUNT,
                null,
                null,
                null,
                null,
                null,
                null,
                attackType,
                null
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }
}
