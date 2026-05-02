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
    static final double INFRASTRUCTURE_STRATEGIC_WEIGHT = 0.015d;
    static final int DEFAULT_STARTING_MAPS = 12;
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
        return contextualMilitaryValue(
                units,
                pendingBuys,
                unitsBoughtToday,
                dailyBuyCaps,
                researchBits,
                ActiveWarContext.basic(hasActiveWars),
                relevance
        );
    }

    public static NationValueBreakdown contextualMilitaryValue(
            UnitReader units,
            UnitReader pendingBuys,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            ActiveWarContext activeWarContext,
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
        double warOutcomeLeverage = subtotal * activeWarLeverageWeight(activeWarContext);
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
        return contextualLossValue(
                currentUnits,
                losses,
                unitsBoughtToday,
                dailyBuyCaps,
                researchBits,
                ActiveWarContext.basic(hasActiveWars),
                relevance
        );
    }

    public static double contextualLossValue(
            UnitReader currentUnits,
            UnitReader losses,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            ActiveWarContext activeWarContext,
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
        return subtotal + (subtotal * activeWarLeverageWeight(activeWarContext));
    }

    public static double marginalLossValue(
            UnitReader currentUnits,
            UnitReader losses,
            UnitReader unitsBoughtToday,
            UnitReader dailyBuyCaps,
            int researchBits,
            ActiveWarContext activeWarContext,
            StrategicRelevance relevance
    ) {
        double lossValue = contextualLossValue(
                currentUnits,
                losses,
                unitsBoughtToday,
                dailyBuyCaps,
                researchBits,
                activeWarContext,
                relevance
        );
        return lossValue * marginalActionSpaceMultiplier(activeWarContext);
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

    public static double infrastructureValue(
            double[] cityInfra,
            ActiveWarContext activeWarContext,
            StrategicRelevance relevance
    ) {
        if (cityInfra == null || cityInfra.length == 0) {
            return 0d;
        }
        return infrastructureValue(index -> cityInfra[index], cityInfra.length, activeWarContext, relevance);
    }

    public static double infrastructureValue(
            CityInfraReader cityInfra,
            int cityCount,
            ActiveWarContext activeWarContext,
            StrategicRelevance relevance
    ) {
        if (cityInfra == null || cityCount <= 0) {
            return 0d;
        }
        double replacementValue = 0d;
        for (int cityIndex = 0; cityIndex < cityCount; cityIndex++) {
            double infra = cityInfra.infra(cityIndex);
            if (Double.isFinite(infra) && infra > 0d) {
                replacementValue += PW.City.Infra.calculateInfra(0d, infra);
            }
        }
        if (!(replacementValue > 0d)) {
            return 0d;
        }
        return replacementValue
                / CONVERTED_VALUE_SCALE
                * INFRASTRUCTURE_STRATEGIC_WEIGHT
                * infrastructureStateMultiplier(activeWarContext)
                * infrastructureRelevanceMultiplier(relevance);
    }

    public static double marginalActionSpaceMultiplier(ActiveWarContext context) {
        if (context == null || !context.hasActiveWars()) {
            return 1d;
        }
        if (!context.outcomeRelevant()) {
            return 0.35d;
        }
        double statePosition = (0.40d * context.controlPosition())
                + (0.35d * context.resistancePosition())
                + (0.25d * context.mapPosition());
        if (statePosition <= -0.75d) {
            return 0.45d;
        }
        if (statePosition <= -0.35d) {
            return 0.65d;
        }
        if (statePosition < 0.10d) {
            return 0.85d + (0.15d * ((statePosition + 0.35d) / 0.45d));
        }
        return 1d;
    }

    public static double nationValue(SimNation nation) {
        StrategicRelevance relevance = StrategicRelevance.DEFAULT;
        ActiveWarContext activeWarContext = ActiveWarContext.fromSlots(
                nation.offSlotsUsed(),
                nation.maxOffSlots(),
                nation.defSlotsUsed(),
                nation.offSlotsUsed() + nation.defSlotsUsed()
        );
        return contextualMilitaryValue(
                nation::units,
                nation::pendingBuys,
                nation::unitsBoughtToday,
                nation::dailyBuyCap,
                nation.researchBits(),
                activeWarContext,
                relevance
        ).totalValue() + infrastructureValue(nation.cityInfra(), activeWarContext, relevance);
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

    private static double activeWarLeverageWeight(ActiveWarContext context) {
        if (context == null || !context.hasActiveWars()) {
            return 0d;
        }
        if (!context.outcomeRelevant()) {
            return ACTIVE_WAR_LEVERAGE_WEIGHT * 0.20d;
        }
        double statePosition = (0.35d * context.controlPosition())
                + (0.25d * context.mapPosition())
                + (0.40d * context.resistancePosition());
        double tenability;
        if (statePosition <= -0.75d) {
            tenability = 0.25d;
        } else if (statePosition <= -0.35d) {
            tenability = 0.55d;
        } else {
            tenability = 1.0d + (0.30d * Math.max(0d, statePosition));
        }
        double slotPressureMultiplier = 0.85d + (0.30d * context.slotPressure());
        double opponentMultiplier = Math.min(1.35d, 0.85d + (0.15d * context.activeOpponents()));
        return ACTIVE_WAR_LEVERAGE_WEIGHT * slotPressureMultiplier * opponentMultiplier * tenability;
    }

    private static double infrastructureStateMultiplier(ActiveWarContext context) {
        if (context == null || !context.hasActiveWars()) {
            return 0.16d;
        }
        if (!context.outcomeRelevant()) {
            return 0.03d;
        }
        double statePosition = (0.45d * context.controlPosition())
                + (0.35d * context.resistancePosition())
                + (0.20d * context.mapPosition());
        if (statePosition <= -0.65d) {
            return 0.04d;
        }
        if (statePosition <= -0.25d) {
            return 0.08d;
        }
        if (statePosition < 0.20d) {
            return 0.16d + (0.20d * ((statePosition + 0.25d) / 0.45d));
        }
        return 0.36d + (0.24d * Math.min(1d, statePosition));
    }

    private static double infrastructureRelevanceMultiplier(StrategicRelevance relevance) {
        double adjustment = contextualRelevanceAdjustment(relevance);
        return Math.max(0.45d, Math.min(1.25d, 1d + adjustment));
    }

    private static double normalizedDifference(double own, double enemy, double floor) {
        double denominator = Math.max(floor, Math.max(Math.abs(own), Math.abs(enemy)));
        if (!(denominator > 0d)) {
            return 0d;
        }
        return clampSigned((own - enemy) / denominator);
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-1d, Math.min(1d, value));
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

    public record ActiveWarContext(
            int activeOpponents,
            double slotPressure,
            double mapPosition,
            double resistancePosition,
            double controlPosition,
            boolean outcomeRelevant
    ) {
        public static final ActiveWarContext NONE = new ActiveWarContext(0, 0d, 0d, 0d, 0d, false);

        public ActiveWarContext {
            activeOpponents = Math.max(0, activeOpponents);
            slotPressure = Math.max(0d, Math.min(1.5d, slotPressure));
            mapPosition = clampSigned(mapPosition);
            resistancePosition = clampSigned(resistancePosition);
            controlPosition = clampSigned(controlPosition);
        }

        public static ActiveWarContext basic(boolean hasActiveWars) {
            return hasActiveWars ? new ActiveWarContext(1, 1d, 0d, 0d, 0d, true) : NONE;
        }

        public static ActiveWarContext fromSlots(
                int currentOffensiveWars,
                int maxOffensiveWars,
                int currentDefensiveWars,
                int activeOpponents
        ) {
            int normalizedOffensiveWars = Math.max(0, currentOffensiveWars);
            int normalizedDefensiveWars = Math.max(0, currentDefensiveWars);
            int normalizedActiveOpponents = Math.max(
                    activeOpponents,
                    normalizedOffensiveWars + normalizedDefensiveWars
            );
            if (normalizedActiveOpponents <= 0) {
                return NONE;
            }
            double offensivePressure = maxOffensiveWars > 0
                    ? normalizedOffensiveWars / (double) maxOffensiveWars
                    : normalizedOffensiveWars > 0 ? 1d : 0d;
            double defensivePressure = normalizedDefensiveWars / (double) WarSlotRules.defensiveSlotCap();
            return new ActiveWarContext(
                    normalizedActiveOpponents,
                    Math.max(offensivePressure, defensivePressure),
                    0d,
                    0d,
                    0d,
                    true
            );
        }

        public static ActiveWarContext fromRelativeWarState(
                int activeOpponents,
                double slotPressure,
                int ownMaps,
                int enemyMaps,
                int ownResistance,
                int enemyResistance,
                int ownControls,
                int enemyControls
        ) {
            int normalizedOwnResistance = Math.max(0, ownResistance);
            int normalizedEnemyResistance = Math.max(0, enemyResistance);
            int normalizedOwnControls = Math.max(0, ownControls);
            int normalizedEnemyControls = Math.max(0, enemyControls);
            boolean outcomeRelevant = normalizedOwnResistance > 0
                    && normalizedEnemyResistance > 0
                    && (Math.max(0, ownMaps) > 0
                    || Math.max(0, enemyMaps) > 0
                    || normalizedOwnControls > 0
                    || normalizedEnemyControls < 3);
            return new ActiveWarContext(
                    activeOpponents,
                    slotPressure,
                    normalizedDifference(Math.max(0, ownMaps), Math.max(0, enemyMaps), DEFAULT_STARTING_MAPS),
                    normalizedDifference(normalizedOwnResistance, normalizedEnemyResistance, 100d),
                    normalizedDifference(normalizedOwnControls, normalizedEnemyControls, 3d),
                    outcomeRelevant
            );
        }

        public boolean hasActiveWars() {
            return activeOpponents > 0;
        }
    }

    @FunctionalInterface
    public interface UnitReader {
        int units(MilitaryUnit unit);
    }

    @FunctionalInterface
    public interface CityInfraReader {
        double infra(int cityIndex);
    }
}
