package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Budgeted primitive marginal-flow owner for blitz horizons where first-turn exact local search is
 * the wrong optimization target.
 *
 * <p>Operates as a deterministic marginal-flow controller:
 * <ol>
 *   <li>Solve a baseline opening assignment with the initial edge-table scores.</li>
 *   <li>Build the full-horizon projection objective over base edge scores, defender-pressure
 *       completion, and attacker-commitment completion.</li>
 *   <li>Run one expanded-slot min-cost-flow solve that optimizes those marginal objective
 *       components directly.</li>
 * </ol>
 *
 * <p>The map-shaped assignment is still only materialized at the boundary. The bridge can also
 * score a small portfolio of candidate opening shapes through the primitive forward projection,
 * so objective terminal value can override the raw assignment scalar without replay allocation.
 */
final class LongHorizonAssignmentOptimizer {
    private static final int SHORT_HORIZON_LIMIT_TURNS = 12;
    private static final int MAX_HORIZON_TURNS = 720;
    private static final int FULL_PROJECTED_PORTFOLIO_EDGE_LIMIT = 1_500;
    private static final int FULL_PROJECTED_PORTFOLIO_PAIR_LIMIT = 150;
    private static final int LARGE_PROJECTED_PORTFOLIO_AUDIT_LIMIT = 1;
    static final double PRESSURE_SCORE_WEIGHT = 0.24d;
    static final double EPSILON = 1e-9;

    private LongHorizonAssignmentOptimizer() {
    }

    static boolean shouldOptimize(int horizonTurns) {
        return horizonTurns > SHORT_HORIZON_LIMIT_TURNS;
    }

    static Map<Integer, List<Integer>> solve(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns
    ) {
        return solve(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                null
        );
    }

    static Map<Integer, List<Integer>> solve(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            ProjectionScoringContext projectionScoringContext
    ) {
        return solveDetailed(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                projectionScoringContext
        ).assignment();
    }

