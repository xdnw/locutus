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
    private final int[] attackerCommitmentNeeds;
    private final double horizonFactor;

    private LongHorizonAssignmentScoringModel(
            float[] baseScores,
            double[] slotDenialScores,
            double[] defenderValues,
            int[] defenderPressureNeeds,
            double[] attackerValues,
            int[] attackerCommitmentNeeds,
            double horizonFactor
    ) {
        this.baseScores = baseScores;
        this.slotDenialScores = slotDenialScores;
        this.defenderValues = defenderValues;
        this.defenderPressureNeeds = defenderPressureNeeds;
        this.attackerValues = attackerValues;
        this.attackerCommitmentNeeds = attackerCommitmentNeeds;
        this.horizonFactor = horizonFactor;
    }

    static LongHorizonAssignmentScoringModel create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            double horizonFactor,
            boolean includeSlotDenial
    ) {
        float[] baseScores = baseScores(edges);
        double[] slotDenialScores = includeSlotDenial ? slotDenialScores(edges, scenario) : new double[edges.edgeCount()];
        return new LongHorizonAssignmentScoringModel(
                baseScores,
                slotDenialScores,
                defenderValues(edges, baseScores, slotDenialScores, scenario.defenderCount()),
                defenderPressureNeeds(scenario, edges, defenderCaps),
                attackerValues(edges, baseScores, slotDenialScores, scenario.attackerCount()),
                attackerCommitmentNeeds(edges, baseScores, slotDenialScores, attackerCaps, horizonTurns),
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
        score += counterOpportunityModel.counterOpportunityScore(attackerCounts, attackerCaps);
        return score;
    }

    double edgeScore(int edgeIndex) {
        return baseScores[edgeIndex] + slotDenialScores[edgeIndex];
    }

    double attackerCommitmentMarginalScore(int attackerIndex, int assignedBefore) {
        int commitmentNeed = attackerCommitmentNeeds[attackerIndex];
        if (commitmentNeed <= 0 || assignedBefore < 0 || assignedBefore >= commitmentNeed) {
            return 0d;
        }
        return horizonFactor * ATTACKER_COMMITMENT_SCORE_WEIGHT * attackerValues[attackerIndex] / commitmentNeed;
    }

    double defenderPressureMarginalScore(int defenderIndex, int assignedBefore) {
        int pressureNeed = defenderPressureNeeds[defenderIndex];
        if (pressureNeed <= 0 || assignedBefore < 0 || assignedBefore >= pressureNeed) {
            return 0d;
        }
        return horizonFactor * LongHorizonAssignmentOptimizer.PRESSURE_SCORE_WEIGHT * defenderValues[defenderIndex] / pressureNeed;
    }

    private double pressureCompletionScore(int[] defenderCounts) {
        double score = 0d;
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            int assignedCount = defenderCounts[defenderIndex];
            int pressureNeed = defenderPressureNeeds[defenderIndex];
            if (assignedCount <= 0 || pressureNeed <= 0) {
                continue;
            }
            double usefulCount = Math.min(assignedCount, pressureNeed);
            double completion = usefulCount / pressureNeed;
            score += horizonFactor * LongHorizonAssignmentOptimizer.PRESSURE_SCORE_WEIGHT * defenderValues[defenderIndex] * completion;
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
            double usefulCount = Math.min(attackerCounts[attackerIndex], commitmentNeed);
            double completion = usefulCount / commitmentNeed;
            score += horizonFactor * ATTACKER_COMMITMENT_SCORE_WEIGHT * attackerValues[attackerIndex] * completion;
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
            double attackerCost = StrategicAssetValue.offensiveWarSlotOpportunityCost(
                    PlannerStrategicValue.localStrategicValue(attacker),
                    attackerPressure,
                    attackerSlotPressure,
                    attackerOpponents
            );
            double defenderDenial = StrategicAssetValue.defensiveWarSlotDenialValue(
                    PlannerStrategicValue.localStrategicValue(defender),
                    defenderPressure,
                    defenderSlotPressure,
                    defenderOpponents
            );
            scores[edgeIndex] = defenderDenial - attackerCost;
        }
        return scores;
    }

    private static double controlPressure(DBNationSnapshot snapshot) {
        double ground = UnitEconomy.groundStrengthRaw(
                snapshot.unit(MilitaryUnit.SOLDIER),
                snapshot.unit(MilitaryUnit.TANK),
                true,
                false
        );
        return OpeningMetricSummary.defenderControlPressure(
                ground,
                snapshot.unit(MilitaryUnit.AIRCRAFT),
                snapshot.unit(MilitaryUnit.SHIP)
        );
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
