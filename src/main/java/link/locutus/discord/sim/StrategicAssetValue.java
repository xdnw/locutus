package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.PW;

import java.util.function.IntToDoubleFunction;

/** Objective-scale asset valuation that deliberately does not use nation score. */
public final class StrategicAssetValue {
    public static final double CONVERTED_VALUE_SCALE = 100_000d;
    static final double SCARCITY_PREMIUM_WEIGHT = 0.40d;
    static final double RECOVERY_VALUE_WEIGHT = 0.35d;
    static final double ACTIVE_WAR_LEVERAGE_WEIGHT = 0.12d;
    static final double CITY_TIER_RELEVANCE_WEIGHT = 0.16d;
    static final double RANGE_COVERAGE_RELEVANCE_WEIGHT = 0.30d;
    private static final UnitReader ZERO_READER = unit -> 0;

    private StrategicAssetValue() {
    }

    public static double unitValue(MilitaryUnit unit, double amount, int researchBits) {
        if (unit == null || !(amount > 0d) || !SimUnits.isPurchasable(unit)) {
            return 0d;
        }
        return unit.getConvertedCost(researchBits) * amount / CONVERTED_VALUE_SCALE;
    }

    public static double unitValue(MilitaryUnit unit, int amount, int researchBits) {
        return unitValue(unit, (double) amount, researchBits);
    }

