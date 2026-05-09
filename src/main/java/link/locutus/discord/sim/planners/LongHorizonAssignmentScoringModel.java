package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

final class LongHorizonAssignmentScoringModel {
    private static final double ATTACKER_COMMITMENT_SCORE_WEIGHT = 0.14d;

    private final float[] baseScores;
    private final double[] slotDenialScores;
    private final double[] defenderValues;
    private final int[] defenderPressureNeeds;
    private final double[] attackerValues;
    private final int[] attackerBaselineOffensiveWars;
    private final int[] attackerCommitmentNeeds;
    private final double[] attackerIdlePressureScores;
    private final double horizonFactor;

    private LongHorizonAssignmentScoringModel(
            float[] baseScores,
            double[] slotDenialScores,
            double[] defenderValues,
            int[] defenderPressureNeeds,
            double[] attackerValues,
                int[] attackerBaselineOffensiveWars,
            int[] attackerCommitmentNeeds,
            double[] attackerIdlePressureScores,
            double horizonFactor
    ) {
        this.baseScores = baseScores;
        this.slotDenialScores = slotDenialScores;
        this.defenderValues = defenderValues;
        this.defenderPressureNeeds = defenderPressureNeeds;
        this.attackerValues = attackerValues;
        this.attackerBaselineOffensiveWars = attackerBaselineOffensiveWars;
        this.attackerCommitmentNeeds = attackerCommitmentNeeds;
        this.attackerIdlePressureScores = attackerIdlePressureScores;
        this.horizonFactor = horizonFactor;
    }

