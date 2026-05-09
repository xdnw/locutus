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
                boolean includeSlotDenialScoring = projectionScoringContext != null
                        && projectionScoringContext.objective().usesWarSlotDenial();
                SideProjectionPolicies attackerProjectionPolicies = projectionScoringContext == null
                    ? SideProjectionPolicies.heuristic()
                    : projectionScoringContext.attackerProjectionPolicies();
                SideProjectionPolicies defenderProjectionPolicies = projectionScoringContext == null
                    ? SideProjectionPolicies.heuristic()
                    : projectionScoringContext.defenderProjectionPolicies();
                int projectedAuditLimit = projectionScoringContext == null
                    ? LARGE_PROJECTED_PORTFOLIO_AUDIT_LIMIT
                    : projectionScoringContext.projectedAuditLimit();
                SidePlannerSettings attackerPlannerSettings = projectionScoringContext == null
                    ? SidePlannerSettings.legacy()
                    : projectionScoringContext.attackerPlannerSettings();
                SidePlannerSettings defenderPlannerSettings = projectionScoringContext == null
                    ? SidePlannerSettings.legacy()
                    : projectionScoringContext.defenderPlannerSettings();

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
                if (includeSlotDenialScoring && !initialAssignment.isEmpty()) {
                    DenseAssignment denseInitial = denseAssignment(
                            baseEdges,
                            scenario,
                            initialAssignment,
                            attackerNationIds,
                            defenderNationIds
                    );
                    System.arraycopy(denseInitial.edgeAssigned(), 0, initialEdgeAssigned, 0, initialEdgeAssigned.length);
                    System.arraycopy(denseInitial.attackerCounts(), 0, initialAttackerCounts, 0, initialAttackerCounts.length);
                    System.arraycopy(denseInitial.defenderCounts(), 0, initialDefenderCounts, 0, initialDefenderCounts.length);
                }

                LongHorizonControlProjection terminalProjection = LongHorizonControlProjection.create(
                    baseEdges,
                    scenario,
                    attackerCaps,
                    defenderCaps,
                    attackerStrengthRanks,
                    horizonTurns,
                    horizonFactor(horizonTurns),
                    includeSlotDenialScoring,
                    projectionScoringContext == null ? null : projectionScoringContext.objective(),
                    projectionScoringContext == null ? null : projectionScoringContext.attackerOpeningSettings(),
                    projectionScoringContext == null ? null : projectionScoringContext.defenderOpeningSettings(),
                    attackerPlannerSettings,
                    defenderPlannerSettings,
                    attackerProjectionPolicies,
                    defenderProjectionPolicies
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

                if (evaluator.canScoreObjectiveProjection()) {
                    if (shouldRunFixedPointFeedback(edgeCount, assignmentPairCount(marginalCandidate.assignment()))) {
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
                            terminalProjection,
                            evaluator,
                            attackerPlannerSettings
                        ), terminalProjection);
                    } else {
                        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "fixedPointFeedbackDeferred", 1);
                    }
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
                            includeSlotDenialScoring,
                            marginalCandidate,
                            terminalProjection,
                            evaluator,
                            attackerPlannerSettings,
                            projectionScoringContext.objective().usesWarSlotDenial(),
                            projectedAuditLimit
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

                static boolean shouldRunFixedPointFeedback(int edgeCount, int assignmentPairs) {
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
                    boolean includeSlotDenialScoring,
                    Candidate marginalCandidate,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator,
                    SidePlannerSettings attackerPlannerSettings,
                    boolean preserveCapLimitBreadth,
                    int projectedAuditLimit
            ) {
                LongHorizonCandidateEvaluator cheapEvaluator = LongHorizonCandidateEvaluator.create(scenario, null);
                int[] realizedCounters = projectedEvaluator.realizedCounters(marginalCandidate, terminalProjection);
                List<Candidate> reliefCandidates = new ArrayList<>(LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
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
                        realizedCounters,
                        attackerPlannerSettings
                    ));
                    Candidate capLimitOne = solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        includeSlotDenialScoring,
                        attackerPlannerSettings,
                        1
                    );
                    Candidate capLimitTwo = solveWithAttackerCapLimit(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        includeSlotDenialScoring,
                        attackerPlannerSettings,
                        2
                );
                reliefCandidates.removeIf(candidate -> candidate == null || candidate == marginalCandidate);
                reliefCandidates.sort((left, right) -> Double.compare(
                        cheapEvaluator.score(right, terminalProjection),
                        cheapEvaluator.score(left, terminalProjection)
                ));
                Candidate bestCapLimit = betterCapLimitCandidate(
                    marginalCandidate,
                    capLimitOne,
                    capLimitTwo,
                    cheapEvaluator,
                    terminalProjection
                );
                int audited = 0;
                int reliefAudited = 0;
                Candidate best = currentBest;
                for (Candidate candidate : reliefCandidates) {
                    if (reliefAudited >= projectedAuditLimit) {
                        break;
                    }
                    best = projectedEvaluator.betterCandidate(best, candidate, terminalProjection);
                    audited++;
                    reliefAudited++;
                }
                if (preserveCapLimitBreadth) {
                    if (capLimitOne != null && capLimitOne != marginalCandidate) {
                        best = projectedEvaluator.betterCandidate(best, capLimitOne, terminalProjection);
                        audited++;
                    }
                    if (capLimitTwo != null && capLimitTwo != marginalCandidate && capLimitTwo != capLimitOne) {
                        best = projectedEvaluator.betterCandidate(best, capLimitTwo, terminalProjection);
                        audited++;
                    }
                } else if (bestCapLimit != null) {
                    best = projectedEvaluator.betterCandidate(best, bestCapLimit, terminalProjection);
                    audited++;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedPortfolio", 1);
                int capLimitCandidateCount = preserveCapLimitBreadth
                        ? distinctCapLimitCandidateCount(marginalCandidate, capLimitOne, capLimitTwo)
                        : (bestCapLimit != null ? 1 : 0);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedCandidates", reliefCandidates.size()
                        + capLimitCandidateCount);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedAudits", audited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedReliefAudits", reliefAudited);
                return best;
            }

            private static int distinctCapLimitCandidateCount(
                    Candidate marginalCandidate,
                    Candidate capLimitOne,
                    Candidate capLimitTwo
            ) {
                Candidate normalizedOne = normalizeCapLimitCandidate(capLimitOne, marginalCandidate);
                Candidate normalizedTwo = normalizeCapLimitCandidate(capLimitTwo, marginalCandidate);
                if (normalizedOne == null) {
                    return normalizedTwo == null ? 0 : 1;
                }
                if (normalizedTwo == null || normalizedTwo == normalizedOne) {
                    return 1;
                }
                return 2;
            }

            private static Candidate betterCapLimitCandidate(
                    Candidate marginalCandidate,
                    Candidate capLimitOne,
                    Candidate capLimitTwo,
                    LongHorizonCandidateEvaluator cheapEvaluator,
                    LongHorizonControlProjection terminalProjection
            ) {
                Candidate best = normalizeCapLimitCandidate(capLimitOne, marginalCandidate);
                Candidate alternative = normalizeCapLimitCandidate(capLimitTwo, marginalCandidate);
                if (best == null) {
                    return alternative;
                }
                if (alternative == null) {
                    return best;
                }
                return cheapEvaluator.score(alternative, terminalProjection)
                        > cheapEvaluator.score(best, terminalProjection) + EPSILON
                        ? alternative
                        : best;
            }

            private static Candidate normalizeCapLimitCandidate(Candidate candidate, Candidate marginalCandidate) {
                if (candidate == null || candidate == marginalCandidate) {
                    return null;
                }
                return candidate;
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
            boolean includeSlotDenialScoring,
                SidePlannerSettings attackerPlannerSettings,
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
                horizonTurns,
                includeSlotDenialScoring,
                attackerPlannerSettings
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
        return solveWithAttackerCaps(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                false
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
            int horizonTurns,
            boolean includeSlotDenialScoring
    ) {
            return solveWithAttackerCaps(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                fixedEdges,
                horizonTurns,
                includeSlotDenialScoring,
                SidePlannerSettings.legacy()
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
                int horizonTurns,
                boolean includeSlotDenialScoring,
                SidePlannerSettings attackerPlannerSettings
            ) {
        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                baseEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                horizonTurns,
                horizonFactor(horizonTurns),
                includeSlotDenialScoring,
                attackerPlannerSettings
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
        return projectedObjectiveSummary(
            baseEdges,
            scenario,
            attackerCaps,
            defenderCaps,
            horizonTurns,
            assignment,
            ProjectionScoringContext.legacy(objective),
            attackerNationIds,
            defenderNationIds
        );
        }

        static ObjectiveValueSummary projectedObjectiveSummary(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int horizonTurns,
            Map<Integer, List<Integer>> assignment,
            ProjectionScoringContext projectionScoringContext,
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
                horizonFactor(horizonTurns),
                projectionScoringContext.objective().usesWarSlotDenial(),
            projectionScoringContext.objective(),
            projectionScoringContext.attackerOpeningSettings(),
            projectionScoringContext.defenderOpeningSettings(),
                projectionScoringContext.attackerPlannerSettings(),
                projectionScoringContext.defenderPlannerSettings(),
                projectionScoringContext.attackerProjectionPolicies(),
                projectionScoringContext.defenderProjectionPolicies()
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
            projectionScoringContext.objective(),
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
            StrategicObjective objective,
            SideOpeningSettings attackerOpeningSettings,
            SideOpeningSettings defenderOpeningSettings,
            SidePlannerSettings attackerPlannerSettings,
            SidePlannerSettings defenderPlannerSettings,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        static ProjectionScoringContext legacy(StrategicObjective objective) {
            return fromSidePolicies(
                objective,
                SidePolicy.legacy("attacker", objective),
                SidePolicy.legacyPassive("defender", objective)
            );
        }

        static ProjectionScoringContext fromSidePolicies(
            StrategicObjective objective,
            SidePolicy attackerPolicy,
            SidePolicy defenderPolicy
        ) {
            if (attackerPolicy == null) {
            throw new IllegalArgumentException("attackerPolicy must not be null");
            }
            if (defenderPolicy == null) {
            throw new IllegalArgumentException("defenderPolicy must not be null");
            }
            return new ProjectionScoringContext(
                objective,
                attackerPolicy.opening(),
                defenderPolicy.opening(),
                attackerPolicy.planner(),
                defenderPolicy.planner(),
                attackerPolicy.projection(),
                defenderPolicy.projection()
            );
        }

        ProjectionScoringContext(
            StrategicObjective objective,
            SidePlannerSettings attackerPlannerSettings,
            SidePlannerSettings defenderPlannerSettings,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
        ) {
            this(
                objective,
                SideOpeningSettings.legacy(objective),
                SideOpeningSettings.legacy(objective),
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
            );
        }

        ProjectionScoringContext(
                StrategicObjective objective,
                SideProjectionPolicies attackerProjectionPolicies,
                SideProjectionPolicies defenderProjectionPolicies,
                int projectedAuditLimit
        ) {
            this(
                    objective,
                    SidePolicy.legacy(objective).opening(),
                    SidePolicy.legacyPassive(objective).opening(),
                    SidePolicy.legacy(objective).planner().withProjectedAuditLimit(projectedAuditLimit),
                    SidePolicy.legacyPassive(objective).planner(),
                    attackerProjectionPolicies,
                    defenderProjectionPolicies
            );
        }

        ProjectionScoringContext {
            if (objective == null) {
                throw new IllegalArgumentException("objective must not be null");
            }
            if (attackerPlannerSettings == null) {
                throw new IllegalArgumentException("attackerPlannerSettings must not be null");
            }
            if (attackerOpeningSettings == null) {
                throw new IllegalArgumentException("attackerOpeningSettings must not be null");
            }
            if (defenderOpeningSettings == null) {
                throw new IllegalArgumentException("defenderOpeningSettings must not be null");
            }
            if (defenderPlannerSettings == null) {
                throw new IllegalArgumentException("defenderPlannerSettings must not be null");
            }
            if (attackerProjectionPolicies == null) {
                throw new IllegalArgumentException("attackerProjectionPolicies must not be null");
            }
            if (defenderProjectionPolicies == null) {
                throw new IllegalArgumentException("defenderProjectionPolicies must not be null");
            }
        }

        int projectedAuditLimit() {
            return attackerPlannerSettings.projectedAuditLimit();
        }
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
