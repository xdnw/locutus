package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.util.PW;

import java.util.Arrays;

final class LongHorizonCounterOpportunityModel {
    private static final double COUNTER_OPPORTUNITY_COST_WEIGHT = 0.32d;

    private final double horizonFactor;
    private final double[] attackerCounterPenaltyScales;

    private LongHorizonCounterOpportunityModel(
            double horizonFactor,
            double[] attackerCounterPenaltyScales
    ) {
        this.horizonFactor = horizonFactor;
        this.attackerCounterPenaltyScales = attackerCounterPenaltyScales;
    }

    static LongHorizonCounterOpportunityModel create(
            CompiledScenario scenario,
            double[] attackerInitialScores,
            double[] attackerProjectedBuyScore,
            double[] attackerCombatStrengths,
            double[] defenderCombatStrengths,
            double horizonFactor
    ) {
        return new LongHorizonCounterOpportunityModel(
                horizonFactor,
                counterPenaltyScales(
                        attackerInitialScores,
                        attackerProjectedBuyScore,
                        attackerCombatStrengths,
                        counterPressures(scenario, attackerCombatStrengths, defenderCombatStrengths)
                )
        );
    }

    double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore, int[] attackerCaps) {
        if (attackerIndex < 0 || attackerIndex >= attackerCounterPenaltyScales.length || assignedBefore < 0) {
            return 0d;
        }
        double scale = attackerCounterPenaltyScales[attackerIndex];
        if (!(scale > 0d)) {
            return 0d;
        }
        int usefulCapacity = Math.max(1, Math.min(3, attackerCaps[attackerIndex]));
        double slotPressure = Math.min(1.75d, (assignedBefore + 1d) / usefulCapacity);
        return -horizonFactor * COUNTER_OPPORTUNITY_COST_WEIGHT * scale * slotPressure;
    }

    double counterOpportunityScore(int[] attackerCounts, int[] attackerCaps) {
        double score = 0d;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            for (int assignedBefore = 0; assignedBefore < attackerCounts[attackerIndex]; assignedBefore++) {
                score += attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore, attackerCaps);
            }
        }
        return score;
    }

    private static double[] counterPressures(
            CompiledScenario scenario,
            double[] attackerCombatStrengths,
            double[] defenderCombatStrengths
    ) {
        double[] pressures = new double[scenario.attackerCount()];
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            DBNationSnapshot counterDeclarer = scenario.defender(defenderIndex);
            int counterSlots = Math.max(0, counterDeclarer.rawFreeOff());
            if (counterSlots == 0 || defenderCombatStrengths[defenderIndex] <= 0d) {
                continue;
            }
            double[] bestScores = new double[Math.min(counterSlots, Math.max(1, scenario.attackerCount()))];
            int[] bestAttackerIndexes = new int[bestScores.length];
            Arrays.fill(bestAttackerIndexes, -1);
            for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                if (!canCounter(counterDeclarer, scenario.attacker(attackerIndex))) {
                    continue;
                }
                double pressure = counterPressure(
                        counterDeclarer,
                        scenario.attacker(attackerIndex),
                        defenderCombatStrengths[defenderIndex],
                        attackerCombatStrengths[attackerIndex],
                        scenario.defenderActivityWeight(defenderIndex)
                );
                insertBest(bestScores, bestAttackerIndexes, pressure, attackerIndex);
            }
            for (int index = 0; index < bestScores.length; index++) {
                double pressure = bestScores[index];
                if (!(pressure > 0d)) {
                    continue;
                }
                int bestAttackerIndex = bestAttackerIndexes[index];
                if (bestAttackerIndex >= 0) {
                    pressures[bestAttackerIndex] += pressure;
                }
            }
        }
        return pressures;
    }

    private static void insertBest(double[] bestScores, int[] bestAttackerIndexes, double pressure, int attackerIndex) {
        if (!(pressure > 0d)) {
            return;
        }
        for (int index = 0; index < bestScores.length; index++) {
            if (pressure > bestScores[index]) {
                for (int shift = bestScores.length - 1; shift > index; shift--) {
                    bestScores[shift] = bestScores[shift - 1];
                    bestAttackerIndexes[shift] = bestAttackerIndexes[shift - 1];
                }
                bestScores[index] = pressure;
                bestAttackerIndexes[index] = attackerIndex;
                return;
            }
        }
    }

    private static double counterPressure(
            DBNationSnapshot counterDeclarer,
            DBNationSnapshot target,
            double counterStrength,
            double targetStrength,
            float activityWeight
    ) {
        double targetDefSlots = Math.max(1, WarSlotRules.freeDefensiveSlots(target.currentDefensiveWars()));
        double strengthRatio = counterStrength / Math.max(1d, targetStrength);
        double scoreValue = Math.max(50d, PlannerStrategicValue.marginalActionSpaceValue(target) * 0.10d);
        double activity = Math.max(0d, Math.min(1d, activityWeight));
        return activity * scoreValue * Math.min(2.0d, strengthRatio) / targetDefSlots;
    }

    private static boolean canCounter(DBNationSnapshot counterDeclarer, DBNationSnapshot target) {
        if (counterDeclarer.nationId() == target.nationId()) {
            return false;
        }
        double minScore = counterDeclarer.score() * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = counterDeclarer.score() * PW.WAR_RANGE_MAX_MODIFIER;
        return target.score() >= minScore && target.score() <= maxScore;
    }

    private static double[] counterPenaltyScales(
            double[] attackerInitialScores,
            double[] attackerProjectedBuyScore,
            double[] attackerCombatStrengths,
            double[] attackerCounterPressures
    ) {
        double[] scales = new double[attackerInitialScores.length];
        for (int attackerIndex = 0; attackerIndex < scales.length; attackerIndex++) {
            double resilience = Math.sqrt(Math.max(1d, attackerCombatStrengths[attackerIndex]))
                    + Math.sqrt(Math.max(0d, attackerProjectedBuyScore[attackerIndex]));
            double scoreValue = Math.max(50d, attackerInitialScores[attackerIndex] * 0.10d);
            double normalizedPressure = attackerCounterPressures[attackerIndex] / Math.max(1d, resilience / 20d);
            scales[attackerIndex] = Math.min(scoreValue, normalizedPressure);
        }
        return scales;
    }

    static double combatStrength(DBNationSnapshot snapshot) {
        double groundStrength = UnitEconomy.groundStrengthRaw(
                snapshot.unit(MilitaryUnit.SOLDIER),
                snapshot.unit(MilitaryUnit.TANK),
                false,
                false
        );
        return groundStrength
                + (3d * snapshot.unit(MilitaryUnit.AIRCRAFT))
                + (2d * snapshot.unit(MilitaryUnit.SHIP));
    }
}