    public static double militaryValue(UnitReader units, int researchBits) {
        double value = 0d;
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            value += unitValue(unit, units.units(unit), researchBits);
        }
        return value;
    }

    public static double projectedRecoveryValue(MilitaryUnit unit, int projectedBuys, int researchBits) {
        return unitValue(unit, projectedBuys, researchBits) * RECOVERY_VALUE_WEIGHT;
    }

    public static NationValueBreakdown contextualMilitaryValue(
            UnitReader units,
            UnitReader pendingBuys,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars
    ) {
        return contextualMilitaryValue(
            units,
            pendingBuys,
            unitsBoughtToday,
            dailyBuyCaps,
            researchBits,
            hasActiveWars,
            StrategicRelevance.DEFAULT
        );
    }

    public static NationValueBreakdown contextualMilitaryValue(
            UnitReader units,
            UnitReader pendingBuys,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars,
            StrategicRelevance relevance
    ) {
        double replacementValue = 0d;
        double contextualMilitaryValue = 0d;
        double recoveryValue = 0d;
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int holdings = Math.max(0, units.units(unit)) + Math.max(0, pendingBuys.units(unit));
            if (holdings > 0) {
                replacementValue += unitValue(unit, holdings, researchBits);
                contextualMilitaryValue += scarcityPremiumValue(
                        unit,
                        holdings,
                        remainingRecoveryCapacity(unit, unitsBoughtToday, dailyBuyCaps),
                        researchBits
                );
            }
            int remainingRecovery = remainingRecoveryCapacity(unit, unitsBoughtToday, dailyBuyCaps);
            if (remainingRecovery > 0) {
                recoveryValue += projectedRecoveryValue(unit, remainingRecovery, researchBits);
            }
        }
        contextualMilitaryValue += replacementValue * contextualRelevanceAdjustment(relevance);
        double subtotal = replacementValue + contextualMilitaryValue + recoveryValue;
        double warOutcomeLeverage = hasActiveWars ? subtotal * ACTIVE_WAR_LEVERAGE_WEIGHT : 0d;
        return new NationValueBreakdown(replacementValue, contextualMilitaryValue, recoveryValue, warOutcomeLeverage);
    }

    public static NationValueBreakdown contextualMilitaryValue(
            UnitReader units,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars
    ) {
        return contextualMilitaryValue(units, ZERO_READER, unitsBoughtToday, dailyBuyCaps, researchBits, hasActiveWars);
    }

        public static NationValueBreakdown contextualMilitaryValue(
            UnitReader units,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars,
            StrategicRelevance relevance
        ) {
        return contextualMilitaryValue(units, ZERO_READER, unitsBoughtToday, dailyBuyCaps, researchBits, hasActiveWars, relevance);
        }

    public static double contextualLossValue(
            UnitReader currentUnits,
            UnitReader losses,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars
    ) {
        return contextualLossValue(
            currentUnits,
            losses,
            unitsBoughtToday,
            dailyBuyCaps,
            researchBits,
            hasActiveWars,
            StrategicRelevance.DEFAULT
        );
    }

    public static double contextualLossValue(
            UnitReader currentUnits,
            UnitReader losses,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            boolean hasActiveWars,
            StrategicRelevance relevance
    ) {
        double replacementValue = 0d;
        double contextualMilitaryValue = 0d;
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            int lossesForUnit = Math.max(0, losses.units(unit));
            if (lossesForUnit <= 0) {
                continue;
            }
            replacementValue += unitValue(unit, lossesForUnit, researchBits);
            contextualMilitaryValue += scarcityPremiumValue(
                    unit,
                    lossesForUnit,
                    remainingRecoveryCapacity(unit, unitsBoughtToday, dailyBuyCaps),
                    researchBits,
                    Math.max(lossesForUnit, currentUnits.units(unit))
            );
        }
        contextualMilitaryValue += replacementValue * contextualRelevanceAdjustment(relevance);
        double subtotal = replacementValue + contextualMilitaryValue;
        return subtotal + (hasActiveWars ? subtotal * ACTIVE_WAR_LEVERAGE_WEIGHT : 0d);
    }

    public static double resourceValue(double[] resources) {
        return resources == null ? 0d : ResourceType.convertedTotal(resources) / CONVERTED_VALUE_SCALE;
    }

    public static double resourceValue(double[] resourcesFlat, int baseOffset) {
        if (resourcesFlat == null || baseOffset < 0 || baseOffset + ResourceType.values.length > resourcesFlat.length) {
            return 0d;
        }
        double value = 0d;
        for (ResourceType type : ResourceType.values) {
            value += ResourceType.convertedTotal(type, resourcesFlat[baseOffset + type.ordinal()]);
        }
        return value / CONVERTED_VALUE_SCALE;
    }

    public static double nationValue(SimNation nation) {
        return contextualMilitaryValue(
                nation::units,
                nation::pendingBuys,
                nation::unitsBoughtToday,
                nation::dailyBuyCap,
                nation.researchBits(),
                nation.offSlotsUsed() > 0 || nation.defSlotsUsed() > 0
        ).totalValue();
    }

    public static StrategicRelevance relevanceForWarRange(
            int cityCount,
            double score,
            int activeOpponents,
            int opponentCount,
            IntToDoubleFunction opponentScoreReader
    ) {
        int normalizedCityCount = Math.max(0, cityCount);
        int normalizedActiveOpponents = Math.max(0, activeOpponents);
        int normalizedOpponentCount = Math.max(0, opponentCount);
        if (normalizedOpponentCount <= 0 || opponentScoreReader == null) {
            return new StrategicRelevance(normalizedCityCount, 0, normalizedOpponentCount, normalizedActiveOpponents);
        }
        double minScore = score * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;
        int reachableOpponents = 0;
        for (int index = 0; index < normalizedOpponentCount; index++) {
            double opponentScore = opponentScoreReader.applyAsDouble(index);
            if (opponentScore >= minScore && opponentScore <= maxScore) {
                reachableOpponents++;
            }
        }
        return new StrategicRelevance(normalizedCityCount, reachableOpponents, normalizedOpponentCount, normalizedActiveOpponents);
    }

    private static double scarcityPremiumValue(
            MilitaryUnit unit,
            int amount,
            int remainingRecoveryCapacity,
            int researchBits
    ) {
        return scarcityPremiumValue(unit, amount, remainingRecoveryCapacity, researchBits, amount);
    }

    private static double scarcityPremiumValue(
            MilitaryUnit unit,
            int amount,
            int remainingRecoveryCapacity,
            int researchBits,
            int scarcityBaseline
    ) {
        if (amount <= 0) {
            return 0d;
        }
        return unitValue(unit, amount, researchBits)
                * SCARCITY_PREMIUM_WEIGHT
                * scarcityFactor(Math.max(1, scarcityBaseline), remainingRecoveryCapacity);
    }

    private static double scarcityFactor(int baselineAmount, int remainingRecoveryCapacity) {
        if (baselineAmount <= 0) {
            return 0d;
        }
        return 1d - Math.min(1d, remainingRecoveryCapacity / (double) baselineAmount);
    }

    private static int remainingRecoveryCapacity(MilitaryUnit unit, UnitReader unitsBoughtToday, UnitReader dailyBuyCaps) {
        return Math.max(0, dailyBuyCaps.units(unit) - unitsBoughtToday.units(unit));
    }

    private static double contextualRelevanceAdjustment(StrategicRelevance relevance) {
        if (relevance == null) {
            return 0d;
        }
        double cityPremium = 0d;
        if (relevance.cityCount() > 0) {
            double cityFactor = clamp((relevance.cityCount() - 6d) / 18d);
            cityPremium = (cityFactor - 0.25d) * CITY_TIER_RELEVANCE_WEIGHT;
        }
        double rangePremium = 0d;
        if (relevance.totalOpponents() > 0) {
            double reachableCoverage = relevance.reachableOpponents() / (double) relevance.totalOpponents();
            double engagedCoverage = Math.max(
                    reachableCoverage,
                    Math.min(1d, relevance.activeOpponents() / (double) relevance.totalOpponents())
            );
            rangePremium = (engagedCoverage - 0.5d) * RANGE_COVERAGE_RELEVANCE_WEIGHT;
        }
        return Math.max(-0.28d, Math.min(0.28d, cityPremium + rangePremium));
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    public record NationValueBreakdown(
            double replacementValue,
            double contextualMilitaryValue,
            double recoveryValue,
            double warOutcomeLeverage
    ) {
        public double totalValue() {
            return replacementValue + contextualMilitaryValue + recoveryValue + warOutcomeLeverage;
        }
    }

    public record StrategicRelevance(
            int cityCount,
            int reachableOpponents,
            int totalOpponents,
            int activeOpponents
    ) {
        public static final StrategicRelevance DEFAULT = new StrategicRelevance(0, 0, 0, 0);

        public StrategicRelevance {
            cityCount = Math.max(0, cityCount);
            reachableOpponents = Math.max(0, reachableOpponents);
            totalOpponents = Math.max(reachableOpponents, totalOpponents);
            activeOpponents = Math.max(0, activeOpponents);
        }
    }

    @FunctionalInterface
    public interface UnitReader {
        int units(MilitaryUnit unit);
    }
}