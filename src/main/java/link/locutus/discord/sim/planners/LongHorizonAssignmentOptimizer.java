package link.locutus.discord.sim.planners;

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
    private static final int SLOT_DENIAL_DIVERSITY_HEDGE_AUDITS = 1;
    private static final int HIGH_CITY_COVERAGE_REPAIR_AUDITS = 3;
    private static final int HIGH_CITY_COVERAGE_REPAIR_CITY_FLOOR = 36;
    private static final int FOLLOW_ON_PROMOTION_EDGE_LIMIT = 3;
    private static final int FOLLOW_ON_FAMILY_SEED_LIMIT = 4;
    private static final int PROJECTED_FEEDBACK_EDGE_LIMIT = 6;
    static final double PRESSURE_SCORE_WEIGHT = 0.70d;
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
                projectionScoringContext,
                true
        );
    }

    private static Result solveDetailed(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            int[] attackerStrengthRanks,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            int horizonTurns,
            ProjectionScoringContext projectionScoringContext,
            boolean allowProjectionFeedbackEdges
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
                LongHorizonMarginalFlowSolver.StaticSolveInputs marginalFlowStaticInputs =
                    LongHorizonMarginalFlowSolver.staticSolveInputs(
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges
                    );
                LongHorizonMarginalFlowSolver.GraphBuildBuffers marginalFlowGraphBuffers =
                    new LongHorizonMarginalFlowSolver.GraphBuildBuffers();

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
                    fixedEdges,
                    marginalFlowStaticInputs,
                    marginalFlowGraphBuffers
                );
                double marginalScore = terminalProjection.assignmentScoreDense(
                    marginalResult.edgeAssigned(),
                    marginalResult.attackerCounts(),
                    marginalResult.defenderCounts()
                );
                Candidate marginalCandidate = new Candidate(marginalResult, marginalScore);
                LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(
                    scenario,
                    baseEdges,
                    projectionScoringContext
                );
                Candidate best = marginalCandidate;

                if (evaluator.canScoreObjectiveProjection()) {
                    int[] fixedAttackerCounts = LongHorizonFeedbackSearch.fixedAttackerCounts(fixedEdges, attackerNationIds);
                    if (shouldRunFixedPointFeedback(edgeCount, marginalCandidate.assignmentPairCount())) {
                        best = evaluator.betterCandidate(best, LongHorizonFeedbackSearch.recedingFixedPointFeedback(
                            baseEdges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            attackerStrengthRanks,
                            attackerNationIds,
                            defenderNationIds,
                            fixedEdges,
                            fixedAttackerCounts,
                            horizonTurns,
                            best,
                            terminalProjection,
                            evaluator,
                            attackerPlannerSettings,
                            marginalFlowStaticInputs,
                            marginalFlowGraphBuffers
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
                            fixedAttackerCounts,
                            horizonTurns,
                            includeSlotDenialScoring,
                            marginalCandidate,
                            terminalProjection,
                            evaluator,
                            attackerPlannerSettings,
                                marginalFlowStaticInputs,
                            marginalFlowGraphBuffers,
                            projectionScoringContext.objective().usesWarSlotDenial(),
                            projectedAuditLimit
                    );
                            best = evaluator.betterCandidate(best, fixedOnlyCandidate(
                                baseEdges,
                                scenario,
                                attackerNationIds,
                                defenderNationIds,
                                fixedEdges,
                                terminalProjection
                            ), terminalProjection);
                    if (allowProjectionFeedbackEdges
                            && appendProjectedFeedbackEdges(
                                    baseEdges,
                                    scenario,
                                    attackerNationIds,
                                    defenderNationIds,
                                    best,
                                    marginalCandidate,
                                    terminalProjection,
                                    evaluator
                            ) > 0) {
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
                                projectionScoringContext,
                                false
                        );
                    }
                }
                ObjectiveValueSummary projectedObjectiveSummary = evaluator.objectiveSummary(
                    best,
                    terminalProjection
                );
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "assignmentPairs", best.assignmentPairCount());
                return new Result(cloneAssignment(best.assignment()), projectedObjectiveSummary);
        }
    }

    private static Candidate fixedOnlyCandidate(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerNationIds,
            int[] defenderNationIds,
            List<BlitzFixedEdge> fixedEdges,
            LongHorizonControlProjection terminalProjection
    ) {
        boolean[] edgeAssigned = new boolean[baseEdges.edgeCount()];
        int[] attackerCounts = new int[scenario.attackerCount()];
        int[] defenderCounts = new int[scenario.defenderCount()];
        Map<Integer, List<Integer>> assignment = fixedEdges.isEmpty()
                ? Map.of()
                : new Int2ObjectLinkedOpenHashMap<>();
        Long2IntOpenHashMap edgeIndexByPair = fixedEdges.isEmpty()
                ? null
                : baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            int attackerIndex = scenario.attackerIndexOrMinusOne(fixedEdge.attackerNationId());
            int defenderIndex = scenario.defenderIndexOrMinusOne(fixedEdge.defenderNationId());
            if (attackerIndex < 0 || defenderIndex < 0) {
                continue;
            }
            attackerCounts[attackerIndex]++;
            defenderCounts[defenderIndex]++;
            int edgeIndex = edgeIndexByPair.get(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
            if (edgeIndex >= 0 && edgeIndex < edgeAssigned.length) {
                edgeAssigned[edgeIndex] = true;
            }
            assignment.computeIfAbsent(fixedEdge.attackerNationId(), ignored -> new IntArrayList())
                    .add(fixedEdge.defenderNationId());
        }
        double projectionScore = terminalProjection.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts);
        return new Candidate(assignment, edgeAssigned, attackerCounts, defenderCounts, projectionScore);
    }

    private static int appendProjectedFeedbackEdges(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            int[] attackerNationIds,
            int[] defenderNationIds,
            Candidate best,
            Candidate marginalCandidate,
            LongHorizonControlProjection terminalProjection,
            LongHorizonCandidateEvaluator projectedEvaluator
    ) {
        if (best == null && marginalCandidate == null) {
            return 0;
        }
        Long2IntOpenHashMap baseEdgeByPair = baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
        int appended = 0;
        appended += appendProjectedFeedbackEdgesFromCandidate(
                baseEdges,
                scenario,
                baseEdgeByPair,
                best,
                terminalProjection,
                projectedEvaluator,
                PROJECTED_FEEDBACK_EDGE_LIMIT - appended
        );
        if (appended < PROJECTED_FEEDBACK_EDGE_LIMIT && marginalCandidate != best) {
            appended += appendProjectedFeedbackEdgesFromCandidate(
                    baseEdges,
                    scenario,
                    baseEdgeByPair,
                    marginalCandidate,
                    terminalProjection,
                    projectedEvaluator,
                    PROJECTED_FEEDBACK_EDGE_LIMIT - appended
            );
        }
        if (appended > 0) {
            PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFeedbackEdges", appended);
        }
        return appended;
    }

    private static int appendProjectedFeedbackEdgesFromCandidate(
            CandidateEdgeTable baseEdges,
            CompiledScenario scenario,
            Long2IntOpenHashMap baseEdgeByPair,
            Candidate seed,
            LongHorizonControlProjection terminalProjection,
            LongHorizonCandidateEvaluator projectedEvaluator,
            int limit
    ) {
        if (limit <= 0 || seed == null || seed.isEmpty()) {
            return 0;
        }
        LongHorizonForwardProjection.ProjectedEvaluation evaluation = projectedEvaluator.projectedEvaluation(seed, terminalProjection);
        int appended = 0;
        for (LongHorizonForwardProjection.OpeningSideLaterDeclaration followOn : evaluation.openingSideLaterDeclarations()) {
            if (appended >= limit) {
                break;
            }
            long key = pairKey(followOn.declarerNationId(), followOn.targetNationId());
            if (baseEdgeByPair.get(key) >= 0) {
                continue;
            }
            int attackerIndex = scenario.attackerIndexOrMinusOne(followOn.declarerNationId());
            int defenderIndex = scenario.defenderIndexOrMinusOne(followOn.targetNationId());
            if (attackerIndex < 0 || defenderIndex < 0) {
                continue;
            }
            float score = (float) Math.max(0d, followOn.score());
            if (!Float.isFinite(score) || score <= 0f) {
                continue;
            }
            int edgeIndex = baseEdges.add(
                    attackerIndex,
                    defenderIndex,
                    (byte) link.locutus.discord.apiv1.enums.WarType.ORD.ordinal(),
                    (byte) 0,
                    score,
                    0f
            );
            baseEdgeByPair.put(key, edgeIndex);
            appended++;
        }
        return appended;
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
                    int[] fixedAttackerCounts,
                    int horizonTurns,
                    boolean includeSlotDenialScoring,
                    Candidate marginalCandidate,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator,
                    SidePlannerSettings attackerPlannerSettings,
                    LongHorizonMarginalFlowSolver.StaticSolveInputs marginalFlowStaticInputs,
                    LongHorizonMarginalFlowSolver.GraphBuildBuffers marginalFlowGraphBuffers,
                    boolean preserveBudgetBreadth,
                    int projectedAuditLimit
            ) {
                LongHorizonCandidateEvaluator cheapEvaluator = LongHorizonCandidateEvaluator.create(scenario, baseEdges, null);
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
                        fixedAttackerCounts,
                        horizonTurns,
                        marginalCandidate,
                        terminalProjection,
                        realizedCounters,
                        attackerPlannerSettings,
                        marginalFlowStaticInputs,
                        marginalFlowGraphBuffers
                    ));
                    Candidate actionabilityFeedbackCandidate = LongHorizonFeedbackSearch.actionabilityFeedbackCandidate(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        fixedAttackerCounts,
                        marginalCandidate,
                        terminalProjection,
                        projectedEvaluator,
                        marginalFlowStaticInputs,
                        marginalFlowGraphBuffers
                    );
                    int[] commitmentCaps = dynamicCommitmentCaps(
                            baseEdges,
                            scenario,
                            attackerCaps,
                            defenderCaps,
                            fixedAttackerCounts
                    );
                    Candidate commitmentBudget = respectsAttackerCaps(marginalCandidate, commitmentCaps)
                            ? marginalCandidate
                            : solveWithAttackerCaps(
                        baseEdges,
                        scenario,
                        commitmentCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdges,
                        horizonTurns,
                        includeSlotDenialScoring,
                        attackerPlannerSettings,
                        marginalCandidate.edgeAssigned(),
                        marginalFlowStaticInputs,
                        marginalFlowGraphBuffers
                    );
                reliefCandidates.removeIf(candidate -> candidate == null || candidate == marginalCandidate);
                sortReliefCandidatesByCheapScore(reliefCandidates, cheapEvaluator, terminalProjection);
                Candidate bestCommitmentBudget = normalizeBudgetCandidate(commitmentBudget, marginalCandidate);
                Candidate diversityHedge = preserveBudgetBreadth
                        ? selectDiversityHedge(reliefCandidates, marginalCandidate, projectedAuditLimit)
                        : null;
                boolean[] fixedEdgeMask = fixedEdgeMask(baseEdges, attackerNationIds, defenderNationIds, fixedEdges);
                Candidate coverageRepairSeed = bestCommitmentBudget == null ? marginalCandidate : bestCommitmentBudget;
                List<Candidate> coverageRepairCandidates = highCityCoverageRepairCandidates(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerNationIds,
                        defenderNationIds,
                    fixedEdgeMask,
                        coverageRepairSeed,
                        terminalProjection,
                        HIGH_CITY_COVERAGE_REPAIR_AUDITS
                );
                int audited = 0;
                int reliefAudited = 0;
                int actionabilityFeedbackAudited = 0;
                int diversityAudited = 0;
                int coverageRepairAudited = 0;
                int followOnPromotionAudited = 0;
                int followOnRebalanceAudited = 0;
                Candidate best = currentBest;
                for (Candidate candidate : reliefCandidates) {
                    if (reliefAudited >= projectedAuditLimit) {
                        break;
                    }
                    best = projectedEvaluator.betterCandidate(best, candidate, terminalProjection);
                    audited++;
                    reliefAudited++;
                }
                if (actionabilityFeedbackCandidate != null) {
                    best = projectedEvaluator.betterCandidate(best, actionabilityFeedbackCandidate, terminalProjection);
                    audited++;
                    actionabilityFeedbackAudited++;
                }
                if (diversityHedge != null) {
                    best = projectedEvaluator.betterCandidate(best, diversityHedge, terminalProjection);
                    audited++;
                    diversityAudited++;
                }
                for (Candidate candidate : coverageRepairCandidates) {
                    best = projectedEvaluator.betterCandidate(best, candidate, terminalProjection);
                    audited++;
                    coverageRepairAudited++;
                }
                if (preserveBudgetBreadth) {
                    if (bestCommitmentBudget != null) {
                        best = projectedEvaluator.betterCandidate(best, bestCommitmentBudget, terminalProjection);
                        audited++;
                    }
                } else if (bestCommitmentBudget != null) {
                    best = projectedEvaluator.betterCandidate(best, bestCommitmentBudget, terminalProjection);
                    audited++;
                }
                List<Candidate> followOnSeeds = followOnFamilySeeds(
                        best,
                        marginalCandidate,
                        bestCommitmentBudget,
                        actionabilityFeedbackCandidate,
                        diversityHedge,
                        reliefCandidates,
                        coverageRepairCandidates
                );
                for (Candidate followOnSeed : followOnSeeds) {
                    FollowOnAuditResult followOnAudit = auditFollowOnFamilies(
                            best,
                            followOnSeed,
                            baseEdges,
                            attackerCaps,
                            defenderCaps,
                            attackerNationIds,
                            defenderNationIds,
                            fixedEdgeMask,
                            terminalProjection,
                            projectedEvaluator
                    );
                    best = followOnAudit.bestCandidate();
                    audited += followOnAudit.additionalAudits();
                    followOnPromotionAudited += followOnAudit.promotionAudits();
                    followOnRebalanceAudited += followOnAudit.rebalanceAudits();
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedPortfolio", 1);
                int commitmentCandidateCount = bestCommitmentBudget == null ? 0 : 1;
                int actionabilityCandidateCount = actionabilityFeedbackCandidate == null ? 0 : 1;
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedCandidates", reliefCandidates.size()
                        + commitmentCandidateCount
                    + actionabilityCandidateCount
                    + followOnPromotionAudited
                    + followOnRebalanceAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedAudits", audited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedReliefAudits", reliefAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedActionabilityFeedbackAudits", actionabilityFeedbackAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedDiversityAudits", diversityAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedCoverageRepairAudits", coverageRepairAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionAudits", followOnPromotionAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceAudits", followOnRebalanceAudited);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnFamilySeeds", followOnSeeds.size());
                return best;
            }

            private static FollowOnAuditResult auditFollowOnFamilies(
                    Candidate currentBest,
                    Candidate seed,
                    CandidateEdgeTable baseEdges,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    boolean[] fixedEdgeMask,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator
            ) {
                if (seed == null || seed.isEmpty()) {
                    return new FollowOnAuditResult(currentBest, 0, 0, 0);
                }
                Candidate best = currentBest;
                int additionalAudits = 0;
                int promotionAudits = 0;
                int rebalanceAudits = 0;
                LongHorizonForwardProjection.ProjectedEvaluation evaluation = followOnEvaluation(
                        seed,
                        terminalProjection,
                        projectedEvaluator
                );
                Candidate promotionCandidate = followOnPromotionCandidate(
                        baseEdges,
                        attackerCaps,
                        defenderCaps,
                        attackerNationIds,
                        defenderNationIds,
                        seed,
                        terminalProjection,
                        evaluation
                );
                if (promotionCandidate != null) {
                    best = projectedEvaluator.betterCandidate(best, promotionCandidate, terminalProjection);
                    additionalAudits++;
                    promotionAudits++;
                }
                Candidate rebalanceCandidate = followOnRebalanceCandidate(
                        baseEdges,
                        attackerCaps,
                        defenderCaps,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdgeMask,
                        seed,
                        terminalProjection,
                        evaluation
                );
                if (rebalanceCandidate != null) {
                    best = projectedEvaluator.betterCandidate(best, rebalanceCandidate, terminalProjection);
                    additionalAudits++;
                    rebalanceAudits++;
                }
                return new FollowOnAuditResult(best, additionalAudits, promotionAudits, rebalanceAudits);
            }

                static List<Candidate> followOnFamilySeeds(
                    Candidate best,
                    Candidate marginalCandidate,
                    Candidate bestCommitmentBudget,
                    Candidate actionabilityFeedbackCandidate,
                    Candidate diversityHedge,
                    List<Candidate> reliefCandidates,
                    List<Candidate> coverageRepairCandidates
            ) {
                List<Candidate> seeds = new ArrayList<>(FOLLOW_ON_FAMILY_SEED_LIMIT);
                addFollowOnFamilySeed(seeds, best);
                addFollowOnFamilySeed(seeds, marginalCandidate);
                addFollowOnFamilySeed(seeds, bestCommitmentBudget);
                addFollowOnFamilySeed(seeds, actionabilityFeedbackCandidate);
                addFollowOnFamilySeed(seeds, diversityHedge);
                for (Candidate candidate : reliefCandidates) {
                    if (seeds.size() >= FOLLOW_ON_FAMILY_SEED_LIMIT) {
                        break;
                    }
                    addFollowOnFamilySeed(seeds, candidate);
                }
                for (Candidate candidate : coverageRepairCandidates) {
                    if (seeds.size() >= FOLLOW_ON_FAMILY_SEED_LIMIT) {
                        break;
                    }
                    addFollowOnFamilySeed(seeds, candidate);
                }
                return seeds;
            }

            private static void addFollowOnFamilySeed(List<Candidate> seeds, Candidate candidate) {
                if (candidate == null || candidate.isEmpty() || seeds.contains(candidate) || seeds.size() >= FOLLOW_ON_FAMILY_SEED_LIMIT) {
                    return;
                }
                seeds.add(candidate);
            }

            private static LongHorizonForwardProjection.ProjectedEvaluation followOnEvaluation(
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator
            ) {
                if (seed == null || seed.isEmpty()) {
                    return null;
                }
                return projectedEvaluator.projectedEvaluation(seed, terminalProjection);
            }

            static Candidate followOnPromotionCandidate(
                    CandidateEdgeTable baseEdges,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator
            ) {
                return followOnPromotionCandidate(
                        baseEdges,
                        attackerCaps,
                        defenderCaps,
                        attackerNationIds,
                        defenderNationIds,
                        seed,
                        terminalProjection,
                        followOnEvaluation(seed, terminalProjection, projectedEvaluator)
                );
            }

            static Candidate followOnPromotionCandidate(
                    CandidateEdgeTable baseEdges,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonForwardProjection.ProjectedEvaluation evaluation
            ) {
                if (seed == null || seed.isEmpty()) {
                    return null;
                }
                if (evaluation == null) {
                    return null;
                }
                List<LongHorizonForwardProjection.OpeningSideLaterDeclaration> followOns = evaluation.openingSideLaterDeclarations();
                if (followOns.isEmpty()) {
                    return null;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionSignals", followOns.size());
                Long2IntOpenHashMap baseEdgeByPair = baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
                boolean[] edgeAssigned = seed.edgeAssigned().clone();
                int[] attackerCounts = seed.attackerCounts().clone();
                int[] defenderCounts = seed.defenderCounts().clone();
                Map<Integer, List<Integer>> assignment = cloneAssignment(seed.assignment());
                int promoted = 0;
                int alreadyAssigned = 0;
                int missingBaseEdge = 0;
                int initialCapBlocked = 0;
                for (LongHorizonForwardProjection.OpeningSideLaterDeclaration followOn : followOns) {
                    if (promoted >= FOLLOW_ON_PROMOTION_EDGE_LIMIT) {
                        break;
                    }
                    int edgeIndex = baseEdgeByPair.get(pairKey(followOn.declarerNationId(), followOn.targetNationId()));
                    if (edgeIndex < 0 || edgeIndex >= edgeAssigned.length) {
                        missingBaseEdge++;
                        continue;
                    }
                    if (edgeAssigned[edgeIndex]) {
                        alreadyAssigned++;
                        continue;
                    }
                    int attackerIndex = baseEdges.attackerIndex(edgeIndex);
                    int defenderIndex = baseEdges.defenderIndex(edgeIndex);
                    if (attackerIndex < 0 || attackerIndex >= attackerCounts.length
                            || defenderIndex < 0 || defenderIndex >= defenderCounts.length
                            || attackerCounts[attackerIndex] >= attackerCaps[attackerIndex]
                            || defenderCounts[defenderIndex] >= defenderCaps[defenderIndex]) {
                        initialCapBlocked++;
                        continue;
                    }
                    edgeAssigned[edgeIndex] = true;
                    attackerCounts[attackerIndex]++;
                    defenderCounts[defenderIndex]++;
                    assignment.computeIfAbsent(followOn.declarerNationId(), ignored -> new IntArrayList())
                            .add(followOn.targetNationId());
                    promoted++;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionAlreadyAssigned", alreadyAssigned);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionMissingBaseEdge", missingBaseEdge);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionInitialCapBlocked", initialCapBlocked);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnPromotionEdges", promoted);
                if (promoted == 0) {
                    return null;
                }
                double projectionScore = terminalProjection.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts);
                return new Candidate(assignment, edgeAssigned, attackerCounts, defenderCounts, projectionScore);
            }

            static Candidate followOnRebalanceCandidate(
                    CandidateEdgeTable baseEdges,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    List<BlitzFixedEdge> fixedEdges,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonCandidateEvaluator projectedEvaluator
            ) {
                return followOnRebalanceCandidate(
                        baseEdges,
                        attackerCaps,
                        defenderCaps,
                        attackerNationIds,
                        defenderNationIds,
                        fixedEdgeMask(baseEdges, attackerNationIds, defenderNationIds, fixedEdges),
                        seed,
                        terminalProjection,
                        followOnEvaluation(seed, terminalProjection, projectedEvaluator)
                );
            }

            static Candidate followOnRebalanceCandidate(
                    CandidateEdgeTable baseEdges,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    boolean[] fixedEdgeMask,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    LongHorizonForwardProjection.ProjectedEvaluation evaluation
            ) {
                if (seed == null || seed.isEmpty() || evaluation == null) {
                    return null;
                }
                List<LongHorizonForwardProjection.OpeningSideLaterDeclaration> followOns = evaluation.openingSideLaterDeclarations();
                if (followOns.isEmpty()) {
                    return null;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceSignals", followOns.size());
                Long2IntOpenHashMap baseEdgeByPair = baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
                boolean[] edgeAssigned = seed.edgeAssigned().clone();
                int[] attackerCounts = seed.attackerCounts().clone();
                int[] defenderCounts = seed.defenderCounts().clone();
                Map<Integer, List<Integer>> assignment = cloneAssignment(seed.assignment());
                int rebalanced = 0;
                int removed = 0;
                int alreadyAssigned = 0;
                int missingBaseEdge = 0;
                int capBlocked = 0;
                for (LongHorizonForwardProjection.OpeningSideLaterDeclaration followOn : followOns) {
                    if (rebalanced >= FOLLOW_ON_PROMOTION_EDGE_LIMIT) {
                        break;
                    }
                    int edgeIndex = baseEdgeByPair.get(pairKey(followOn.declarerNationId(), followOn.targetNationId()));
                    if (edgeIndex < 0 || edgeIndex >= edgeAssigned.length) {
                        missingBaseEdge++;
                        continue;
                    }
                    if (edgeAssigned[edgeIndex]) {
                        alreadyAssigned++;
                        continue;
                    }
                    int attackerIndex = baseEdges.attackerIndex(edgeIndex);
                    int defenderIndex = baseEdges.defenderIndex(edgeIndex);
                    boolean attackerBlocked = attackerIndex < 0
                            || attackerIndex >= attackerCounts.length
                            || attackerCounts[attackerIndex] >= attackerCaps[attackerIndex];
                    boolean defenderBlocked = defenderIndex < 0
                            || defenderIndex >= defenderCounts.length
                            || defenderCounts[defenderIndex] >= defenderCaps[defenderIndex];
                    if (!attackerBlocked && !defenderBlocked) {
                        continue;
                    }
                    int attackerRemoval = attackerBlocked
                            ? weakestAssignedEdgeForAttacker(baseEdges, fixedEdgeMask, edgeAssigned, attackerIndex)
                            : -1;
                    if (attackerBlocked && attackerRemoval < 0) {
                        capBlocked++;
                        continue;
                    }
                    int defenderRemoval = -1;
                    int defenderCountAfterAttackerRemoval = defenderCounts[defenderIndex];
                    if (attackerRemoval >= 0 && baseEdges.defenderIndex(attackerRemoval) == defenderIndex) {
                        defenderCountAfterAttackerRemoval--;
                    }
                    if (defenderBlocked && defenderCountAfterAttackerRemoval >= defenderCaps[defenderIndex]) {
                        defenderRemoval = weakestAssignedEdgeForDefender(
                                baseEdges,
                                fixedEdgeMask,
                                edgeAssigned,
                                defenderIndex,
                                attackerRemoval
                        );
                        if (defenderRemoval < 0) {
                            capBlocked++;
                            continue;
                        }
                    }
                    if (attackerRemoval >= 0) {
                        removeAssignedEdge(baseEdges, assignment, edgeAssigned, attackerCounts, defenderCounts, attackerNationIds, defenderNationIds, attackerRemoval);
                        removed++;
                    }
                    if (defenderRemoval >= 0 && defenderRemoval != attackerRemoval) {
                        removeAssignedEdge(baseEdges, assignment, edgeAssigned, attackerCounts, defenderCounts, attackerNationIds, defenderNationIds, defenderRemoval);
                        removed++;
                    }
                    edgeAssigned[edgeIndex] = true;
                    attackerCounts[attackerIndex]++;
                    defenderCounts[defenderIndex]++;
                    assignment.computeIfAbsent(attackerNationIds[attackerIndex], ignored -> new IntArrayList())
                            .add(defenderNationIds[defenderIndex]);
                    rebalanced++;
                }
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceAlreadyAssigned", alreadyAssigned);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceMissingBaseEdge", missingBaseEdge);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceCapBlocked", capBlocked);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceEdges", rebalanced);
                PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_SOLVE, "boundedProjectedFollowOnRebalanceRemovedEdges", removed);
                if (rebalanced == 0 || removed == 0) {
                    return null;
                }
                double projectionScore = terminalProjection.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts);
                return new Candidate(assignment, edgeAssigned, attackerCounts, defenderCounts, projectionScore);
            }

            private static List<Candidate> highCityCoverageRepairCandidates(
                    CandidateEdgeTable baseEdges,
                    CompiledScenario scenario,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    boolean[] fixedEdgeMask,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    int maxCandidates
            ) {
                List<Candidate> candidates = new ArrayList<>(Math.max(0, maxCandidates));
                if (maxCandidates <= 0 || seed == null || seed.isEmpty()) {
                    return candidates;
                }
                boolean[] usedAttacker = new boolean[scenario.attackerCount()];
                Candidate repairSeed = seed;
                for (int defenderIndex = 0; defenderIndex < seed.defenderCounts().length && candidates.size() < maxCandidates; defenderIndex++) {
                    int[] defenderCounts = repairSeed.defenderCounts();
                    if (defenderCounts[defenderIndex] > 0
                            || defenderCaps[defenderIndex] <= 0
                            || scenario.defender(defenderIndex).cities() < HIGH_CITY_COVERAGE_REPAIR_CITY_FLOOR) {
                        continue;
                    }
                    int bestIncomingEdge = bestActiveIncomingEdge(baseEdges, repairSeed, usedAttacker, defenderIndex);
                    if (bestIncomingEdge < 0) {
                        continue;
                    }
                    Candidate repaired = retargetCoverageCandidate(
                            baseEdges,
                            scenario,
                            attackerCaps,
                            attackerNationIds,
                            defenderNationIds,
                            fixedEdgeMask,
                            repairSeed,
                            terminalProjection,
                            bestIncomingEdge
                    );
                    if (repaired != null) {
                        candidates.add(repaired);
                        repairSeed = repaired;
                        usedAttacker[baseEdges.attackerIndex(bestIncomingEdge)] = true;
                    }
                }
                return candidates;
            }

            private static int weakestAssignedEdgeForAttacker(
                    CandidateEdgeTable baseEdges,
                    boolean[] fixedEdgeMask,
                    boolean[] edgeAssigned,
                    int attackerIndex
            ) {
                int bestEdge = -1;
                double bestScore = Double.POSITIVE_INFINITY;
                for (int edgeIndex = 0; edgeIndex < edgeAssigned.length; edgeIndex++) {
                    if (!edgeAssigned[edgeIndex]
                            || fixedEdgeMask[edgeIndex]
                            || baseEdges.attackerIndex(edgeIndex) != attackerIndex) {
                        continue;
                    }
                    double score = baseEdges.scalarScore(edgeIndex);
                    if (score < bestScore) {
                        bestScore = score;
                        bestEdge = edgeIndex;
                    }
                }
                return bestEdge;
            }

            private static int weakestAssignedEdgeForDefender(
                    CandidateEdgeTable baseEdges,
                    boolean[] fixedEdgeMask,
                    boolean[] edgeAssigned,
                    int defenderIndex,
                    int excludedEdge
            ) {
                int bestEdge = -1;
                double bestScore = Double.POSITIVE_INFINITY;
                for (int edgeIndex = 0; edgeIndex < edgeAssigned.length; edgeIndex++) {
                    if (edgeIndex == excludedEdge
                            || !edgeAssigned[edgeIndex]
                            || fixedEdgeMask[edgeIndex]
                            || baseEdges.defenderIndex(edgeIndex) != defenderIndex) {
                        continue;
                    }
                    double score = baseEdges.scalarScore(edgeIndex);
                    if (score < bestScore) {
                        bestScore = score;
                        bestEdge = edgeIndex;
                    }
                }
                return bestEdge;
            }

            private static void removeAssignedEdge(
                    CandidateEdgeTable baseEdges,
                    Map<Integer, List<Integer>> assignment,
                    boolean[] edgeAssigned,
                    int[] attackerCounts,
                    int[] defenderCounts,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    int edgeIndex
            ) {
                if (edgeIndex < 0 || edgeIndex >= edgeAssigned.length || !edgeAssigned[edgeIndex]) {
                    return;
                }
                int attackerIndex = baseEdges.attackerIndex(edgeIndex);
                int defenderIndex = baseEdges.defenderIndex(edgeIndex);
                edgeAssigned[edgeIndex] = false;
                attackerCounts[attackerIndex]--;
                defenderCounts[defenderIndex]--;
                List<Integer> targets = assignment.get(attackerNationIds[attackerIndex]);
                if (targets != null) {
                    targets.remove(Integer.valueOf(defenderNationIds[defenderIndex]));
                    if (targets.isEmpty()) {
                        assignment.remove(attackerNationIds[attackerIndex]);
                    }
                }
            }

            private static int bestActiveIncomingEdge(
                    CandidateEdgeTable baseEdges,
                    Candidate seed,
                    boolean[] usedAttacker,
                    int defenderIndex
            ) {
                int bestEdge = -1;
                double bestScore = Double.NEGATIVE_INFINITY;
                int[] attackerCounts = seed.attackerCounts();
                for (int edgeIndex = 0; edgeIndex < baseEdges.edgeCount(); edgeIndex++) {
                    if (baseEdges.defenderIndex(edgeIndex) != defenderIndex) {
                        continue;
                    }
                    int attackerIndex = baseEdges.attackerIndex(edgeIndex);
                    if (attackerCounts[attackerIndex] <= 0 || usedAttacker[attackerIndex]) {
                        continue;
                    }
                    double score = baseEdges.scalarScore(edgeIndex);
                    if (score > bestScore) {
                        bestScore = score;
                        bestEdge = edgeIndex;
                    }
                }
                return bestEdge;
            }

            private static Candidate retargetCoverageCandidate(
                    CandidateEdgeTable baseEdges,
                    CompiledScenario scenario,
                    int[] attackerCaps,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    boolean[] fixedEdgeMask,
                    Candidate seed,
                    LongHorizonControlProjection terminalProjection,
                    int incomingEdge
            ) {
                int attackerIndex = baseEdges.attackerIndex(incomingEdge);
                int defenderIndex = baseEdges.defenderIndex(incomingEdge);
                if (seed.edgeAssigned()[incomingEdge]) {
                    return null;
                }
                int removeEdge = -1;
                if (seed.attackerCounts()[attackerIndex] >= attackerCaps[attackerIndex]) {
                    removeEdge = removableAssignedEdgeForCoverageRepair(
                            baseEdges,
                            scenario,
                            fixedEdgeMask,
                            seed,
                            attackerIndex,
                            defenderIndex
                    );
                    if (removeEdge < 0) {
                        return null;
                    }
                }

                boolean[] edgeAssigned = seed.edgeAssigned().clone();
                int[] attackerCounts = seed.attackerCounts().clone();
                int[] defenderCounts = seed.defenderCounts().clone();
                Map<Integer, List<Integer>> assignment = cloneAssignment(seed.assignment());
                int attackerNationId = attackerNationIds[attackerIndex];
                List<Integer> targets = assignment.computeIfAbsent(attackerNationId, ignored -> new IntArrayList());
                if (removeEdge >= 0) {
                    int removedDefenderIndex = baseEdges.defenderIndex(removeEdge);
                    edgeAssigned[removeEdge] = false;
                    defenderCounts[removedDefenderIndex]--;
                    targets.remove(Integer.valueOf(defenderNationIds[removedDefenderIndex]));
                } else {
                    attackerCounts[attackerIndex]++;
                }
                edgeAssigned[incomingEdge] = true;
                defenderCounts[defenderIndex]++;
                targets.add(defenderNationIds[defenderIndex]);
                double projectionScore = terminalProjection.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts);
                return new Candidate(assignment, edgeAssigned, attackerCounts, defenderCounts, projectionScore);
            }

            private static int removableAssignedEdgeForCoverageRepair(
                    CandidateEdgeTable baseEdges,
                    CompiledScenario scenario,
                    boolean[] fixedEdgeMask,
                    Candidate seed,
                    int attackerIndex,
                    int targetDefenderIndex
            ) {
                int bestEdge = -1;
                double bestRemovalScore = Double.POSITIVE_INFINITY;
                for (int edgeIndex = 0; edgeIndex < baseEdges.edgeCount(); edgeIndex++) {
                    if (!seed.edgeAssigned()[edgeIndex]
                            || fixedEdgeMask[edgeIndex]
                            || baseEdges.attackerIndex(edgeIndex) != attackerIndex
                            || baseEdges.defenderIndex(edgeIndex) == targetDefenderIndex) {
                        continue;
                    }
                    int defenderIndex = baseEdges.defenderIndex(edgeIndex);
                    int defenderCount = seed.defenderCounts()[defenderIndex];
                    double coveredBonus = defenderCount > 1 ? -1_000_000d : 0d;
                    double cityCost = 250d * scenario.defender(defenderIndex).cities();
                    double removalScore = coveredBonus + cityCost + baseEdges.scalarScore(edgeIndex);
                    if (removalScore < bestRemovalScore) {
                        bestRemovalScore = removalScore;
                        bestEdge = edgeIndex;
                    }
                }
                return bestEdge;
            }

            private static boolean[] fixedEdgeMask(
                    CandidateEdgeTable baseEdges,
                    int[] attackerNationIds,
                    int[] defenderNationIds,
                    List<BlitzFixedEdge> fixedEdges
            ) {
                boolean[] fixed = new boolean[baseEdges.edgeCount()];
                if (fixedEdges == null || fixedEdges.isEmpty()) {
                    return fixed;
                }
                Long2IntOpenHashMap edgeIndexByPair = baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
                for (BlitzFixedEdge fixedEdge : fixedEdges) {
                    int edgeIndex = edgeIndexByPair.get(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
                    if (edgeIndex >= 0 && edgeIndex < fixed.length) {
                        fixed[edgeIndex] = true;
                    }
                }
                return fixed;
            }

            private static Candidate selectDiversityHedge(
                    List<Candidate> reliefCandidates,
                    Candidate marginalCandidate,
                    int projectedAuditLimit
            ) {
                if (reliefCandidates.size() <= projectedAuditLimit || SLOT_DENIAL_DIVERSITY_HEDGE_AUDITS <= 0) {
                    return null;
                }
                Candidate best = null;
                int bestDistance = 0;
                for (int index = projectedAuditLimit; index < reliefCandidates.size(); index++) {
                    Candidate candidate = reliefCandidates.get(index);
                    int distance = structuralDistance(marginalCandidate, candidate);
                    if (distance <= 0) {
                        continue;
                    }
                    if (best == null || distance > bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
                return best;
            }

            private static void sortReliefCandidatesByCheapScore(
                    List<Candidate> reliefCandidates,
                    LongHorizonCandidateEvaluator cheapEvaluator,
                    LongHorizonControlProjection terminalProjection
            ) {
                int size = reliefCandidates.size();
                if (size < 2) {
                    return;
                }
                double[] cheapScores = new double[size];
                for (int index = 0; index < size; index++) {
                    cheapScores[index] = cheapEvaluator.score(reliefCandidates.get(index), terminalProjection);
                }
                for (int index = 1; index < size; index++) {
                    Candidate candidate = reliefCandidates.get(index);
                    double candidateScore = cheapScores[index];
                    int cursor = index;
                    while (cursor > 0 && candidateScore > cheapScores[cursor - 1]) {
                        reliefCandidates.set(cursor, reliefCandidates.get(cursor - 1));
                        cheapScores[cursor] = cheapScores[cursor - 1];
                        cursor--;
                    }
                    reliefCandidates.set(cursor, candidate);
                    cheapScores[cursor] = candidateScore;
                }
            }

            private static int structuralDistance(Candidate baseline, Candidate candidate) {
                int distance = 0;
                for (int index = 0; index < baseline.attackerCounts().length; index++) {
                    distance += Math.abs(baseline.attackerCounts()[index] - candidate.attackerCounts()[index]);
                }
                for (int index = 0; index < baseline.defenderCounts().length; index++) {
                    distance += Math.abs(baseline.defenderCounts()[index] - candidate.defenderCounts()[index]);
                }
                for (int index = 0; index < baseline.edgeAssigned().length; index++) {
                    if (baseline.edgeAssigned()[index] != candidate.edgeAssigned()[index]) {
                        distance++;
                    }
                }
                return distance;
            }

            private static Candidate normalizeBudgetCandidate(Candidate candidate, Candidate marginalCandidate) {
                if (candidate == null || candidate == marginalCandidate) {
                    return null;
                }
                return candidate;
            }

            private static boolean respectsAttackerCaps(Candidate candidate, int[] attackerCaps) {
                int[] attackerCounts = candidate.attackerCounts();
                for (int index = 0; index < attackerCounts.length && index < attackerCaps.length; index++) {
                    if (attackerCounts[index] > attackerCaps[index]) {
                        return false;
                    }
                }
                return true;
            }

            private static int[] dynamicCommitmentCaps(
                    CandidateEdgeTable baseEdges,
                    CompiledScenario scenario,
                    int[] attackerCaps,
                    int[] defenderCaps,
                    int[] fixedAttackerCounts
            ) {
                double[] edgeScores = new double[baseEdges.edgeCount()];
                for (int edgeIndex = 0; edgeIndex < edgeScores.length; edgeIndex++) {
                    edgeScores[edgeIndex] = baseEdges.scalarScore(edgeIndex);
                }
                int[] commitmentNeeds = LongHorizonOpeningCommitmentModel.attackerCommitmentNeeds(
                        baseEdges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        edgeScores
                );
                int[] commitmentCaps = new int[attackerCaps.length];
                for (int attackerIndex = 0; attackerIndex < commitmentCaps.length; attackerIndex++) {
                    int fixedCount = attackerIndex < fixedAttackerCounts.length ? fixedAttackerCounts[attackerIndex] : 0;
                    int dynamicNeed = attackerIndex < commitmentNeeds.length ? commitmentNeeds[attackerIndex] : 0;
                    commitmentCaps[attackerIndex] = Math.min(
                            Math.max(0, attackerCaps[attackerIndex]),
                            Math.max(fixedCount, dynamicNeed)
                    );
                }
                return commitmentCaps;
            }

            private static int assignmentPairCount(Map<Integer, List<Integer>> assignment) {
            int pairCount = 0;
            for (List<Integer> defenders : assignment.values()) {
                pairCount += defenders.size();
            }
            return pairCount;
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
                    attackerPlannerSettings,
                    null,
                    LongHorizonMarginalFlowSolver.staticSolveInputs(
                            attackerNationIds,
                            defenderNationIds,
                            fixedEdges
                        ),
                    new LongHorizonMarginalFlowSolver.GraphBuildBuffers()
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
                SidePlannerSettings attackerPlannerSettings,
                boolean[] warmStartEdgeAssigned,
                LongHorizonMarginalFlowSolver.StaticSolveInputs marginalFlowStaticInputs,
                LongHorizonMarginalFlowSolver.GraphBuildBuffers marginalFlowGraphBuffers
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
                fixedEdges,
                marginalFlowStaticInputs,
                marginalFlowGraphBuffers,
                warmStartEdgeAssigned
        );
        double projectionScore = projection.assignmentScoreDense(
                result.edgeAssigned(),
                result.attackerCounts(),
                result.defenderCounts()
        );
        return new Candidate(result, projectionScore);
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
        Long2IntOpenHashMap edgeIndexByPair = baseEdges.edgeIndexByPair(attackerNationIds, defenderNationIds);
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerIndex = scenario.attackerIndexOrMinusOne(entry.getKey());
            if (attackerIndex < 0) {
                continue;
            }
            for (int defenderNationId : entry.getValue()) {
                int defenderIndex = scenario.defenderIndexOrMinusOne(defenderNationId);
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

        private record FollowOnAuditResult(
            Candidate bestCandidate,
            int additionalAudits,
            int promotionAudits,
            int rebalanceAudits
        ) {
        }

    static final class Candidate {
        private Map<Integer, List<Integer>> assignment;
        private final LongHorizonMarginalFlowSolver.Result lazyAssignmentSource;
        private final boolean[] edgeAssigned;
        private final int[] attackerCounts;
        private final int[] defenderCounts;
        private final double projectionScore;
        private final int assignmentPairCount;

        Candidate(
                Map<Integer, List<Integer>> assignment,
                boolean[] edgeAssigned,
                int[] attackerCounts,
                int[] defenderCounts,
                double projectionScore
        ) {
            this.assignment = assignment;
            this.lazyAssignmentSource = null;
            this.edgeAssigned = edgeAssigned;
            this.attackerCounts = attackerCounts;
            this.defenderCounts = defenderCounts;
            this.projectionScore = projectionScore;
            this.assignmentPairCount = LongHorizonAssignmentOptimizer.assignmentPairCount(assignment);
        }

        Candidate(LongHorizonMarginalFlowSolver.Result solveResult, double projectionScore) {
            this.assignment = null;
            this.lazyAssignmentSource = solveResult;
            this.edgeAssigned = solveResult.edgeAssigned();
            this.attackerCounts = solveResult.attackerCounts();
            this.defenderCounts = solveResult.defenderCounts();
            this.projectionScore = projectionScore;
            this.assignmentPairCount = solveResult.assignmentPairCount();
        }

        Map<Integer, List<Integer>> assignment() {
            if (assignment == null) {
                assignment = lazyAssignmentSource == null ? Map.of() : lazyAssignmentSource.assignment();
            }
            return assignment;
        }

        boolean[] edgeAssigned() {
            return edgeAssigned;
        }

        int[] attackerCounts() {
            return attackerCounts;
        }

        int[] defenderCounts() {
            return defenderCounts;
        }

        double projectionScore() {
            return projectionScore;
        }

        int assignmentPairCount() {
            return assignmentPairCount;
        }

        boolean isEmpty() {
            return assignmentPairCount == 0;
        }
    }

    private record DenseAssignment(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
    }

}