    static Result solveDetailed(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            ProjectionScoringContext projectionScoringContext
    ) {
            try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE)) {
                int edgeCount = baseEdges.edgeCount();
                int attackerCount = scenario.attackerCount();
                int defenderCount = scenario.defenderCount();
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "edges", edgeCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "attackers", attackerCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "defenders", defenderCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "horizonTurns", horizonTurns);

                boolean[] initialEdgeAssigned = new boolean[edgeCount];
                int[] initialAttackerCounts = new int[attackerCount];
                int[] initialDefenderCounts = new int[defenderCount];

                Map<Integer, List<Integer>> initialAssignment = PrimitiveAssignmentSolver.solveAssignment(
                    baseEdges,
                    null,
                    attackerCount,
                    defenderCount,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges,
                    edgeCount > 0 ? initialEdgeAssigned : null,
                    initialAttackerCounts,
                    initialDefenderCounts
                );
                if (!shouldOptimize(horizonTurns) || edgeCount == 0) {
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "assignmentPairs", assignmentPairCount(initialAssignment));
                return new Result(initialAssignment, null);
                }

                LongHorizonControlProjection terminalProjection = LongHorizonControlProjection.create(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    horizonTurns,
                    horizonFactor(horizonTurns)
                );
                double initialScore = terminalProjection.assignmentScoreDense(
                    initialEdgeAssigned,
                    initialAttackerCounts,
                    initialDefenderCounts
                );
                LongHorizonMarginalFlowSolver.Result marginalResult = LongHorizonMarginalFlowSolver.solve(
                    baseEdges,
                    terminalProjection,
                    attackerCount,
                    defenderCount,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    attackerNationIds,
                    defenderNationIds,
                    fixedEdges
                );
                double marginalScore = terminalProjection.assignmentScoreDense(
                    marginalResult.edgeAssigned(),
                    marginalResult.attackerCounts(),
                    marginalResult.defenderCounts()
                );
                Candidate best = new Candidate(
                    initialAssignment,
                    initialEdgeAssigned,
                    initialAttackerCounts,
                    initialDefenderCounts,
                    initialScore
                );
                Candidate marginalCandidate = new Candidate(
                    marginalResult.assignment(),
                    marginalResult.edgeAssigned(),
                    marginalResult.attackerCounts(),
                    marginalResult.defenderCounts(),
                    marginalScore
                );
                LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(scenario, projectionScoringContext);
                best = evaluator.betterCandidate(best, marginalCandidate, terminalProjection);

                int marginalPairCount = assignmentPairCount(marginalCandidate.assignment());
                boolean runFullProjectedPortfolio = evaluator.canScoreObjectiveProjection()
                        && shouldRunFullProjectedPortfolio(edgeCount, marginalPairCount);
                if (runFullProjectedPortfolio) {
                    int[] realizedCounters = evaluator.realizedCounters(
                        marginalCandidate,
                        terminalProjection
                    );
                    for (Candidate reliefCandidate : LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        marginalCandidate,
                        terminalProjection,
                        realizedCounters
                    )) {
                        best = evaluator.betterCandidate(best, reliefCandidate, terminalProjection);
                    }
                    int[] feedbackCounters = evaluator.realizedCounters(
                        best,
                        terminalProjection
                    );
                    best = evaluator.betterCandidate(best, LongHorizonFeedbackSearch.recedingFixedPointFeedback(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        best,
                        feedbackCounters,
                        terminalProjection,
                        evaluator
                    ), terminalProjection);
                    best = evaluator.betterCandidate(best, solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        1
                    ), terminalProjection);
                    best = evaluator.betterCandidate(best, solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        2
                    ), terminalProjection);
                } else if (evaluator.canScoreObjectiveProjection()) {
                    best = evaluateBoundedProjectedPortfolio(
                            best,
                            baseEdges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            attackerStrengthRanks,
                            attackerNationIds,
                            defenderNationIds,
                            fixedEdges,
                            horizonTurns,
                            marginalCandidate,
                            terminalProjection,
                            evaluator
                    );
                }
                ObjectiveValueSummary projectedObjectiveSummary = evaluator.objectiveSummary(
                    best,
                    terminalProjection
                );
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "assignmentPairs", assignmentPairCount(best.assignment()));
                return new Result(cloneAssignment(best.assignment()), projectedObjectiveSummary);
            }
    }

            private static boolean shouldRunFullProjectedPortfolio(int edgeCount, int assignmentPairs) {
                return edgeCount <= FULL_PROJECTED_PORTFOLIO_EDGE_LIMIT
                        && assignmentPairs <= FULL_PROJECTED_PORTFOLIO_PAIR_LIMIT;
            }

            private static Candidate evaluateBoundedProjectedPortfolio(
                    Candidate currentBest,
                    CandidateEdgeTable baseEdges,
                    CompiledScenario scenario,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerStrengthRanks,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    List<BlitzFixedEdge> fixedEdges,
                    int horizonTurns,
                    Candidate marginalCandidate,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator
            ) {
                LongHorizonCandidateEvaluator cheapEvaluator = LongHorizonCandidateEvaluator.create(scenario, null);
                int[] realizedCounters = projectedEvaluator.realizedCounters(marginalCandidate, terminalProjection);
                List<Candidate> candidates = new ArrayList<>();
                candidates.addAll(LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        marginalCandidate,
                        terminalProjection,
                        realizedCounters
                ));
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "fixedPointFeedbackDeferred", 1);
                candidates.add(solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        1
                ));
                candidates.add(solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        2
                ));
                candidates.removeIf(candidate -> candidate == null || candidate == marginalCandidate);
                candidates.sort((left, right) -> Double.compare(
                        cheapEvaluator.score(right, terminalProjection),
                        cheapEvaluator.score(left, terminalProjection)
                ));
                int audited = 0;
                Candidate best = currentBest;
                for (Candidate candidate : candidates) {
                    if (audited >= LARGE_PROJECTED_PORTFOLIO_AUDIT_LIMIT) {
                        break;
                    }
                    best = projectedEvaluator.betterCandidate(best, candidate, terminalProjection);
                    audited++;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedPortfolio", 1);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedCandidates", candidates.size());
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedAudits", audited);
                return best;
            }

            private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
            int pairCount = 0;
            for (List<Integer> defenders : assignment.values()) {
                pairCount += defenders.size();
            }
            return pairCount;
            }

    private static Candidate solveWithAttackerCapLimit(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            int attackerCapLimit
    ) {
        int[] limitedCaps = new int[attackerCaps.length];
        for (int index = 0; index < attackerCaps.length; index++) {
            limitedCaps[index] = Math.min(attackerCaps[index], attackerCapLimit);
        }
        return solveWithAttackerCaps(
                baseEdges,
                scenario,
                limitedCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns
        );
    }

    static Candidate solveWithAttackerCaps(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns
    ) {
        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                horizonTurns,
                horizonFactor(horizonTurns)
        );
        LongHorizonMarginalFlowSolver.Result result = LongHorizonMarginalFlowSolver.solve(
                baseEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges
        );
        double projectionScore = projection.assignmentScoreDense(
                result.edgeAssigned(),
                result.attackerCounts(),
                result.defenderCounts()
        );
        return new Candidate(result.assignment(), result.edgeAssigned(), result.attackerCounts(), result.defenderCounts(), projectionScore);
    }

    static ObjectiveValueSummary projectedObjectiveSummary(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            Map<Integer, List<Integer>> assignment,
            StrategicObjective objective,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        if (assignment.isEmpty()) {
            return ObjectiveValueSummary.identical(0d);
        }
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                horizonTurns,
                horizonFactor(horizonTurns)
        );
        DenseAssignment denseAssignment = denseAssignment(
                baseEdges,
                scenario,
                assignment,
                attackerNationIds,
                defenderNationIds
        );
        int attackerTeamId = scenario.attackerCount() == 0 ? 1 : scenario.attacker(0).teamId();
        return ObjectiveValueSummary.identical(projection.projectedObjectiveScore(
                objective,
                attackerTeamId,
                denseAssignment.edgeAssigned(),
                denseAssignment.attackerCounts(),
                denseAssignment.defenderCounts()
        ));
    }

    private static DenseAssignment denseAssignment(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            Map<Integer, List<Integer>> assignment,
            int[] attackerNationIds,
            int[] defenderNationIds
    ) {
        boolean[] edgeAssigned = new boolean[baseEdges.edgeCount()];
        int[] attackerCounts = new int[scenario.attackerCount()];
        int[] defenderCounts = new int[scenario.defenderCount()];
        Int2IntOpenHashMap attackerIndexByNationId = new Int2IntOpenHashMap(Math.max(16, attackerNationIds.length * 2));
        Int2IntOpenHashMap defenderIndexByNationId = new Int2IntOpenHashMap(Math.max(16, defenderNationIds.length * 2));
        Long2IntOpenHashMap edgeIndexByPair = new Long2IntOpenHashMap(Math.max(16, baseEdges.edgeCount() * 2));
        attackerIndexByNationId.defaultReturnValue(-1);
        defenderIndexByNationId.defaultReturnValue(-1);
        edgeIndexByPair.defaultReturnValue(-1);
        for (int attackerIndex = 0; attackerIndex < attackerNationIds.length; attackerIndex++) {
            attackerIndexByNationId.put(attackerNationIds[attackerIndex], attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderNationIds.length; defenderIndex++) {
            defenderIndexByNationId.put(defenderNationIds[defenderIndex], defenderIndex);
        }
        for (int edgeIndex = 0; edgeIndex < baseEdges.edgeCount(); edgeIndex++) {
            edgeIndexByPair.put(pairKey(
                    attackerNationIds[baseEdges.attackerIndex(edgeIndex)],
                    defenderNationIds[baseEdges.defenderIndex(edgeIndex)]
            ), edgeIndex);
        }
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerIndex = attackerIndexByNationId.get(entry.getKey());
            if (attackerIndex < 0) {
                continue;
            }
            for (int defenderNationId : entry.getValue()) {
                int defenderIndex = defenderIndexByNationId.get(defenderNationId);
                if (defenderIndex < 0) {
                    continue;
                }
                attackerCounts[attackerIndex]++;
                defenderCounts[defenderIndex]++;
                int edgeIndex = edgeIndexByPair.get(pairKey(entry.getKey(), defenderNationId));
                if (edgeIndex >= 0) {
                    edgeAssigned[edgeIndex] = true;
                }
            }
        }
        return new DenseAssignment(edgeAssigned, attackerCounts, defenderCounts);
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) ^ (defenderNationId & 0xffffffffL);
    }

    private static Map<Integer, List<Integer>> cloneAssignment(Map<Integer, List<Integer>> assignment) {
        Map<Integer, List<Integer>> clone = new Int2ObjectLinkedOpenHashMap<>(Math.max(16, assignment.size() * 2));
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            clone.put(entry.getKey(), new IntArrayList(entry.getValue()));
        }
        return clone;
    }

    static double horizonFactor(int horizonTurns) {
        int clampedHorizon = Math.max(SHORT_HORIZON_LIMIT_TURNS + 1, Math.min(MAX_HORIZON_TURNS, horizonTurns));
        double numerator = Math.log1p(clampedHorizon - SHORT_HORIZON_LIMIT_TURNS);
        double denominator = Math.log1p(MAX_HORIZON_TURNS - SHORT_HORIZON_LIMIT_TURNS);
        return Math.max(0d, Math.min(1d, numerator / denominator));
    }

        record ProjectionScoringContext(
            StrategicObjective objective
    ) {
    }

    record Result(
            Map<Integer, List<Integer>> assignment,
            ObjectiveValueSummary projectedObjectiveSummary
    ) {
    }

        record Candidate(
            Map<Integer, List<Integer>> assignment,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            double projectionScore
    ) {
    }

    private record DenseAssignment(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
    }

}