    static LongHorizonAssignmentScoringModel create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial,
            SidePlannerSettings attackerPlannerSettings
    ) {
        float[] baseScores = baseScores(edges);
        double[] slotDenialScores = includeSlotDenial ? slotDenialScores(edges, scenario) : new double[edges.edgeCount()];
        double[] attackerValues = attackerValues(edges, baseScores, slotDenialScores, scenario.attackerCount());
        int[] attackerCommitmentNeeds = attackerCommitmentNeeds(edges, baseScores, slotDenialScores, attackerCaps, horizonTurns);
        int[] attackerBaselineOffensiveWars = attackerBaselineOffensiveWars(scenario);
        return new LongHorizonAssignmentScoringModel(
                baseScores,
                slotDenialScores,
                defenderValues(edges, baseScores, slotDenialScores, scenario.defenderCount()),
                defenderPressureNeeds(scenario, edges, defenderCaps),
            attackerValues,
            attackerBaselineOffensiveWars,
            attackerCommitmentNeeds,
            attackerIdlePressureScores(
                attackerValues,
                attackerBaselineOffensiveWars,
                attackerCommitmentNeeds,
                attackerStrengthRanks,
                attackerPlannerSettings,
                horizonFactor
            ),
                horizonFactor
        );
    }

    double assignmentScoreDense(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            LongHorizonCounterOpportunityModel counterOpportunityModel,
            int[] attackerCaps
    ) {
        double score = 0d;
        for (int edgeIndex = 0; edgeIndex < baseScores.length; edgeIndex++) {
            if (edgeAssigned[edgeIndex]) {
                score += edgeScore(edgeIndex);
            }
        }
        score += pressureCompletionScore(defenderCounts);
        score += commitmentCompletionScore(attackerCounts);
        score += idlePressureCompletionScore(attackerCounts);
        score += counterOpportunityModel.counterOpportunityScore(attackerCounts, attackerCaps);
        return score;
    }

    double edgeScore(int edgeIndex) {
        return baseScores[edgeIndex] + slotDenialScores[edgeIndex];
    }

    double attackerCommitmentMarginalScore(int attackerIndex, int assignedBefore) {
        int commitmentNeed = attackerCommitmentNeeds[attackerIndex];
        int totalBefore = attackerBaselineOffensiveWars[attackerIndex] + assignedBefore;
        if (commitmentNeed <= 0 || assignedBefore < 0 || totalBefore >= commitmentNeed) {
            return 0d;
        }
        return horizonFactor * ATTACKER_COMMITMENT_SCORE_WEIGHT * attackerValues[attackerIndex] / commitmentNeed;
    }

    double attackerIdlePressureMarginalScore(int attackerIndex) {
        if (attackerIndex < 0 || attackerIndex >= attackerIdlePressureScores.length) {
            return 0d;
        }
        if (attackerBaselineOffensiveWars[attackerIndex] > 0) {
            return 0d;
        }
        return attackerIdlePressureScores[attackerIndex];
    }

    double defenderPressureMarginalScore(int defenderIndex, int assignedBefore) {
        int pressureNeed = defenderPressureNeeds[defenderIndex];
        if (pressureNeed <= 0 || assignedBefore < 0 || assignedBefore >= pressureNeed) {
            return 0d;
        }
        return horizonFactor
                * LongHorizonAssignmentOptimizer.PRESSURE_SCORE_WEIGHT
                * defenderValues[defenderIndex]
                * defenderPressureSlotWeight(pressureNeed, assignedBefore);
    }

    private double pressureCompletionScore(int[] defenderCounts) {
        double score = 0d;
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            int assignedCount = defenderCounts[defenderIndex];
            int pressureNeed = defenderPressureNeeds[defenderIndex];
            if (assignedCount <= 0 || pressureNeed <= 0) {
                continue;
            }
            score += horizonFactor
                    * LongHorizonAssignmentOptimizer.PRESSURE_SCORE_WEIGHT
                    * defenderValues[defenderIndex]
                    * defenderPressureCompletionWeight(pressureNeed, assignedCount);
        }
        return score;
    }

    private double commitmentCompletionScore(int[] attackerCounts) {
        double score = 0d;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            int commitmentNeed = attackerCommitmentNeeds[attackerIndex];
            if (commitmentNeed <= 0 || attackerCounts[attackerIndex] <= 0) {
                continue;
            }
            double usefulCount = Math.min(attackerBaselineOffensiveWars[attackerIndex] + attackerCounts[attackerIndex], commitmentNeed);
            double completion = usefulCount / commitmentNeed;
            score += horizonFactor * ATTACKER_COMMITMENT_SCORE_WEIGHT * attackerValues[attackerIndex] * completion;
        }
        return score;
    }

    private double idlePressureCompletionScore(int[] attackerCounts) {
        double score = 0d;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] <= 0 || attackerBaselineOffensiveWars[attackerIndex] > 0) {
                continue;
            }
            score += attackerIdlePressureScores[attackerIndex];
        }
        return score;
    }

    private static float[] baseScores(CandidateEdgeTable edges) {
        float[] scores = new float[edges.edgeCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            scores[edgeIndex] = edges.scalarScore(edgeIndex);
        }
        return scores;
    }

    private static double[] defenderValues(CandidateEdgeTable edges, float[] baseScores, double[] slotDenialScores, int defenderCount) {
        double[] values = new double[defenderCount];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int defenderIndex = edges.defenderIndex(edgeIndex);
            values[defenderIndex] = Math.max(values[defenderIndex], edgeValue(edges, baseScores, slotDenialScores, edgeIndex));
        }
        return values;
    }

    private static double[] attackerValues(CandidateEdgeTable edges, float[] baseScores, double[] slotDenialScores, int attackerCount) {
        double[] values = new double[attackerCount];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            values[attackerIndex] = Math.max(values[attackerIndex], edgeValue(edges, baseScores, slotDenialScores, edgeIndex));
        }
        return values;
    }

    private static double edgeValue(CandidateEdgeTable edges, float[] baseScores, double[] slotDenialScores, int edgeIndex) {
        double value = Math.max(0d, baseScores[edgeIndex] + slotDenialScores[edgeIndex]);
        if (edges.retainsImmediateHarm()) {
            value = Math.max(value, Math.max(0d, edges.immediateHarm(edgeIndex)));
        }
        if (edges.retainsControlLeverage()) {
            value = Math.max(value, Math.max(0d, edges.controlLeverage(edgeIndex)));
        }
        if (edges.retainsFutureWarLeverage()) {
            value = Math.max(value, Math.max(0d, edges.futureWarLeverage(edgeIndex)));
        }
        return value;
    }

    private static double[] slotDenialScores(CandidateEdgeTable edges, CompiledScenario scenario) {
        double[] scores = new double[edges.edgeCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            DBNationSnapshot attacker = scenario.attacker(edges.attackerIndex(edgeIndex));
            DBNationSnapshot defender = scenario.defender(edges.defenderIndex(edgeIndex));
            double attackerPressure = controlPressure(attacker);
            double defenderPressure = controlPressure(defender);
            double attackerSlotPressure = (attacker.currentOffensiveWars() + 1d) / Math.max(1, attacker.maxOff());
            double defenderSlotPressure = (defender.currentDefensiveWars() + 1d) / WarSlotRules.defensiveSlotCap();
            int attackerOpponents = Math.max(
                    attacker.activeOpponentNationIds().size() + 1,
                    attacker.currentOffensiveWars() + attacker.currentDefensiveWars() + 1
            );
            int defenderOpponents = Math.max(
                    defender.activeOpponentNationIds().size() + 1,
                    defender.currentOffensiveWars() + defender.currentDefensiveWars() + 1
            );
                StrategicCapabilityVector attackerCapability = PlannerStrategicValue.capabilityVector(attacker);
                StrategicCapabilityVector defenderCapability = PlannerStrategicValue.capabilityVector(defender);
            double attackerCost = StrategicAssetValue.offensiveWarSlotOpportunityCost(
                    PlannerStrategicValue.offensiveSlotCapabilityValue(attackerCapability, attackerSlotPressure),
                    attackerPressure,
                    attackerSlotPressure,
                    attackerOpponents
            );
            double defenderDenial = StrategicAssetValue.defensiveWarSlotDenialValue(
                    PlannerStrategicValue.defensiveSlotCapabilityValue(defenderCapability, defenderSlotPressure),
                    defenderPressure,
                    defenderSlotPressure,
                    defenderOpponents
            );
            scores[edgeIndex] = defenderDenial - attackerCost;
        }
        return scores;
    }

    private static double controlPressure(DBNationSnapshot snapshot) {
        return OpeningMetricSummary.defenderControlPressure(snapshot);
    }

    private static int[] defenderPressureNeeds(CompiledScenario scenario, CandidateEdgeTable edges, int[] defenderCaps) {
        double[] strongestCandidateAttacker = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            strongestCandidateAttacker[defenderIndex] = Math.max(
                    strongestCandidateAttacker[defenderIndex],
                    combatStrength(scenario.attacker(attackerIndex))
            );
        }

        int[] pressureNeeds = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < pressureNeeds.length; defenderIndex++) {
            int defenderCap = Math.max(1, defenderCaps[defenderIndex]);
            double attackerStrength = strongestCandidateAttacker[defenderIndex];
            if (!(attackerStrength > 0d) || defenderCap <= 1) {
                pressureNeeds[defenderIndex] = 1;
                continue;
            }
            double strengthRatio = combatStrength(scenario.defender(defenderIndex)) / attackerStrength;
            int estimatedNeed;
            if (strengthRatio >= 2.25d) {
                estimatedNeed = 3;
            } else if (strengthRatio >= 1.15d) {
                estimatedNeed = 2;
            } else {
                estimatedNeed = 1;
            }
            pressureNeeds[defenderIndex] = Math.max(1, Math.min(defenderCap, estimatedNeed));
        }
        return pressureNeeds;
    }

    private static double defenderPressureCompletionWeight(int pressureNeed, int assignedCount) {
        int usefulCount = Math.max(0, Math.min(assignedCount, pressureNeed));
        double weight = 0d;
        for (int slot = 0; slot < usefulCount; slot++) {
            weight += defenderPressureSlotWeight(pressureNeed, slot);
        }
        return weight;
    }

    private static double defenderPressureSlotWeight(int pressureNeed, int assignedBefore) {
        if (assignedBefore < 0 || assignedBefore >= pressureNeed) {
            return 0d;
        }
        if (pressureNeed <= 1) {
            return 1d;
        }
        if (pressureNeed == 2) {
            return assignedBefore == 0 ? 1d : 0.45d;
        }
        return switch (assignedBefore) {
            case 0 -> 1d;
            case 1 -> 0.55d;
            case 2 -> 0.25d;
            default -> 0.10d;
        };
    }

    private static int[] attackerCommitmentNeeds(CandidateEdgeTable edges, float[] baseScores, double[] slotDenialScores, int[] attackerCaps, int horizonTurns) {
        int[] positiveEdgeCounts = new int[attackerCaps.length];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (baseScores[edgeIndex] + slotDenialScores[edgeIndex] > 0d) {
                positiveEdgeCounts[edges.attackerIndex(edgeIndex)]++;
            }
        }
        int horizonCommitmentLimit = horizonCommitmentLimit(horizonTurns);
        int[] commitmentNeeds = new int[attackerCaps.length];
        for (int attackerIndex = 0; attackerIndex < commitmentNeeds.length; attackerIndex++) {
            int usefulCapacity = Math.min(attackerCaps[attackerIndex], positiveEdgeCounts[attackerIndex]);
            commitmentNeeds[attackerIndex] = Math.max(0, Math.min(usefulCapacity, horizonCommitmentLimit));
        }
        return commitmentNeeds;
    }

        private static double[] attackerIdlePressureScores(
            double[] attackerValues,
            int[] attackerBaselineOffensiveWars,
            int[] attackerCommitmentNeeds,
            int[] attackerStrengthRanks,
            SidePlannerSettings attackerPlannerSettings,
            double horizonFactor
    ) {
        double weight = attackerPlannerSettings == null ? 0d : attackerPlannerSettings.idlePressureWeight();
        double[] scores = new double[attackerValues.length];
        if (!(weight > 0d)) {
            return scores;
        }
        int attackerCount = Math.max(1, attackerValues.length);
        for (int attackerIndex = 0; attackerIndex < scores.length; attackerIndex++) {
            if (attackerBaselineOffensiveWars[attackerIndex] > 0
                    || attackerCommitmentNeeds[attackerIndex] <= 0
                    || !(attackerValues[attackerIndex] > 0d)) {
                continue;
            }
            int rank = attackerStrengthRanks != null && attackerIndex < attackerStrengthRanks.length
                    ? Math.max(0, attackerStrengthRanks[attackerIndex])
                    : attackerCount - 1;
            double rankScale = Math.max(1d / attackerCount, Math.min(1d, (attackerCount - rank) / (double) attackerCount));
            scores[attackerIndex] = horizonFactor * weight * attackerValues[attackerIndex] * rankScale;
        }
        return scores;
    }

    private static int[] attackerBaselineOffensiveWars(CompiledScenario scenario) {
        int[] baseline = new int[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < baseline.length; attackerIndex++) {
            baseline[attackerIndex] = Math.max(
                    0,
                    scenario.attacker(attackerIndex).maxOff() - Math.max(0, scenario.attackerFreeOffSlots(attackerIndex))
            );
        }
        return baseline;
    }

    private static int horizonCommitmentLimit(int horizonTurns) {
        if (horizonTurns >= 360) {
            return 3;
        }
        if (horizonTurns >= 72) {
            return 2;
        }
        return 1;
    }

    private static double combatStrength(DBNationSnapshot snapshot) {
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
