package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.StrategicObjective;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

final class BlitzAssignmentRefiner {
    private BlitzAssignmentRefiner() {
    }

    static Map<Integer, List<Integer>> refine(
            SimTuning tuning,
            OverrideSet overrides,
            StrategicObjective objective,
            Map<Integer, List<Integer>> assignment,
            BlitzGeneratedCandidates candidates,
            Map<Long, Integer> warTypeOrdinalsByPair,
            Map<Integer, Integer> attackerCapsByNationId,
            Map<Integer, Integer> defenderCapsByNationId,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            List<BlitzFixedEdge> fixedEdges,
            boolean excludeReciprocalPairs) {

        long budgetMs = tuning.localSearchBudgetMs();
        int maxIterations = tuning.localSearchMaxIterations();
        if (budgetMs <= 0 || maxIterations <= 0) {
            return assignment;
        }

        long deadline = System.currentTimeMillis() + budgetMs;
        PlannerAssignmentSession best = PlannerAssignmentSession.create(
            assignment,
            attackers,
            defenders,
            attackerCapsByNationId,
            defenderCapsByNationId,
            fixedEdges
        );
        int[][] candidateDefenderSlotsByAttacker = candidateDefenderSlotsByAttacker(candidates, best);
        int attackerTeamId = attackers.isEmpty() ? 1 : attackers.get(0).teamId();
        RefinementAggregates aggregates = RefinementAggregates.fromAssignment(best);
        Map<Integer, StrategicAssetValue.StrategicRelevance> relevanceByNationId =
                PlannerStrategicValue.relevanceByNationId(attackers, defenders);

        int iteration = 0;
        while (iteration < maxIterations && System.currentTimeMillis() < deadline) {
            iteration++;

            boolean improved = false;
            outer:
            for (int attackerOneSlot = 0; attackerOneSlot < best.attackerCount(); attackerOneSlot++) {
                if (!best.hasAssignments(attackerOneSlot)) {
                    continue;
                }
                for (int attackerTwoSlot = attackerOneSlot + 1; attackerTwoSlot < best.attackerCount(); attackerTwoSlot++) {
                    if (!best.hasAssignments(attackerTwoSlot)) {
                        continue;
                    }

                    for (int defenderOneIndex = 0; defenderOneIndex < best.assignedCount(attackerOneSlot); defenderOneIndex++) {
                        if (best.isLocked(attackerOneSlot, defenderOneIndex)) {
                            continue;
                        }
                        for (int defenderTwoIndex = 0; defenderTwoIndex < best.assignedCount(attackerTwoSlot); defenderTwoIndex++) {
                            if (best.isLocked(attackerTwoSlot, defenderTwoIndex)) {
                                continue;
                            }
                            int defenderOneSlot = best.defenderSlotAt(attackerOneSlot, defenderOneIndex);
                            int defenderTwoSlot = best.defenderSlotAt(attackerTwoSlot, defenderTwoIndex);
                            if (defenderOneSlot == defenderTwoSlot) {
                                continue;
                            }

                            int attackerOneId = best.attackerNationIdAt(attackerOneSlot);
                            int attackerTwoId = best.attackerNationIdAt(attackerTwoSlot);
                            int defenderOneId = best.defenderNationIdAt(defenderOneSlot);
                            int defenderTwoId = best.defenderNationIdAt(defenderTwoSlot);

                            if (!candidates.containsPair(attackerOneId, defenderTwoId)
                                    || !candidates.containsPair(attackerTwoId, defenderOneId)) {
                                continue;
                            }
                            if (excludeReciprocalPairs
                                    && (wouldCreateReciprocalPair(best, attackerOneId, defenderTwoId, attackerTwoSlot, defenderTwoIndex)
                                    || wouldCreateReciprocalPair(best, attackerTwoId, defenderOneId, attackerOneSlot, defenderOneIndex))) {
                                continue;
                            }
                            if (best.containsDefenderSlotExcept(attackerOneSlot, defenderTwoSlot, defenderOneIndex)
                                    || best.containsDefenderSlotExcept(attackerTwoSlot, defenderOneSlot, defenderTwoIndex)) {
                                continue;
                            }

                            PlannerAssignmentChange candidate = best.swapChange(
                                attackerOneSlot,
                                defenderOneIndex,
                                defenderTwoSlot,
                                attackerTwoSlot,
                                defenderTwoIndex,
                                defenderOneSlot
                            );
                            double surrogateDelta = aggregates.swapDelta(
                                best,
                                attackerOneSlot,
                                defenderOneSlot,
                                defenderTwoSlot,
                                attackerTwoSlot,
                                defenderTwoSlot,
                                defenderOneSlot,
                                candidates
                            );
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(
                                tuning,
                                overrides,
                                objective,
                                best,
                                candidate,
                                attackers,
                                defenders,
                                attackerTeamId,
                                warTypeOrdinalsByPair,
                                relevanceByNationId
                            );
                            if (exactDelta > 1e-9) {
                                best.applySwap(
                                    attackerOneSlot,
                                    defenderOneIndex,
                                    defenderTwoSlot,
                                    attackerTwoSlot,
                                    defenderTwoIndex,
                                    defenderOneSlot
                                );
                                aggregates.applySwap();
                                improved = true;
                                break outer;
                            }
                        }
                    }
                    if (System.currentTimeMillis() > deadline) {
                        break outer;
                    }
                }
            }

            if (!improved) {
                outerMove:
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    if (!best.hasAssignments(attackerSlot)) {
                        continue;
                    }
                    int[] candidateDefenderSlots = candidateDefenderSlotsByAttacker[attackerSlot];
                    for (int assignedIndex = 0; assignedIndex < best.assignedCount(attackerSlot); assignedIndex++) {
                        if (best.isLocked(attackerSlot, assignedIndex)) {
                            continue;
                        }
                        int previousDefenderSlot = best.defenderSlotAt(attackerSlot, assignedIndex);
                        for (int nextDefenderSlot : candidateDefenderSlots) {
                            if (nextDefenderSlot == previousDefenderSlot) {
                                continue;
                            }
                            if (best.containsDefenderSlotExcept(attackerSlot, nextDefenderSlot, assignedIndex)) {
                                continue;
                            }
                            if (excludeReciprocalPairs && wouldCreateReciprocalPair(
                                    best,
                                    best.attackerNationIdAt(attackerSlot),
                                    best.defenderNationIdAt(nextDefenderSlot),
                                    -1,
                                    -1)) {
                                continue;
                            }
                            if (best.defenderAssignedCount(nextDefenderSlot) >= best.defenderCap(nextDefenderSlot)) {
                                continue;
                            }

                            PlannerAssignmentChange candidate = best.moveChange(attackerSlot, assignedIndex, nextDefenderSlot);
                            double surrogateDelta = aggregates.moveDelta(best, attackerSlot, previousDefenderSlot, nextDefenderSlot, candidates);
                            if (!aggregates.isPromising(surrogateDelta)) {
                                continue;
                            }
                            double exactDelta = exactBundleDelta(
                                tuning,
                                overrides,
                                objective,
                                best,
                                candidate,
                                attackers,
                                defenders,
                                attackerTeamId,
                                warTypeOrdinalsByPair,
                                relevanceByNationId
                            );
                            if (exactDelta > 1e-9) {
                                best.applyMove(attackerSlot, assignedIndex, nextDefenderSlot);
                                aggregates.applyMove(previousDefenderSlot, nextDefenderSlot);
                                improved = true;
                                break outerMove;
                            }
                        }
                        if (System.currentTimeMillis() > deadline) {
                            break outerMove;
                        }
                    }
                }
            }

            if (!improved) {
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    if (best.assignedCount(attackerSlot) >= best.attackerCap(attackerSlot)) {
                        continue;
                    }

                    int[] candidateDefenderSlots = candidateDefenderSlotsByAttacker[attackerSlot];
                    for (int defenderSlot : candidateDefenderSlots) {
                        if (best.defenderAssignedCount(defenderSlot) >= best.defenderCap(defenderSlot)) {
                            continue;
                        }
                        if (best.containsDefenderSlot(attackerSlot, defenderSlot)) {
                            continue;
                        }
                        if (excludeReciprocalPairs && wouldCreateReciprocalPair(
                                best,
                                best.attackerNationIdAt(attackerSlot),
                                best.defenderNationIdAt(defenderSlot),
                                -1,
                                -1)) {
                            continue;
                        }

                        PlannerAssignmentChange candidate = best.addChange(attackerSlot, defenderSlot);
                        double surrogateDelta = aggregates.addDelta(best, attackerSlot, defenderSlot, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(
                            tuning,
                            overrides,
                            objective,
                            best,
                            candidate,
                            attackers,
                            defenders,
                            attackerTeamId,
                            warTypeOrdinalsByPair,
                            relevanceByNationId
                        );
                        if (exactDelta > 1e-9) {
                            best.applyAdd(attackerSlot, defenderSlot);
                            aggregates.applyAdd(attackerSlot, defenderSlot);
                            improved = true;
                            break;
                        }
                    }
                    if (improved) {
                        break;
                    }
                }
            }

            if (!improved) {
                outerDrop:
                for (int attackerSlot = 0; attackerSlot < best.attackerCount(); attackerSlot++) {
                    if (!best.hasAssignments(attackerSlot)) {
                        continue;
                    }
                    for (int assignedIndex = 0; assignedIndex < best.assignedCount(attackerSlot); assignedIndex++) {
                        if (best.isLocked(attackerSlot, assignedIndex)) {
                            continue;
                        }
                        int removedDefenderSlot = best.defenderSlotAt(attackerSlot, assignedIndex);
                        PlannerAssignmentChange candidate = best.dropChange(attackerSlot, assignedIndex);
                        double surrogateDelta = aggregates.dropDelta(best, attackerSlot, removedDefenderSlot, candidates);
                        if (!aggregates.isPromising(surrogateDelta)) {
                            continue;
                        }
                        double exactDelta = exactBundleDelta(
                            tuning,
                            overrides,
                            objective,
                            best,
                            candidate,
                            attackers,
                            defenders,
                            attackerTeamId,
                            warTypeOrdinalsByPair,
                            relevanceByNationId
                        );
                        if (exactDelta > 1e-9) {
                            best.applyDrop(attackerSlot, assignedIndex);
                            aggregates.applyDrop(attackerSlot, removedDefenderSlot);
                            improved = true;
                            break outerDrop;
                        }
                        if (System.currentTimeMillis() > deadline) {
                            break outerDrop;
                        }
                    }
                }
            }

            if (!improved) {
                break;
            }
        }
        return best.toAssignmentMap();
    }

    private static double exactBundleDelta(
            SimTuning tuning,
            OverrideSet overrides,
            StrategicObjective objective,
            PlannerAssignmentSession currentAssignment,
            PlannerAssignmentChange candidateChange,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            int attackerTeamId,
            Map<Long, Integer> warTypeOrdinalsByPair,
            Map<Integer, StrategicAssetValue.StrategicRelevance> relevanceByNationId
    ) {
        return PlannerConflictExecutor.scoreAssignmentDelta(
            tuning,
            overrides,
            objective,
            currentAssignment,
            candidateChange,
            attackers,
            defenders,
            attackerTeamId,
            warTypeOrdinalsByPair,
            relevanceByNationId
        );
    }

    private static boolean wouldCreateReciprocalPair(
            PlannerAssignmentSession assignment,
            int attackerNationId,
            int defenderNationId,
            int excludedAttackerSlot,
            int excludedAssignmentIndex
    ) {
        int reverseAttackerSlot = assignment.attackerSlot(defenderNationId);
        int reverseDefenderSlot = assignment.defenderSlot(attackerNationId);
        if (reverseAttackerSlot < 0 || reverseDefenderSlot < 0) {
            return false;
        }
        int excludedIndex = reverseAttackerSlot == excludedAttackerSlot ? excludedAssignmentIndex : -1;
        return assignment.containsDefenderSlotExcept(reverseAttackerSlot, reverseDefenderSlot, excludedIndex);
    }

    private static int[][] candidateDefenderSlotsByAttacker(BlitzGeneratedCandidates candidates, PlannerAssignmentSession assignment) {
        int[][] candidateDefenderSlots = new int[assignment.attackerCount()][];
        for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
            List<Integer> defenderIds = candidates.candidateDefendersByAttacker().getOrDefault(
                assignment.attackerNationIdAt(attackerSlot),
                List.of()
            );
            int[] defenderSlots = new int[defenderIds.size()];
            int size = 0;
            for (int defenderId : defenderIds) {
                int defenderSlot = assignment.defenderSlot(defenderId);
                if (defenderSlot >= 0) {
                    defenderSlots[size++] = defenderSlot;
                }
            }
            candidateDefenderSlots[attackerSlot] = size == defenderSlots.length
                ? defenderSlots
                : Arrays.copyOf(defenderSlots, size);
        }
        return candidateDefenderSlots;
    }

    private static final class RefinementAggregates {
        private static final double DEFENDER_PRESSURE_WEIGHT = 0.06;
        private static final double ATTACKER_COMMITMENT_WEIGHT = 0.04;
        private static final double SURROGATE_EVAL_FLOOR = -0.05;

        private final int[] attackerAssignedCount;
        private final int[] defenderAssignedCount;
        private final double[] attackerCommitmentLoad;
        private final double[] defenderPressure;
        private final int[] attackerCaps;
        private final int[] defenderCaps;

        private RefinementAggregates(
                int[] attackerAssignedCount,
                int[] defenderAssignedCount,
                double[] attackerCommitmentLoad,
                double[] defenderPressure,
                int[] attackerCaps,
                int[] defenderCaps) {
            this.attackerAssignedCount = attackerAssignedCount;
            this.defenderAssignedCount = defenderAssignedCount;
            this.attackerCommitmentLoad = attackerCommitmentLoad;
            this.defenderPressure = defenderPressure;
            this.attackerCaps = attackerCaps;
            this.defenderCaps = defenderCaps;
        }

        static RefinementAggregates fromAssignment(PlannerAssignmentSession assignment) {
            int[] attackerAssignedCount = new int[assignment.attackerCount()];
            int[] defenderAssignedCount = new int[assignment.defenderCount()];
            double[] attackerCommitmentLoad = new double[assignment.attackerCount()];
            double[] defenderPressure = new double[assignment.defenderCount()];
            int[] attackerCaps = new int[assignment.attackerCount()];
            int[] defenderCaps = new int[assignment.defenderCount()];

            for (int attackerSlot = 0; attackerSlot < assignment.attackerCount(); attackerSlot++) {
                attackerAssignedCount[attackerSlot] = assignment.assignedCount(attackerSlot);
                attackerCaps[attackerSlot] = Math.max(1, assignment.attackerCap(attackerSlot));
                attackerCommitmentLoad[attackerSlot] = (double) attackerAssignedCount[attackerSlot] / attackerCaps[attackerSlot];
            }
            for (int defenderSlot = 0; defenderSlot < assignment.defenderCount(); defenderSlot++) {
                defenderAssignedCount[defenderSlot] = assignment.defenderAssignedCount(defenderSlot);
                defenderCaps[defenderSlot] = Math.max(1, assignment.defenderCap(defenderSlot));
                defenderPressure[defenderSlot] = (double) defenderAssignedCount[defenderSlot] / defenderCaps[defenderSlot];
            }

            return new RefinementAggregates(
                attackerAssignedCount,
                defenderAssignedCount,
                attackerCommitmentLoad,
                defenderPressure,
                attackerCaps,
                defenderCaps
            );
        }

        boolean isPromising(double surrogateDelta) {
            return surrogateDelta > SURROGATE_EVAL_FLOOR;
        }

        double addDelta(PlannerAssignmentSession assignment, int attackerSlot, int defenderSlot, BlitzGeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(
                assignment.attackerNationIdAt(attackerSlot),
                assignment.defenderNationIdAt(defenderSlot)
            );
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return edgeScore
                + attackerPenaltyDelta(attackerSlot, +1)
                + defenderPenaltyDelta(defenderSlot, +1);
        }

        double dropDelta(PlannerAssignmentSession assignment, int attackerSlot, int defenderSlot, BlitzGeneratedCandidates candidates) {
            float edgeScore = candidates.edgeScore(
                assignment.attackerNationIdAt(attackerSlot),
                assignment.defenderNationIdAt(defenderSlot)
            );
            if (!Float.isFinite(edgeScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return -edgeScore
                + attackerPenaltyDelta(attackerSlot, -1)
                + defenderPenaltyDelta(defenderSlot, -1);
        }

        double moveDelta(
                PlannerAssignmentSession assignment,
                int attackerSlot,
                int oldDefenderSlot,
                int newDefenderSlot,
                BlitzGeneratedCandidates candidates) {
            int attackerNationId = assignment.attackerNationIdAt(attackerSlot);
            float dropScore = candidates.edgeScore(attackerNationId, assignment.defenderNationIdAt(oldDefenderSlot));
            float addScore = candidates.edgeScore(attackerNationId, assignment.defenderNationIdAt(newDefenderSlot));
            if (!Float.isFinite(dropScore) || !Float.isFinite(addScore)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (addScore - dropScore)
                + defenderPenaltyDelta(oldDefenderSlot, -1)
                + defenderPenaltyDelta(newDefenderSlot, +1);
        }

        double swapDelta(
                PlannerAssignmentSession assignment,
                int attackerOneSlot,
                int oldDefenderOneSlot,
                int newDefenderOneSlot,
                int attackerTwoSlot,
                int oldDefenderTwoSlot,
                int newDefenderTwoSlot,
                BlitzGeneratedCandidates candidates) {
            int attackerOneNationId = assignment.attackerNationIdAt(attackerOneSlot);
            int attackerTwoNationId = assignment.attackerNationIdAt(attackerTwoSlot);
            float oldOne = candidates.edgeScore(attackerOneNationId, assignment.defenderNationIdAt(oldDefenderOneSlot));
            float oldTwo = candidates.edgeScore(attackerTwoNationId, assignment.defenderNationIdAt(oldDefenderTwoSlot));
            float nextOne = candidates.edgeScore(attackerOneNationId, assignment.defenderNationIdAt(newDefenderOneSlot));
            float nextTwo = candidates.edgeScore(attackerTwoNationId, assignment.defenderNationIdAt(newDefenderTwoSlot));
            if (!Float.isFinite(oldOne) || !Float.isFinite(oldTwo) || !Float.isFinite(nextOne) || !Float.isFinite(nextTwo)) {
                return Double.NEGATIVE_INFINITY;
            }
            return (nextOne + nextTwo) - (oldOne + oldTwo);
        }

        void applyAdd(int attackerSlot, int defenderSlot) {
            applyAttacker(attackerSlot, +1);
            applyDefender(defenderSlot, +1);
        }

        void applyDrop(int attackerSlot, int defenderSlot) {
            applyAttacker(attackerSlot, -1);
            applyDefender(defenderSlot, -1);
        }

        void applyMove(int oldDefenderSlot, int newDefenderSlot) {
            applyDefender(oldDefenderSlot, -1);
            applyDefender(newDefenderSlot, +1);
        }

        void applySwap() {
        }

        private double attackerPenaltyDelta(int attackerSlot, int delta) {
            double before = attackerCommitmentLoad[attackerSlot];
            double after = (double) (attackerAssignedCount[attackerSlot] + delta) / attackerCaps[attackerSlot];
            return -ATTACKER_COMMITMENT_WEIGHT * ((after * after) - (before * before));
        }

        private double defenderPenaltyDelta(int defenderSlot, int delta) {
            double before = defenderPressure[defenderSlot];
            double after = (double) (defenderAssignedCount[defenderSlot] + delta) / defenderCaps[defenderSlot];
            return -DEFENDER_PRESSURE_WEIGHT * ((after * after) - (before * before));
        }

        private void applyAttacker(int attackerSlot, int delta) {
            attackerAssignedCount[attackerSlot] += delta;
            attackerCommitmentLoad[attackerSlot] = (double) attackerAssignedCount[attackerSlot] / attackerCaps[attackerSlot];
        }

        private void applyDefender(int defenderSlot, int delta) {
            defenderAssignedCount[defenderSlot] += delta;
            defenderPressure[defenderSlot] = (double) defenderAssignedCount[defenderSlot] / defenderCaps[defenderSlot];
        }
    }
}