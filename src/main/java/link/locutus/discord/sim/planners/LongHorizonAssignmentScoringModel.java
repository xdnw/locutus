package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

final class LongHorizonAssignmentScoringModel {
    private static final double ATTACKER_COMMITMENT_SCORE_WEIGHT = 0.14d;
    private static final double ATTACKER_OVERCOMMITMENT_SCORE_WEIGHT = 0.65d;

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
        double[] edgeScores = edgeScores(baseScores, slotDenialScores);
        double[] attackerValues = attackerValues(edges, baseScores, slotDenialScores, scenario.attackerCount());
        int[] attackerCommitmentNeeds = LongHorizonOpeningCommitmentModel.attackerCommitmentNeeds(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                edgeScores
        );
        int[] attackerBaselineOffensiveWars = attackerBaselineOffensiveWars(scenario);
        return new LongHorizonAssignmentScoringModel(
                baseScores,
                slotDenialScores,
                defenderValues(edges, baseScores, slotDenialScores, scenario),
                LongHorizonOpeningCommitmentModel.defenderPressureNeeds(scenario, edges, defenderCaps),
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

            LongHorizonAssignmentScoringModel sameTopologyVariant(
                CandidateEdgeTable edges,
                CompiledScenario scenario,
                int[] attackerCaps,
                int[] defenderCaps,
                int[] attackerStrengthRanks,
                int horizonTurns,
                SidePlannerSettings attackerPlannerSettings
            ) {
            float[] baseScores = baseScores(edges);
            double[] edgeScores = edgeScores(baseScores, slotDenialScores);
            double[] attackerValues = attackerValues(edges, baseScores, slotDenialScores, scenario.attackerCount());
            int[] attackerCommitmentNeeds = LongHorizonOpeningCommitmentModel.attackerCommitmentNeeds(
                    edges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    edgeScores
            );
            int[] attackerBaselineOffensiveWars = this.attackerBaselineOffensiveWars;
            return new LongHorizonAssignmentScoringModel(
                baseScores,
                slotDenialScores,
                defenderValues(edges, baseScores, slotDenialScores, scenario),
                LongHorizonOpeningCommitmentModel.defenderPressureNeeds(scenario, edges, defenderCaps),
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

    LongHorizonAssignmentScoringModel sameTopologyRescaledAttackerVariant(
            CandidateEdgeTable edges,
            int[] attackerCaps,
            int[] attackerStrengthRanks,
            int horizonTurns,
            SidePlannerSettings attackerPlannerSettings,
            IntArrayList touchedAttackers
    ) {
        if (touchedAttackers == null || touchedAttackers.isEmpty()) {
            return this;
        }
        float[] baseScores = this.baseScores.clone();
        double[] attackerValues = this.attackerValues.clone();
        int[] attackerCommitmentNeeds = this.attackerCommitmentNeeds.clone();
        double[] attackerIdlePressureScores = this.attackerIdlePressureScores.clone();
        double[] defenderValues = this.defenderValues.clone();

        boolean[] touchedAttackerFlags = new boolean[attackerValues.length];
        double[] refreshedAttackerValues = new double[attackerValues.length];
        boolean[] touchedDefenderFlags = new boolean[defenderValues.length];
        IntArrayList touchedDefenders = new IntArrayList();
        for (int index = 0; index < touchedAttackers.size(); index++) {
            int attackerIndex = touchedAttackers.getInt(index);
            if (attackerIndex >= 0 && attackerIndex < touchedAttackerFlags.length) {
                touchedAttackerFlags[attackerIndex] = true;
            }
        }
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            if (!touchedAttackerFlags[attackerIndex]) {
                continue;
            }
            baseScores[edgeIndex] = edges.scalarScore(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            if (!touchedDefenderFlags[defenderIndex]) {
                touchedDefenderFlags[defenderIndex] = true;
                touchedDefenders.add(defenderIndex);
            }
            double edgeValue = edgeValue(edges, baseScores, slotDenialScores, edgeIndex);
            refreshedAttackerValues[attackerIndex] = Math.max(refreshedAttackerValues[attackerIndex], edgeValue);
        }
        for (int index = 0; index < touchedDefenders.size(); index++) {
            defenderValues[touchedDefenders.getInt(index)] = 0d;
        }
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int defenderIndex = edges.defenderIndex(edgeIndex);
            if (!touchedDefenderFlags[defenderIndex]) {
                continue;
            }
            defenderValues[defenderIndex] = Math.max(defenderValues[defenderIndex], edgeValue(edges, baseScores, slotDenialScores, edgeIndex));
        }
        for (int index = 0; index < touchedAttackers.size(); index++) {
            int attackerIndex = touchedAttackers.getInt(index);
            attackerValues[attackerIndex] = refreshedAttackerValues[attackerIndex];
            attackerIdlePressureScores[attackerIndex] = attackerIdlePressureScore(
                    attackerIndex,
                    attackerValues,
                    attackerBaselineOffensiveWars,
                    attackerCommitmentNeeds,
                    attackerStrengthRanks,
                    attackerPlannerSettings,
                    horizonFactor
            );
        }
        return new LongHorizonAssignmentScoringModel(
                baseScores,
                slotDenialScores,
                defenderValues,
                defenderPressureNeeds,
                attackerValues,
                attackerBaselineOffensiveWars,
                attackerCommitmentNeeds,
                attackerIdlePressureScores,
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
        if (assignedBefore < 0) {
            return 0d;
        }
        if (commitmentNeed <= 0) {
            return -attackerOvercommitmentMarginalPenalty(attackerIndex, totalBefore);
        }
        if (totalBefore >= commitmentNeed) {
            return -attackerOvercommitmentMarginalPenalty(attackerIndex, totalBefore - commitmentNeed);
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
            if (attackerCounts[attackerIndex] <= 0) {
                continue;
            }
            if (commitmentNeed > 0) {
                double usefulCount = Math.min(attackerBaselineOffensiveWars[attackerIndex] + attackerCounts[attackerIndex], commitmentNeed);
                double completion = usefulCount / commitmentNeed;
                score += horizonFactor * ATTACKER_COMMITMENT_SCORE_WEIGHT * attackerValues[attackerIndex] * completion;
            }
            score -= attackerOvercommitmentPenalty(attackerIndex, attackerCounts[attackerIndex], commitmentNeed);
        }
        return score;
    }

    private double attackerOvercommitmentPenalty(int attackerIndex, int assignedCount, int commitmentNeed) {
        int baselineOver = Math.max(0, attackerBaselineOffensiveWars[attackerIndex] - commitmentNeed);
        int totalOver = Math.max(0, attackerBaselineOffensiveWars[attackerIndex] + assignedCount - commitmentNeed);
        int addedOver = totalOver - baselineOver;
        if (addedOver <= 0) {
            return 0d;
        }
        double penalty = 0d;
        for (int overSlot = baselineOver; overSlot < baselineOver + addedOver; overSlot++) {
            penalty += attackerOvercommitmentMarginalPenalty(attackerIndex, overSlot);
        }
        return penalty;
    }

    private double attackerOvercommitmentMarginalPenalty(int attackerIndex, int overSlot) {
        return horizonFactor
                * ATTACKER_OVERCOMMITMENT_SCORE_WEIGHT
                * attackerValues[attackerIndex]
                * attackerOvercommitmentSlotWeight(overSlot);
    }

    private static double attackerOvercommitmentSlotWeight(int overSlot) {
        return switch (Math.max(0, overSlot)) {
            case 0 -> 0.60d;
            case 1 -> 0.85d;
            case 2 -> 1.15d;
            default -> 1.50d;
        };
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

    private static double[] edgeScores(float[] baseScores, double[] slotDenialScores) {
        double[] scores = new double[baseScores.length];
        for (int index = 0; index < scores.length; index++) {
            scores[index] = baseScores[index] + slotDenialScores[index];
        }
        return scores;
    }

    private static double[] defenderValues(CandidateEdgeTable edges, float[] baseScores, double[] slotDenialScores, CompiledScenario scenario) {
        double[] values = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int defenderIndex = edges.defenderIndex(edgeIndex);
            values[defenderIndex] = Math.max(values[defenderIndex], edgeValue(edges, baseScores, slotDenialScores, edgeIndex));
        }
        for (int defenderIndex = 0; defenderIndex < values.length; defenderIndex++) {
            values[defenderIndex] = Math.max(values[defenderIndex], strategicCoverageValue(scenario.defender(defenderIndex)));
        }
        return values;
    }

    private static double strategicCoverageValue(DBNationSnapshot defender) {
        double tierWeight = Math.max(0d, Math.min(1d, (defender.cities() - 35d) / 10d));
        if (!(tierWeight > 0d)) {
            return 0d;
        }
        return tierWeight * (OpeningMetricSummary.defenderControlPressure(defender) + (35d * defender.cities()));
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
        double[] attackerPressures = new double[scenario.attackerCount()];
        double[] attackerSlotCapabilityValues = new double[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            DBNationSnapshot attacker = scenario.attacker(attackerIndex);
            attackerPressures[attackerIndex] = controlPressure(attacker);
            attackerSlotCapabilityValues[attackerIndex] = PlannerStrategicValue.slotCapabilityValue(attacker);
        }
        double[] defenderPressures = new double[scenario.defenderCount()];
        double[] defenderSlotCapabilityValues = new double[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            DBNationSnapshot defender = scenario.defender(defenderIndex);
            defenderPressures[defenderIndex] = controlPressure(defender);
            defenderSlotCapabilityValues[defenderIndex] = PlannerStrategicValue.slotCapabilityValue(defender);
        }
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            DBNationSnapshot attacker = scenario.attacker(attackerIndex);
            DBNationSnapshot defender = scenario.defender(defenderIndex);
            double attackerPressure = attackerPressures[attackerIndex];
            double defenderPressure = defenderPressures[defenderIndex];
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
                    PlannerStrategicValue.offensiveSlotCapabilityValue(attackerSlotCapabilityValues[attackerIndex], attackerSlotPressure),
                    attackerPressure,
                    attackerSlotPressure,
                    attackerOpponents
            );
            double defenderDenial = StrategicAssetValue.defensiveWarSlotDenialValue(
                    PlannerStrategicValue.defensiveSlotCapabilityValue(defenderSlotCapabilityValues[defenderIndex], defenderSlotPressure),
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
            scores[attackerIndex] = attackerIdlePressureScore(
                attackerIndex,
                attackerValues,
                attackerBaselineOffensiveWars,
                attackerCommitmentNeeds,
                attackerStrengthRanks,
                attackerPlannerSettings,
                horizonFactor,
                weight,
                attackerCount
            );
        }
        return scores;
    }

        private static double attackerIdlePressureScore(
            int attackerIndex,
            double[] attackerValues,
            int[] attackerBaselineOffensiveWars,
            int[] attackerCommitmentNeeds,
            int[] attackerStrengthRanks,
            SidePlannerSettings attackerPlannerSettings,
            double horizonFactor
        ) {
        double weight = attackerPlannerSettings == null ? 0d : attackerPlannerSettings.idlePressureWeight();
        int attackerCount = Math.max(1, attackerValues.length);
        return attackerIdlePressureScore(
            attackerIndex,
            attackerValues,
            attackerBaselineOffensiveWars,
            attackerCommitmentNeeds,
            attackerStrengthRanks,
            attackerPlannerSettings,
            horizonFactor,
            weight,
            attackerCount
        );
        }

        private static double attackerIdlePressureScore(
            int attackerIndex,
            double[] attackerValues,
            int[] attackerBaselineOffensiveWars,
            int[] attackerCommitmentNeeds,
            int[] attackerStrengthRanks,
            SidePlannerSettings attackerPlannerSettings,
            double horizonFactor,
            double weight,
            int attackerCount
        ) {
        if (!(weight > 0d)
            || attackerBaselineOffensiveWars[attackerIndex] > 0
            || attackerCommitmentNeeds[attackerIndex] <= 0
            || !(attackerValues[attackerIndex] > 0d)) {
            return 0d;
        }
        int rank = attackerStrengthRanks != null && attackerIndex < attackerStrengthRanks.length
            ? Math.max(0, attackerStrengthRanks[attackerIndex])
            : attackerCount - 1;
        double rankScale = Math.max(1d / attackerCount, Math.min(1d, (attackerCount - rank) / (double) attackerCount));
        return horizonFactor * weight * attackerValues[attackerIndex] * rankScale;
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

}
