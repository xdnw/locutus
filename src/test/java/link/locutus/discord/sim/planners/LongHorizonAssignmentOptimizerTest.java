package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicEvaluationComponents;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.TeamProjectionView;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongHorizonAssignmentOptimizerTest {
        private static LongHorizonAssignmentOptimizer.ProjectionScoringContext heuristicProjectionContext(
                        StrategicObjective objective
        ) {
                return new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                                objective,
                                SideOpeningSettings.defaults(objective),
                                SideOpeningSettings.defaults(objective),
                                SidePlannerSettings.actingDefaults(),
                                SidePlannerSettings.defaults(),
                                SideProjectionPolicies.heuristic(),
                                SideProjectionPolicies.heuristic()
                );
        }

    @Test
    void longHorizonReSolveAddsPressureToHighNeedTarget() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 900),
                nation(3, 1, 900)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 3_000),
                nation(102, 2, 100)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = pressureScenarioEdges();
        int[] attackerCaps = {1, 1, 1};
        int[] defenderCaps = {3, 3};
        int[] attackerStrengthRanks = {0, 1, 2};
        int[] attackerNationIds = {1, 2, 3};
        int[] defenderNationIds = {101, 102};

        Map<Integer, List<Integer>> shortAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72
        );

        assertEquals(1, targetCount(shortAssignment, 101));
        assertTrue(targetCount(longAssignment, 101) > targetCount(shortAssignment, 101));
        assertEquals(totalPairs(shortAssignment), totalPairs(longAssignment));
    }

    @Test
    void shortHorizonUsesCurrentPrimitiveAssignment() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 900),
                nation(3, 1, 900)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 3_000),
                nation(102, 2, 100)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = pressureScenarioEdges();
        int[] attackerCaps = {1, 1, 1};
        int[] defenderCaps = {3, 3};
        int[] attackerStrengthRanks = {0, 1, 2};
        int[] attackerNationIds = {1, 2, 3};
        int[] defenderNationIds = {101, 102};

        Map<Integer, List<Integer>> primitiveAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        Map<Integer, List<Integer>> optimizerAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                12
        );

        assertEquals(primitiveAssignment, optimizerAssignment);
    }

    @Test
    void deepHorizonDistributesCommitmentAcrossPeerAttackers() {
        // Strong attacker can fill 3 slots greedily, but commitment-aware re-solve at deep
        // horizon should distribute openings so peer attackers are not left idle while the
        // strongest attacker monopolizes every slot.
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(3).build(),
                nation(2, 1, 880),
                nation(3, 1, 860),
                nation(4, 1, 840)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 900),
                nation(103, 2, 900)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = deepCommitmentScenarioEdges();
        int[] attackerCaps = {3, 1, 1, 1};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {0, 1, 2, 3};
        int[] attackerNationIds = {1, 2, 3, 4};
        int[] defenderNationIds = {101, 102, 103};

        Map<Integer, List<Integer>> shortAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                720
        );

        assertEquals(3, shortAssignment.getOrDefault(1, List.of()).size());
        assertEquals(0, distinctCommittedAttackerCount(shortAssignment) - 1);
        assertTrue(distinctCommittedAttackerCount(longAssignment) >= 3,
                "Deep-horizon receding solve should commit at least three distinct attackers when comparable positive-control edges exist");
        assertEquals(totalPairs(shortAssignment), totalPairs(longAssignment));
    }

    @Test
    void projectedControlStartsFromMarginalFlowPortfolioNotRawPrimitiveBaseline() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(3).build(),
                nation(2, 1, 880),
                nation(3, 1, 860),
                nation(4, 1, 840)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 900),
                nation(103, 2, 900)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = deepCommitmentScenarioEdges();
        int[] attackerCaps = {3, 1, 1, 1};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {0, 1, 2, 3};
        int[] attackerNationIds = {1, 2, 3, 4};
        int[] defenderNationIds = {101, 102, 103};

        Map<Integer, List<Integer>> primitiveAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        Map<Integer, List<Integer>> projectedAssignment = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                heuristicProjectionContext(BlitzObjective.CONTROL.objective())
        );

        assertEquals(1, distinctCommittedAttackerCount(primitiveAssignment),
                "The raw primitive baseline in this fixture is intentionally source-collapsed");
        assertTrue(distinctCommittedAttackerCount(projectedAssignment) >= 3,
                "Projected CONTROL should not let the raw primitive baseline override the marginal-flow opening portfolio");
        assertEquals(totalPairs(primitiveAssignment), totalPairs(projectedAssignment));
    }

    @Test
    void boundedPortfolioRepairsVisibleUncoveredHighCityDefender() {
        List<DBNationSnapshot> attackers = List.of(
                strategicNation(1, 1, 0, 40, 1.0d, 1)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                strategicNation(102, 2, 1, 40, 1.0d, 1)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 90.0f, 0.0f);

        Map<Integer, List<Integer>> assignment = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                new int[]{1},
                new int[]{1, 1},
                new int[]{0},
                new int[]{1},
                new int[]{101, 102},
                List.of(),
                72,
                heuristicProjectionContext(new SlotDenialNeutralObjective())
        );

        assertEquals(List.of(102), assignment.get(1),
                "Projected comparison should audit a retargeting shape for visible uncovered high-city defenders");
    }

    @Test
    void longHorizonOutputIsDeterministicAcrossRepeatedRuns() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 900),
                nation(3, 1, 900)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 3_000),
                nation(102, 2, 100)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = pressureScenarioEdges();
        int[] attackerCaps = {1, 1, 1};
        int[] defenderCaps = {3, 3};
        int[] attackerStrengthRanks = {0, 1, 2};
        int[] attackerNationIds = {1, 2, 3};
        int[] defenderNationIds = {101, 102};

        Map<Integer, List<Integer>> first = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges, scenario, attackerCaps, defenderCaps, attackerStrengthRanks,
                attackerNationIds, defenderNationIds, List.of(), 360
        );
        Map<Integer, List<Integer>> second = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges, scenario, attackerCaps, defenderCaps, attackerStrengthRanks,
                attackerNationIds, defenderNationIds, List.of(), 360
        );
        assertEquals(first, second);
    }

    @Test
    void longHorizonCommitsComparablePeerBeforeMaxingOneAttacker() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).build(),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 900)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = commitmentScenarioEdges();
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};

        Map<Integer, List<Integer>> shortAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72
        );

        assertEquals(List.of(101, 102), shortAssignment.get(1));
        assertTrue(!shortAssignment.containsKey(2) || shortAssignment.get(2).isEmpty());
        assertEquals(List.of(101), longAssignment.get(1));
        assertEquals(List.of(102), longAssignment.get(2));
        assertEquals(totalPairs(shortAssignment), totalPairs(longAssignment));
    }

    @Test
    void counterfactualScoringDoesNotCollapseRawPressureCandidate() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(3).build(),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 900),
                nation(103, 2, 900)
        );
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = counterfactualScenarioEdges();
        int[] attackerCaps = {3, 1};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103};

        Map<Integer, List<Integer>> projectionOnly = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72
        );
        Map<Integer, List<Integer>> counterfactual = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                13,
                heuristicProjectionContext(new WarCountAvoidanceObjective())
        );

        assertEquals(3, totalPairs(projectionOnly));
        assertEquals(totalPairs(projectionOnly), totalPairs(counterfactual),
                "Projected-objective portfolio scoring composes with raw pressure and must not collapse useful openings for a war-count-only objective");
    }

    @Test
        void slotPressurePortfolioRemainsAvailableBeforeProjectedTerminalComparison() {
        List<DBNationSnapshot> attackers = new ArrayList<>();
        List<DBNationSnapshot> defenders = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            DBNationSnapshot.Builder attacker = strategicNation(10_000 + index, 1, index, 23 + index % 4, 1.05d, 3)
                    .toBuilder()
                    .currentOffensiveWars(index % 3)
                    .currentDefensiveWars(index % 2)
                    .activeOpponentNationId(30_000 + index);
            if ((index & 1) == 0) {
                attacker.activeOpponentNationId(31_000 + index);
            }
            attackers.add(attacker.build());

            defenders.add(strategicNation(20_000 + index, 2, index, 23 + index % 4, 1.05d, 1)
                    .toBuilder()
                    .currentDefensiveWars(index % 3)
                    .activeOpponentNationId(40_000 + index)
                    .build());
        }
        CompiledScenario scenario = compile(attackers, defenders);
        int[] attackerCaps = new int[attackers.size()];
        int[] defenderCaps = new int[defenders.size()];
        int[] attackerStrengthRanks = new int[attackers.size()];
        int[] attackerNationIds = new int[attackers.size()];
        int[] defenderNationIds = new int[defenders.size()];
        for (int index = 0; index < attackers.size(); index++) {
            attackerCaps[index] = OverrideSet.EMPTY.effectiveFreeOff(attackers.get(index));
            attackerStrengthRanks[index] = index;
            attackerNationIds[index] = scenario.attackerNationId(index);
            defenderCaps[index] = OverrideSet.EMPTY.effectiveFreeDef(defenders.get(index));
            defenderNationIds[index] = scenario.defenderNationId(index);
        }
        CandidateEdgeTable edges = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                SimTuning.defaults(),
                OverrideSet.EMPTY,
                BlitzObjective.NET_DAMAGE.objective(),
                attackerCaps.clone(),
                defenderCaps.clone(),
                edges
        );

        Map<Integer, List<Integer>> primitiveAssignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds
        );
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        LongHorizonMarginalFlowSolver.Result marginalSeed = LongHorizonMarginalFlowSolver.solve(
                edges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonAssignmentOptimizer.Candidate marginalCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                marginalSeed.assignment(),
                marginalSeed.edgeAssigned(),
                marginalSeed.attackerCounts(),
                marginalSeed.defenderCounts(),
                projection.assignmentScoreDense(
                        marginalSeed.edgeAssigned(),
                        marginalSeed.attackerCounts(),
                        marginalSeed.defenderCounts()
                )
        );
        LongHorizonCandidateEvaluator projectedEvaluator = LongHorizonCandidateEvaluator.create(
                scenario,
                heuristicProjectionContext(BlitzObjective.NET_DAMAGE.objective())
        );
        double marginalProjectedObjective = projectedEvaluator.objectiveSummary(marginalCandidate, projection).mean();

        LongHorizonAssignmentOptimizer.Result result = LongHorizonAssignmentOptimizer.solveDetailed(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                heuristicProjectionContext(BlitzObjective.NET_DAMAGE.objective())
        );

        assertTrue(totalPairs(primitiveAssignment) > 0,
                "Capability-based slot denial should still produce a non-empty primitive slot-pressure family"
                        + " (edgeCount=" + edges.edgeCount() + ')');
        assertTrue(totalPairs(marginalSeed.assignment()) > 0,
                "Capability-based slot denial should still produce a non-empty marginal slot-pressure family"
                        + " (primitivePairs=" + totalPairs(primitiveAssignment)
                        + ", marginalPairs=" + totalPairs(marginalSeed.assignment()) + ')');
        assertTrue(Double.isFinite(marginalProjectedObjective),
                "Projected NET_DAMAGE comparison should remain numerically well-defined for the capability-based slot-pressure family"
                        + " (marginalProjectedMean=" + marginalProjectedObjective + ')');
        assertNotNull(result.projectedObjectiveSummary(),
                "Projected-objective summary should remain available even when capability-based slot valuation rejects the scorer-side family");
        assertTrue(Double.isFinite(result.projectedObjectiveSummary().mean()),
                "Projected-objective summary should remain finite under capability-based slot valuation"
                        + " (projectedMean=" + result.projectedObjectiveSummary().mean()
                        + ", pairCount=" + totalPairs(result.assignment()) + ')');
    }

    @Test
    void emptyProjectedCandidateUsesTerminalObjectiveBaseline() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 100), 100.0));
        List<DBNationSnapshot> defenders = List.of(withTotalScore(nation(101, 2, 300), 300.0));
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{0},
                new int[]{0},
                72,
                1.0d
        );
        LongHorizonAssignmentOptimizer.Candidate emptyCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(),
                new boolean[0],
                new int[]{0},
                new int[]{0},
                0d
        );
        TeamDifferenceObjective objective = new TeamDifferenceObjective();
        LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(
                scenario,
                heuristicProjectionContext(objective)
        );
        double projectedBaseline = projection.projectedEvaluation(
                objective,
                attackers.get(0).teamId(),
                emptyCandidate.edgeAssigned(),
                emptyCandidate.attackerCounts(),
                emptyCandidate.defenderCounts()
        ).objectiveScore();

        ObjectiveValueSummary summary = evaluator.objectiveSummary(emptyCandidate, projection);

        assertNotNull(summary);
        assertTrue(projectedBaseline < 0d, "Test setup must produce a non-neutral no-opening terminal baseline");
        assertEquals(projectedBaseline, summary.mean(), 1e-6,
                "Empty projected candidates must use the real no-opening terminal objective instead of a magic neutral baseline");
    }

    @Test
    void projectedTerminalSlotCostIsMarginalAcrossMultipleWars() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 1_200).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 1_000),
                nation(102, 2, 1_000)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 100.0f, 0.0f);
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{2},
                new int[]{1, 1},
                1,
                1.0d
        );
        OffensiveSlotCostObjective objective = new OffensiveSlotCostObjective();

        double oneWarCost = projection.projectedEvaluation(
                objective,
                attackers.get(0).teamId(),
                new boolean[]{true, false},
                new int[]{1},
                new int[]{1, 0}
        ).objectiveScore();
        double twoWarCost = projection.projectedEvaluation(
                objective,
                attackers.get(0).teamId(),
                new boolean[]{true, true},
                new int[]{2},
                new int[]{1, 1}
        ).objectiveScore();

        assertTrue(oneWarCost > 0d, "Test setup must expose positive offensive slot cost");
        assertTrue(twoWarCost > oneWarCost, "A second active war can still increase total slot cost through higher slot pressure");
        assertTrue(twoWarCost < oneWarCost * 2d,
                "Terminal slot metrics should allocate current nation slot cost across active wars instead of repeating full cost per war");
    }

    @Test
    void forwardProjectionDerivesScoreFromMutableStateAndCurrentRebuyCapacity() {
                DBNationSnapshot attackerNoBuysUsed = noCurrentBuysNationWithTotalScore(1, 1, 100.0);
                DBNationSnapshot attackerNoRebuyLeft = exhaustedBuysNationWithTotalScore(1, 1, 100.0);
                DBNationSnapshot defender = exhaustedBuysNationWithTotalScore(101, 2, 50.0);
        CandidateEdgeTable edges = new CandidateEdgeTable();

        double noBuysUsedScore = emptyProjectionScore(List.of(attackerNoBuysUsed), List.of(defender), edges, 1);
        double noRebuyLeftScore = emptyProjectionScore(List.of(attackerNoRebuyLeft), List.of(defender), edges, 1);

        assertTrue(noBuysUsedScore > noRebuyLeftScore,
                "Forward projection should credit remaining current-day buy capacity instead of treating rebuy mode as cosmetic");
        assertEquals(50.0, noRebuyLeftScore, 1e-6,
                "Forward projection should derive score from non-infra score, city infra, units, and pending buys instead of snapshot.score");
    }

    @Test
    void forwardProjectionMutatesDenseWarResistanceAndControlState() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 1_200)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{1},
                new int[]{1},
                1,
                1.0d
        );

        LongHorizonForwardProjection.MidHorizonSnapshot assignedSnapshot = projection.snapshotMidHorizonState(
                new boolean[]{true},
                new int[]{1},
                new int[]{1}
        );
        assertTrue(
                assignedSnapshot.defenderStrengthsMid()[0] < assignedSnapshot.defenderStrengthsBaseline()[0]
                        || assignedSnapshot.attackerStrengthsMid()[0] < assignedSnapshot.attackerStrengthsBaseline()[0],
                "Forward projection should mutate projected combat strength when an opening edge is assigned"
        );
        assertTrue(
                assignedSnapshot.defenderScoresMid()[0] < assignedSnapshot.defenderScoresBaseline()[0]
                        || assignedSnapshot.attackerScoresMid()[0] != assignedSnapshot.attackerScoresBaseline()[0],
                "Forward projection should also mutate projected score state through dense combat buffers"
        );
    }

    @Test
    void attackerOnlyFeedbackMatchesFullFeedbackForFixedPointInputs() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 1_200),
                nation(2, 1, 1_050)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 980),
                nation(102, 2, 900)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 90.0f, 0.0f);
        edges.add(1, 0, 95.0f, 0.0f);
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{1, 1},
                new int[]{1, 1},
                72,
                1.0d
        );

        LongHorizonForwardProjection.ProjectedFeedbackEvaluation fullFeedback = projection.projectedFeedbackEvaluation(
                BlitzObjective.NET_DAMAGE.objective(),
                1,
                new boolean[]{true, false, true},
                new int[]{1, 1},
                new int[]{2, 0}
        );
        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation attackerFeedback = projection.projectedAttackerFeedbackEvaluation(
                BlitzObjective.NET_DAMAGE.objective(),
                1,
                new boolean[]{true, false, true},
                new int[]{1, 1},
                new int[]{2, 0}
        );

        assertEquals(
                fullFeedback.projectedEvaluation().objectiveScore(),
                attackerFeedback.projectedEvaluation().objectiveScore(),
                1e-9
        );
        assertArrayEquals(
                fullFeedback.projectedEvaluation().realizedCounterIncidence(),
                attackerFeedback.projectedEvaluation().realizedCounterIncidence()
        );
        for (int attackerIndex = 0; attackerIndex < 2; attackerIndex++) {
            assertEquals(
                    fullFeedback.midHorizonSnapshot().attackerEdgeFactor(attackerIndex),
                    attackerFeedback.attackerMidHorizonSnapshot().attackerEdgeFactor(attackerIndex),
                    1e-9
            );
        }
    }

    @Test
    void forwardProjectionLimitsProjectedBuysWhenResourcesAreKnownAndInsufficient() {
        DBNationSnapshot unknownResourceAttacker = noCurrentBuysNationWithTotalScore(1, 1, 100.0);
        DBNationSnapshot constrainedAttacker = noCurrentBuysNationWithTotalScore(1, 1, 100.0)
                .toBuilder()
                .resource(ResourceType.CREDITS, 1.0)
                .build();
        DBNationSnapshot defender = exhaustedBuysNationWithTotalScore(101, 2, 50.0);
        CandidateEdgeTable edges = new CandidateEdgeTable();

        double unknownResourceScore = emptyProjectionScore(List.of(unknownResourceAttacker), List.of(defender), edges, 1);
        double constrainedScore = emptyProjectionScore(List.of(constrainedAttacker), List.of(defender), edges, 1);

        assertTrue(unknownResourceScore > constrainedScore,
                "Projection should preserve capacity-only behavior for absent resource payloads but honor explicit insufficient resources when present");
        assertEquals(50.0, constrainedScore, 1e-6);
    }

    @Test
    void counterOpportunityCostFeedsBackIntoOpeningAssignment() {
        List<DBNationSnapshot> vulnerableAttackers = List.of(
                withTotalScore(nation(1, 1, 80), 1_600.0).toBuilder().maxOff(2).build(),
                nation(2, 1, 900)
        );
        List<DBNationSnapshot> passiveDefenders = List.of(
                nation(101, 2, 1_500).toBuilder().maxOff(0).build(),
                nation(102, 2, 1_500).toBuilder().maxOff(0).build()
        );
        List<DBNationSnapshot> counterCapableDefenders = List.of(
                nation(101, 2, 1_500).toBuilder().maxOff(1).build(),
                nation(102, 2, 1_500).toBuilder().maxOff(1).build()
        );
        CandidateEdgeTable edges = counterPressureAssignmentEdges();
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};

        Map<Integer, List<Integer>> noCounterAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                compile(vulnerableAttackers, passiveDefenders),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                720
        );
        Map<Integer, List<Integer>> counterAwareAssignment = LongHorizonAssignmentOptimizer.solveHeuristic(
                edges,
                compile(vulnerableAttackers, counterCapableDefenders),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                720
        );

        assertEquals(2, noCounterAssignment.getOrDefault(1, List.of()).size(),
                "Without counter capacity, raw opening score should still be able to use the vulnerable attacker's slots");
        assertEquals(1, counterAwareAssignment.getOrDefault(1, List.of()).size());
        assertEquals(1, counterAwareAssignment.getOrDefault(2, List.of()).size(),
                "Expected counter pressure should be priced before opening output, not patched after assignment");
    }

    @Test
    void recedingFeedbackIteratesUntilProjectedCounterPressureStabilizes() {
        // Vulnerable attacker (id=1) has 3 offensive slots and outranks the strong attacker on
        // candidate edge score, so the primitive seed loads it heavily. Counter-capable defenders
        // make that shape risky, and fixed-point feedback must at least give the viable strong peer
        // real capacity to absorb some of the defended target set.
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(3).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> counterCapableDefenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build(),
                nation(104, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, counterCapableDefenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.5f, 0.0f);
        edges.add(0, 2, 99.0f, 0.0f);
        edges.add(0, 3, 98.5f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 1, 79.5f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        edges.add(1, 3, 78.5f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103, 104};
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                13,
                1.0d
        );
        int[] seedCounters = projection.realizedCounterIncidence(
                new boolean[]{true, true, true, false, false, false, false, false},
                new int[]{3, 0},
                new int[]{1, 1, 1, 0}
        );
        assertTrue(seedCounters[0] >= 2,
                "Test setup must project repeated counters against the loaded vulnerable attacker: " + java.util.Arrays.toString(seedCounters));

        Map<Integer, List<Integer>> assignment = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                13,
                heuristicProjectionContext(new CounterAdjustedForwardWarObjective())
        );

        int strongCount = assignment.getOrDefault(2, List.of()).size();
        assertTrue(strongCount >= 1,
                "Slots relieved from the over-countered attacker should be picked up by viable peers, not silently dropped");
    }

    @Test
    void recedingFeedbackCanDropNonFixedSingleWarWhenProjectedCountersWipeIt() {
        // The fixed-point loop must not preserve a hidden one-war participation floor. If a
        // non-fixed attacker has only one assigned opening war but the dense projection realizes
        // multiple counters against that attacker, the loop should be allowed to reduce its cap to
        // zero and let a viable peer take the target.
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(1).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 200.0f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        int[] attackerCaps = {1, 1};
        int[] defenderCaps = {1, 0};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                13,
                1.0d
        );
        int[] vulnerableCounters = projection.realizedCounterIncidence(
                new boolean[]{true, false},
                new int[]{1, 0},
                new int[]{1, 0}
        );
        assertTrue(vulnerableCounters[0] >= 2,
                "Test setup must project multiple counters against the vulnerable single-war opener");
        LongHorizonControlProjection peerProjection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{0, 1},
                defenderCaps,
                13,
                1.0d
        );
        LongHorizonMarginalFlowSolver.Result peerOnly = LongHorizonMarginalFlowSolver.solve(
                edges,
                peerProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                new int[]{0, 1},
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        assertEquals(List.of(101), peerOnly.assignment().getOrDefault(2, List.of()),
                "Test setup must allow the peer to take the relieved target");
        int[] peerCounters = projection.realizedCounterIncidence(
                new boolean[]{false, true},
                new int[]{0, 1},
                new int[]{1, 0}
        );
        assertTrue(peerCounters[1] < 2,
                "Test setup must not over-counter the strong peer: " + java.util.Arrays.toString(peerCounters));
        LongHorizonMarginalFlowSolver.Result marginalSeed = LongHorizonMarginalFlowSolver.solve(
                edges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        assertTrue(totalPairs(marginalSeed.assignment()) > 0,
                "Test setup must produce a non-empty marginal seed: " + marginalSeed.assignment());

        Map<Integer, List<Integer>> assignment = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                13,
                heuristicProjectionContext(new CounterAdjustedForwardWarObjective())
        );

        assertEquals(0, assignment.getOrDefault(1, List.of()).size(),
                "Projected wipe risk should be able to reject the vulnerable attacker's only non-fixed war");
        assertEquals(List.of(101), assignment.getOrDefault(2, List.of()),
                "A viable peer should take the target when the over-countered attacker's cap is relieved: " + assignment);
    }

        @Test
        void selectiveReliefCandidatesReachDeeperModerateCapsForMultipleOverextendedAttackers() {
                List<DBNationSnapshot> attackers = List.of(
                                nation(1, 1, 900).toBuilder().maxOff(7).build(),
                                nation(2, 1, 880).toBuilder().maxOff(7).build(),
                                nation(3, 1, 860).toBuilder().maxOff(7).build()
                );
                List<DBNationSnapshot> defenders = new java.util.ArrayList<>();
                for (int defenderId = 101; defenderId < 122; defenderId++) {
                        defenders.add(nation(defenderId, 2, 900).toBuilder().maxOff(0).build());
                }
                CompiledScenario scenario = compile(attackers, defenders);
                CandidateEdgeTable edges = new CandidateEdgeTable();
                for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
                        for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
                                edges.add(attackerIndex, defenderIndex, (float) (100.0 - attackerIndex - (defenderIndex * 0.01)), 0.0f);
                        }
                }
                int[] attackerCaps = {7, 7, 7};
                int[] defenderCaps = new int[defenders.size()];
                java.util.Arrays.fill(defenderCaps, 1);
                int[] attackerStrengthRanks = {0, 1, 2};
                int[] attackerNationIds = {1, 2, 3};
                int[] defenderNationIds = new int[defenders.size()];
                for (int index = 0; index < defenders.size(); index++) {
                        defenderNationIds[index] = defenders.get(index).nationId();
                }

                LongHorizonAssignmentOptimizer.Candidate seed = LongHorizonAssignmentOptimizer.solveWithAttackerCaps(
                                edges,
                                scenario,
                                attackerCaps,
                                defenderCaps,
                                attackerStrengthRanks,
                                attackerNationIds,
                                defenderNationIds,
                                List.of(),
                                72,
                                false,
                                SidePlannerSettings.actingDefaults()
                );
                LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                                edges,
                                scenario,
                                attackerCaps,
                                defenderCaps,
                                attackerStrengthRanks,
                                72,
                                1.0d,
                                false,
                                SidePlannerSettings.actingDefaults()
                );
                LongHorizonMarginalFlowSolver.StaticSolveInputs staticSolveInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                                attackerNationIds,
                                defenderNationIds,
                                List.of()
                );
                LongHorizonMarginalFlowSolver.GraphBuildBuffers graphBuffers =
                                new LongHorizonMarginalFlowSolver.GraphBuildBuffers();

                List<LongHorizonAssignmentOptimizer.Candidate> reliefCandidates = LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                                edges,
                                scenario,
                                attackerCaps,
                                defenderCaps,
                                attackerStrengthRanks,
                                attackerNationIds,
                                defenderNationIds,
                                List.of(),
                                LongHorizonFeedbackSearch.fixedAttackerCounts(List.of(), attackerNationIds),
                                72,
                                seed,
                                projection,
                                new int[]{5, 5, 0},
                                SidePlannerSettings.actingDefaults(),
                                staticSolveInputs,
                                graphBuffers
                );

                assertTrue(
                                reliefCandidates.stream().anyMatch(candidate -> candidate.attackerCounts()[0] <= 3 && candidate.attackerCounts()[1] <= 3),
                                "Selective relief should be able to reach deeper moderate-cap variants when multiple attackers are heavily overextended"
                );
        }

            @Test
            void selectiveReliefCandidatesProvideFallbackVariantBelowOvercounterThreshold() {
                List<DBNationSnapshot> attackers = List.of(
                                withTotalScore(nation(1, 1, 80), 1_600.0).toBuilder().maxOff(2).build(),
                                nation(2, 1, 900)
                );
                List<DBNationSnapshot> defenders = List.of(
                                nation(101, 2, 1_500).toBuilder().maxOff(1).build(),
                                nation(102, 2, 1_500).toBuilder().maxOff(1).build()
                );
                CompiledScenario scenario = compile(attackers, defenders);
                CandidateEdgeTable edges = counterPressureAssignmentEdges();
                int[] attackerCaps = {2, 1};
                int[] defenderCaps = {1, 1};
                int[] attackerStrengthRanks = {1, 0};
                int[] attackerNationIds = {1, 2};
                int[] defenderNationIds = {101, 102};

                LongHorizonAssignmentOptimizer.Candidate seed = new LongHorizonAssignmentOptimizer.Candidate(
                                Map.of(1, new IntArrayList(List.of(101, 102))),
                                new boolean[]{true, true, false, false},
                                new int[]{2, 0},
                                new int[]{1, 1},
                                0d
                );
                LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                        edges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        72,
                        1.0d,
                        false,
                        SidePlannerSettings.actingDefaults()
                );
                LongHorizonMarginalFlowSolver.StaticSolveInputs staticSolveInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                        attackerNationIds,
                        defenderNationIds,
                        List.of()
                );
                LongHorizonMarginalFlowSolver.GraphBuildBuffers graphBuffers =
                        new LongHorizonMarginalFlowSolver.GraphBuildBuffers();

                assertTrue(-projection.attackerCounterOpportunityMarginalScore(0, seed.attackerCounts()[0] - 1) > 0d,
                        "Test setup must expose positive counter-pressure on the primary attacker");

                List<LongHorizonAssignmentOptimizer.Candidate> reliefCandidates = LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                        edges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        attackerStrengthRanks,
                        attackerNationIds,
                        defenderNationIds,
                        List.of(),
                        LongHorizonFeedbackSearch.fixedAttackerCounts(List.of(), attackerNationIds),
                        72,
                        seed,
                        projection,
                        new int[]{1, 0},
                        SidePlannerSettings.actingDefaults(),
                        staticSolveInputs,
                        graphBuffers
                );

                assertTrue(
                        reliefCandidates.stream().anyMatch(candidate -> candidate.attackerCounts()[0] < seed.attackerCounts()[0]),
                        "Selective relief should emit a one-step safer partial variant when counter-pressure exists below the overcounter threshold"
                );
            }

    @Test
    void midHorizonSnapshotReducesAttackerEdgeFactorAfterProjectedCounters() {
        // A vulnerable attacker exposed to a counter-capable defender for long enough should have
        // a projected mid-horizon edge factor strictly less than 1.0; a passive defender should
        // leave the factor at 1.0. This locks in that the optimizer's edge rebuild is keyed off
        // real projected nation state, not a fixed scalar penalty.
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> counterCapableDefenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> passiveDefenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        LongHorizonControlProjection counterCapableProjection = LongHorizonControlProjection.createHeuristic(
                edges,
                compile(attackers, counterCapableDefenders),
                new int[]{1},
                new int[]{1},
                72,
                1.0d
        );
        LongHorizonControlProjection passiveProjection = LongHorizonControlProjection.createHeuristic(
                edges,
                compile(attackers, passiveDefenders),
                new int[]{1},
                new int[]{1},
                72,
                1.0d
        );

        LongHorizonForwardProjection.MidHorizonSnapshot counterSnapshot = counterCapableProjection.snapshotMidHorizonState(
                new boolean[]{true},
                new int[]{1},
                new int[]{1}
        );
        LongHorizonForwardProjection.MidHorizonSnapshot passiveSnapshot = passiveProjection.snapshotMidHorizonState(
                new boolean[]{true},
                new int[]{1},
                new int[]{1}
        );

        double counterFactor = counterSnapshot.attackerEdgeFactor(0);
        double passiveFactor = passiveSnapshot.attackerEdgeFactor(0);
        assertTrue(counterFactor < passiveFactor,
                "Mid-horizon edge factor should drop when projected state shows real counter damage");
        assertTrue(counterFactor < 1.0d,
                "An attacker absorbing projected later declarations should not project to its full baseline strength + score");
    }

    @Test
    void feedbackEvaluationMatchesSeparateTerminalAndMidHorizonProjectionReads() {
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(3).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103};
        CounterAdjustedForwardWarObjective objective = new CounterAdjustedForwardWarObjective();
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                72,
                1.0d
        );
        LongHorizonMarginalFlowSolver.Result seed = LongHorizonMarginalFlowSolver.solve(
                edges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonAssignmentOptimizer.Candidate candidate = new LongHorizonAssignmentOptimizer.Candidate(
                seed.assignment(),
                seed.edgeAssigned(),
                seed.attackerCounts(),
                seed.defenderCounts(),
                projection.assignmentScoreDense(seed.edgeAssigned(), seed.attackerCounts(), seed.defenderCounts())
        );
        LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(
                scenario,
                heuristicProjectionContext(objective)
        );

        LongHorizonForwardProjection.ProjectedEvaluation separateEvaluation = projection.projectedEvaluation(
                objective,
                attackers.get(0).teamId(),
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts()
        );
        LongHorizonForwardProjection.MidHorizonSnapshot separateSnapshot = projection.snapshotMidHorizonState(
                candidate.edgeAssigned(),
                candidate.attackerCounts(),
                candidate.defenderCounts()
        );
        LongHorizonForwardProjection.ProjectedFeedbackEvaluation combined = evaluator.feedbackEvaluation(candidate, projection);

        assertNotNull(combined.midHorizonSnapshot());
        assertEquals(separateEvaluation.objectiveScore(), combined.projectedEvaluation().objectiveScore(), 1e-6);
        assertArrayEquals(separateEvaluation.realizedCounterIncidence(), combined.projectedEvaluation().realizedCounterIncidence());
        assertArrayEquals(separateSnapshot.realizedCounterIncidence(), combined.midHorizonSnapshot().realizedCounterIncidence());
        assertEquals(separateSnapshot.attackerEdgeFactor(0), combined.midHorizonSnapshot().attackerEdgeFactor(0), 1e-9);
        assertEquals(separateSnapshot.attackerEdgeFactor(1), combined.midHorizonSnapshot().attackerEdgeFactor(1), 1e-9);
        assertEquals(separateSnapshot.defenderEdgeFactor(0), combined.midHorizonSnapshot().defenderEdgeFactor(0), 1e-9);
        assertEquals(separateSnapshot.defenderEdgeFactor(1), combined.midHorizonSnapshot().defenderEdgeFactor(1), 1e-9);
        assertEquals(separateSnapshot.defenderEdgeFactor(2), combined.midHorizonSnapshot().defenderEdgeFactor(2), 1e-9);
    }

    @Test
    void feedbackEvaluationReusesProjectedCacheForEquivalentCandidateState() {
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(3).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103};
        CounterAdjustedForwardWarObjective objective = new CounterAdjustedForwardWarObjective();
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                72,
                1.0d
        );
        LongHorizonMarginalFlowSolver.Result seed = LongHorizonMarginalFlowSolver.solve(
                edges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonAssignmentOptimizer.Candidate firstCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                seed.assignment(),
                seed.edgeAssigned(),
                seed.attackerCounts(),
                seed.defenderCounts(),
                projection.assignmentScoreDense(seed.edgeAssigned(), seed.attackerCounts(), seed.defenderCounts())
        );
        LongHorizonAssignmentOptimizer.Candidate equivalentCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                seed.assignment(),
                Arrays.copyOf(seed.edgeAssigned(), seed.edgeAssigned().length),
                Arrays.copyOf(seed.attackerCounts(), seed.attackerCounts().length),
                Arrays.copyOf(seed.defenderCounts(), seed.defenderCounts().length),
                firstCandidate.projectionScore()
        );
        LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(
                scenario,
                heuristicProjectionContext(objective)
        );

        LongHorizonForwardProjection.ProjectedFeedbackEvaluation first = evaluator.feedbackEvaluation(firstCandidate, projection);
        LongHorizonForwardProjection.ProjectedFeedbackEvaluation second = evaluator.feedbackEvaluation(equivalentCandidate, projection);

        assertSame(first, second,
                "Equivalent dense candidate states should reuse one projected feedback artifact even when built as new objects");
        assertSame(first.projectedEvaluation(), second.projectedEvaluation(),
                "Equivalent dense candidate states should reuse the same terminal projected evaluation instance");
    }

    @Test
    void smallProjectedPortfolioStillHonorsAuditBudget() {
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(3).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build(),
                nation(104, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.5f, 0.0f);
        edges.add(0, 2, 99.0f, 0.0f);
        edges.add(0, 3, 98.5f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 1, 79.5f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        edges.add(1, 3, 78.5f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103, 104};
        PlannerProfiler.Session session = new PlannerProfiler.Session();

        PlannerProfiler.withSession(session, () -> LongHorizonAssignmentOptimizer.solveDetailed(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new CounterAdjustedForwardWarObjective(),
                        SideOpeningSettings.defaults(new CounterAdjustedForwardWarObjective()),
                        SideOpeningSettings.defaults(new CounterAdjustedForwardWarObjective()),
                        SidePlannerSettings.defaults().withProjectedAuditLimit(1),
                        SidePlannerSettings.defaults(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        ));
        PlannerProfiler.ProfileSnapshot snapshot = session.snapshot();

        PlannerProfiler.ScopeStats solveStats = snapshot.stats(PlannerProfiler.Scope.LONG_HORIZON_SOLVE);
        assertEquals(1L, solveStats.counters().getOrDefault("boundedProjectedPortfolio", 0L),
                "Small projected portfolios should now flow through the canonical bounded audit owner");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedReliefAudits", 0L) <= 1L,
                "Projected audit limit 1 should cap the replay-heavy relief family even on small portfolios");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedAudits", 0L)
                        <= solveStats.counters().getOrDefault("boundedProjectedCandidates", 0L),
                "The small-portfolio path should audit only generated bounded variants");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedFollowOnPromotionAudits", 0L) <= 2L,
                "Follow-on promotion should remain bounded to the current best and marginal-flow seeds, not replay every later declaration");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedFollowOnRebalanceAudits", 0L) <= 2L,
                "Follow-on rebalance should stay bounded to one structural swap candidate per audited seed");
        assertFalse(solveStats.counters().containsKey("fixedPointFeedbackDeferred"),
                "Fixed-point feedback should remain on the dedicated path instead of being deferred by the portfolio owner");
    }

    @Test
    void fixedPointFeedbackDefersForLargeProjectedPortfolios() {
        assertTrue(LongHorizonAssignmentOptimizer.shouldRunFixedPointFeedback(1_500, 150),
                "Feedback search should stay enabled at the verified small-portfolio boundary");
        assertFalse(LongHorizonAssignmentOptimizer.shouldRunFixedPointFeedback(1_501, 150),
                "Edge counts above the verified boundary should defer replay-heavy fixed-point feedback");
        assertFalse(LongHorizonAssignmentOptimizer.shouldRunFixedPointFeedback(1_500, 151),
                "Assignment-pair counts above the verified boundary should defer replay-heavy fixed-point feedback");
    }

    @Test
    void largeProjectedPortfolioRecordsFixedPointFeedbackDeferral() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 10_000).toBuilder().maxOff(40).build(),
                nation(2, 1, 9_900).toBuilder().maxOff(40).build()
        );
        List<DBNationSnapshot> defenders = new ArrayList<>();
        for (int defenderIndex = 0; defenderIndex < 40; defenderIndex++) {
            defenders.add(nation(101 + defenderIndex, 2, 900).toBuilder().maxOff(1).build());
        }
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        for (int attackerIndex = 0; attackerIndex < attackers.size(); attackerIndex++) {
            for (int defenderIndex = 0; defenderIndex < defenders.size(); defenderIndex++) {
                for (int duplicate = 0; duplicate < 20; duplicate++) {
                    edges.add(attackerIndex, defenderIndex, 100.0f - defenderIndex - duplicate * 0.01f, 0.0f);
                }
            }
        }
        int[] attackerCaps = {40, 40};
        int[] defenderCaps = new int[defenders.size()];
        Arrays.fill(defenderCaps, 2);
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = defenders.stream().mapToInt(DBNationSnapshot::nationId).toArray();
        PlannerProfiler.Session session = new PlannerProfiler.Session();

        PlannerProfiler.withSession(session, () -> LongHorizonAssignmentOptimizer.solveDetailed(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new CounterAdjustedForwardWarObjective(),
                        SideOpeningSettings.defaults(new CounterAdjustedForwardWarObjective()),
                        SideOpeningSettings.defaults(new CounterAdjustedForwardWarObjective()),
                        SidePlannerSettings.defaults().withProjectedAuditLimit(1),
                        SidePlannerSettings.defaults(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        ));
        PlannerProfiler.ProfileSnapshot snapshot = session.snapshot();

        PlannerProfiler.ScopeStats solveStats = snapshot.stats(PlannerProfiler.Scope.LONG_HORIZON_SOLVE);
        assertEquals(1L, solveStats.counters().getOrDefault("fixedPointFeedbackDeferred", 0L),
                "Large projected portfolios should defer replay-heavy fixed-point feedback until the cheaper path exists");
    }

    @Test
        void slotDenialProjectedPortfolioKeepsReliefAuditBounded() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(3).build(),
                nation(2, 1, 880).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build(),
                nation(104, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.5f, 0.0f);
        edges.add(0, 2, 99.0f, 0.0f);
        edges.add(0, 3, 98.5f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 1, 79.5f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        edges.add(1, 3, 78.5f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103, 104};
        PlannerProfiler.Session session = new PlannerProfiler.Session();

        PlannerProfiler.withSession(session, () -> LongHorizonAssignmentOptimizer.solveDetailed(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        BlitzObjective.CONTROL.objective(),
                        SideOpeningSettings.defaults(BlitzObjective.CONTROL.objective()),
                        SideOpeningSettings.defaults(BlitzObjective.CONTROL.objective()),
                        SidePlannerSettings.defaults().withProjectedAuditLimit(1),
                        SidePlannerSettings.defaults(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        ));
        PlannerProfiler.ProfileSnapshot snapshot = session.snapshot();

        PlannerProfiler.ScopeStats solveStats = snapshot.stats(PlannerProfiler.Scope.LONG_HORIZON_SOLVE);
        assertEquals(1L, solveStats.counters().getOrDefault("boundedProjectedPortfolio", 0L),
                "Slot-denial objectives should stay on the same bounded projected-portfolio owner");
        assertEquals(1L, solveStats.counters().getOrDefault("boundedProjectedReliefAudits", 0L),
                "The explicit relief audit budget should remain unchanged for slot-denial objectives");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedAudits", 0L)
                        >= solveStats.counters().getOrDefault("boundedProjectedReliefAudits", 0L),
                "Bounded projected audits should not hide extra work inside the relief budget itself");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedFollowOnRebalanceAudits", 0L) <= 2L,
                "Slot-denial projected families should keep rebalance audits bounded to the same small seed set");
    }

    @Test
    void marginalFlowStaticInputsReuseAcrossEquivalentEdgeCopies() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 880)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = commitmentScenarioEdges();
        CandidateEdgeTable copiedEdges = CandidateEdgeTable.copyOf(edges);
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};
        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                copiedEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );

        LongHorizonMarginalFlowSolver.StaticSolveInputs staticInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonMarginalFlowSolver.Result withStaticInputs = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs
        );
        LongHorizonMarginalFlowSolver.Result freshInputs = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );

        assertEquals(freshInputs.assignment(), withStaticInputs.assignment(),
                "Equivalent edge-table copies should solve identically when the optimizer reuses immutable marginal-flow indexes");
        assertArrayEquals(freshInputs.edgeAssigned(), withStaticInputs.edgeAssigned());
        assertArrayEquals(freshInputs.attackerCounts(), withStaticInputs.attackerCounts());
        assertArrayEquals(freshInputs.defenderCounts(), withStaticInputs.defenderCounts());
    }

    @Test
    void marginalFlowGraphBuffersReuseAcrossRepeatedSolveBatch() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 880)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = commitmentScenarioEdges();
        CandidateEdgeTable copiedEdges = CandidateEdgeTable.copyOf(edges);
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};
        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                copiedEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );

        LongHorizonMarginalFlowSolver.StaticSolveInputs staticInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonMarginalFlowSolver.GraphBuildBuffers graphBuffers =
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers();
        LongHorizonMarginalFlowSolver.Result firstBuffered = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                graphBuffers
        );
        LongHorizonMarginalFlowSolver.Result secondBuffered = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                graphBuffers
        );
        LongHorizonMarginalFlowSolver.Result freshSolve = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                projection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs
        );

        assertEquals(freshSolve.assignment(), firstBuffered.assignment());
        assertArrayEquals(freshSolve.edgeAssigned(), firstBuffered.edgeAssigned());
        assertArrayEquals(freshSolve.attackerCounts(), firstBuffered.attackerCounts());
        assertArrayEquals(freshSolve.defenderCounts(), firstBuffered.defenderCounts());
        assertEquals(freshSolve.assignment(), secondBuffered.assignment());
        assertArrayEquals(freshSolve.edgeAssigned(), secondBuffered.edgeAssigned());
        assertArrayEquals(freshSolve.attackerCounts(), secondBuffered.attackerCounts());
        assertArrayEquals(freshSolve.defenderCounts(), secondBuffered.defenderCounts());
    }

    @Test
    void marginalFlowResultLazilyMaterializesAssignmentAndPreservesOffTableFixedEdges() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 880)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        int[] attackerCaps = {1, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};
        List<BlitzFixedEdge> fixedEdges = List.of(new BlitzFixedEdge(2, 102));

        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );

        LongHorizonMarginalFlowSolver.Result result = LongHorizonMarginalFlowSolver.solve(
                edges,
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

        assertEquals(2, result.assignmentPairCount(),
                "Dense long-horizon results should count both fixed and solved assignment pairs before map materialization");
        Map<Integer, List<Integer>> firstMaterialized = result.assignment();
        Map<Integer, List<Integer>> secondMaterialized = result.assignment();

        assertSame(firstMaterialized, secondMaterialized,
                "Long-horizon solver results should cache the materialized assignment map once it is requested");
        assertEquals(List.of(102), firstMaterialized.get(2),
                "Fixed pairs that never existed in the edge table must still survive lazy assignment materialization");
        assertEquals(List.of(101), firstMaterialized.get(1),
                "Solved non-fixed edges should still materialize in deterministic edge order");
    }

    @Test
    void capLimitWarmStartMatchesFreshRelaxedSolve() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900),
                nation(2, 1, 880)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 880)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 0, 99.5f, 0.0f);
        edges.add(0, 1, 94.0f, 0.0f);
        edges.add(1, 0, 93.0f, 0.0f);
        edges.add(1, 1, 92.0f, 0.0f);
        int[] capLimitOne = {1, 1};
        int[] capLimitTwo = {2, 2};
        int[] defenderCaps = {2, 2};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};

        LongHorizonControlProjection capOneProjection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                capLimitOne,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );
        LongHorizonControlProjection capTwoProjection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                capLimitTwo,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );
        LongHorizonMarginalFlowSolver.StaticSolveInputs staticInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                attackerNationIds,
                defenderNationIds,
                List.of()
        );

        LongHorizonMarginalFlowSolver.Result capOneResult = LongHorizonMarginalFlowSolver.solve(
                edges,
                capOneProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                capLimitOne,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers()
        );
        LongHorizonMarginalFlowSolver.Result freshCapTwo = LongHorizonMarginalFlowSolver.solve(
                edges,
                capTwoProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                capLimitTwo,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers()
        );
        LongHorizonMarginalFlowSolver.Result warmStartedCapTwo = LongHorizonMarginalFlowSolver.solve(
                edges,
                capTwoProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                capLimitTwo,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers(),
                capOneResult.edgeAssigned()
        );

        assertEquals(freshCapTwo.assignment(), warmStartedCapTwo.assignment());
        assertArrayEquals(freshCapTwo.edgeAssigned(), warmStartedCapTwo.edgeAssigned());
        assertArrayEquals(freshCapTwo.attackerCounts(), warmStartedCapTwo.attackerCounts());
        assertArrayEquals(freshCapTwo.defenderCounts(), warmStartedCapTwo.defenderCounts());
    }

    @Test
    void rescaledVariantWarmStartMatchesFreshSolve() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).build(),
                nation(2, 1, 880).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900),
                nation(102, 2, 880)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable baseEdges = commitmentScenarioEdges();
        int[] baseAttackerCaps = {2, 1};
        int[] variantAttackerCaps = {1, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};

        LongHorizonControlProjection baseProjection = LongHorizonControlProjection.createScorerOnly(
                baseEdges,
                scenario,
                baseAttackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );
        LongHorizonMarginalFlowSolver.StaticSolveInputs staticInputs = LongHorizonMarginalFlowSolver.staticSolveInputs(
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonMarginalFlowSolver.Result baseResult = LongHorizonMarginalFlowSolver.solve(
                baseEdges,
                baseProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                baseAttackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers()
        );

        CandidateEdgeTable variantEdges = CandidateEdgeTable.copyOf(baseEdges);
        variantEdges.rescaleAttackerEdgesFromProjectedState(0, 0.55f);
        LongHorizonControlProjection variantProjection = LongHorizonControlProjection.createScorerOnly(
                variantEdges,
                scenario,
                variantAttackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                false,
                SidePlannerSettings.defaults()
        );

        LongHorizonMarginalFlowSolver.Result freshVariant = LongHorizonMarginalFlowSolver.solve(
                variantEdges,
                variantProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                variantAttackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers()
        );
        LongHorizonMarginalFlowSolver.Result warmStartedVariant = LongHorizonMarginalFlowSolver.solve(
                variantEdges,
                variantProjection,
                scenario.attackerCount(),
                scenario.defenderCount(),
                variantAttackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                staticInputs,
                new LongHorizonMarginalFlowSolver.GraphBuildBuffers(),
                baseResult.edgeAssigned()
        );

        assertEquals(freshVariant.assignment(), warmStartedVariant.assignment());
        assertArrayEquals(freshVariant.edgeAssigned(), warmStartedVariant.edgeAssigned());
        assertArrayEquals(freshVariant.attackerCounts(), warmStartedVariant.attackerCounts());
        assertArrayEquals(freshVariant.defenderCounts(), warmStartedVariant.defenderCounts());
    }

    @Test
    void forwardProjectionReusesPreparedStateAcrossVariantsWithSameActiveProfile() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(1).build(),
                nation(2, 1, 880).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 880).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 98.0f, 0.0f);
        edges.add(1, 1, 97.0f, 0.0f);
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[]{1, 1},
                new int[]{1, 1},
                72,
                1.0d
        );
        PlannerProfiler.Session session = new PlannerProfiler.Session();
        boolean[] firstVariant = {true, false, false, true};
        boolean[] secondVariant = {false, true, true, false};
        int[] attackerCounts = {1, 1};
        int[] defenderCounts = {1, 1};

        PlannerProfiler.withSession(session, () -> {
            projection.projectedObjectiveScore(
                    new TeamDifferenceObjective(),
                    attackers.get(0).teamId(),
                    firstVariant,
                    attackerCounts,
                    defenderCounts
            );
            projection.projectedObjectiveScore(
                    new TeamDifferenceObjective(),
                    attackers.get(0).teamId(),
                    secondVariant,
                    attackerCounts,
                    defenderCounts
            );
        });

        PlannerProfiler.ScopeStats projectedStats = session.snapshot().stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION);
        assertEquals(1L, projectedStats.counters().getOrDefault("preparedStateProfiles", 0L),
                "One active-war profile should seed one prepared projection-state checkpoint");
        assertEquals(1L, projectedStats.counters().getOrDefault("preparedWarTemplateBuilds", 0L),
                "The opening-war template should be built once per forward-projection owner");
        assertTrue(projectedStats.counters().getOrDefault("preparedStateRestores", 0L) >= 1L,
                "A second variant with the same active profile should restore prepared projection state instead of rebuilding it");
        assertTrue(projectedStats.counters().getOrDefault("preparedWarRestores", 0L) >= 1L,
                "A second variant should restore the prepared opening-war template before applying its own openings");
    }

    @Test
    void scorerOnlyVariantMatchesFullVariantForSolveTimeReliefScoring() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).build(),
                nation(2, 1, 880).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 880).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = commitmentScenarioEdges();
        CandidateEdgeTable copiedEdges = CandidateEdgeTable.copyOf(edges);
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102};

        LongHorizonControlProjection seedProjection = LongHorizonControlProjection.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        LongHorizonControlProjection fullVariant = seedProjection.sameSettingsFullVariant(
                copiedEdges,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks
        );
        LongHorizonControlProjection scorerOnlyVariant = seedProjection.sameSettingsScorerOnlyVariant(
                copiedEdges,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks
        );

        LongHorizonMarginalFlowSolver.Result fullResult = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                fullVariant,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );
        LongHorizonMarginalFlowSolver.Result scorerOnlyResult = LongHorizonMarginalFlowSolver.solve(
                copiedEdges,
                scorerOnlyVariant,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );

        assertEquals(fullResult.assignment(), scorerOnlyResult.assignment(),
                "Selective relief variants should not rebuild a full forward projection when solve-time scorer inputs are unchanged");
        assertArrayEquals(fullResult.edgeAssigned(), scorerOnlyResult.edgeAssigned());
        assertArrayEquals(fullResult.attackerCounts(), scorerOnlyResult.attackerCounts());
        assertArrayEquals(fullResult.defenderCounts(), scorerOnlyResult.defenderCounts());
        assertEquals(
                fullVariant.assignmentScoreDense(fullResult.edgeAssigned(), fullResult.attackerCounts(), fullResult.defenderCounts()),
                scorerOnlyVariant.assignmentScoreDense(scorerOnlyResult.edgeAssigned(), scorerOnlyResult.attackerCounts(), scorerOnlyResult.defenderCounts()),
                1e-9,
                "Scorer-only relief variants must preserve solve-time assignment scoring"
        );
    }

    @Test
    void sameTopologyScoringVariantMatchesFreshScoringModel() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).build(),
                nation(2, 1, 880).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 880).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = commitmentScenarioEdges();
        CandidateEdgeTable variantEdges = CandidateEdgeTable.copyOf(edges);
        variantEdges.rescaleAttackerEdgesFromProjectedState(0, 0.5f);
        int[] attackerCaps = {2, 1};
        int[] defenderCaps = {1, 1};
        int[] attackerStrengthRanks = {0, 1};

        LongHorizonAssignmentScoringModel fresh = LongHorizonAssignmentScoringModel.create(
                variantEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults()
        );
        LongHorizonAssignmentScoringModel reused = LongHorizonAssignmentScoringModel.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults()
        ).sameTopologyVariant(
                variantEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                SidePlannerSettings.defaults()
        );

        boolean[] edgeAssigned = {true, false, true};
        int[] attackerCounts = {1, 1};
        int[] defenderCounts = {2, 0};
        LongHorizonCounterOpportunityModel counterOpportunityModel = LongHorizonForwardProjection.counterOpportunityModel(
                scenario,
                72,
                1.0d
        );

        assertEquals(
                fresh.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps),
                reused.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps),
                1e-9,
                "Same-topology scorer-model reuse must preserve assignment scoring"
        );
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            assertEquals(fresh.attackerCommitmentMarginalScore(attackerIndex, 0), reused.attackerCommitmentMarginalScore(attackerIndex, 0), 1e-9);
            assertEquals(fresh.attackerIdlePressureMarginalScore(attackerIndex), reused.attackerIdlePressureMarginalScore(attackerIndex), 1e-9);
        }
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            assertEquals(fresh.defenderPressureMarginalScore(defenderIndex, 0), reused.defenderPressureMarginalScore(defenderIndex, 0), 1e-9);
        }
    }

    @Test
    void sameTopologyRescaledAttackerVariantMatchesFreshScoringModel() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(3).build(),
                nation(2, 1, 880).toBuilder().maxOff(2).build(),
                nation(3, 1, 860).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 880).toBuilder().maxOff(1).build(),
                nation(103, 2, 860).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = commitmentScenarioEdges();
        edges.add(0, 2, (byte) 0, (byte) 0, 6f, 0f, 4f, 0f, 0f, 2f, 1f);
        edges.add(1, 2, (byte) 0, (byte) 0, 7f, 0f, 5f, 0f, 0f, 3f, 2f);
        CandidateEdgeTable variantEdges = CandidateEdgeTable.copyOf(edges);
        variantEdges.rescaleAttackerEdgesFromProjectedState(0, 0.4f);
        variantEdges.rescaleAttackerEdgesFromProjectedState(1, 0.6f);
        int[] attackerCaps = {2, 1, 1};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {0, 1, 2};

        LongHorizonAssignmentScoringModel fresh = LongHorizonAssignmentScoringModel.create(
                variantEdges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults()
        );
        LongHorizonAssignmentScoringModel reused = LongHorizonAssignmentScoringModel.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                SidePlannerSettings.defaults()
        ).sameTopologyRescaledAttackerVariant(
                variantEdges,
                attackerCaps,
                attackerStrengthRanks,
                72,
                SidePlannerSettings.defaults(),
                new IntArrayList(new int[]{0, 1})
        );

        boolean[] edgeAssigned = {true, false, true, true, false};
        int[] attackerCounts = {1, 1, 1};
        int[] defenderCounts = {2, 1, 0};
        LongHorizonCounterOpportunityModel counterOpportunityModel = LongHorizonForwardProjection.counterOpportunityModel(
                scenario,
                72,
                1.0d
        );

        assertEquals(
                fresh.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps),
                reused.assignmentScoreDense(edgeAssigned, attackerCounts, defenderCounts, counterOpportunityModel, attackerCaps),
                1e-9,
                "Rescaled-attacker scorer-model reuse must preserve assignment scoring"
        );
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            assertEquals(fresh.attackerCommitmentMarginalScore(attackerIndex, 0), reused.attackerCommitmentMarginalScore(attackerIndex, 0), 1e-9);
            assertEquals(fresh.attackerIdlePressureMarginalScore(attackerIndex), reused.attackerIdlePressureMarginalScore(attackerIndex), 1e-9);
        }
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            assertEquals(fresh.defenderPressureMarginalScore(defenderIndex, 0), reused.defenderPressureMarginalScore(defenderIndex, 0), 1e-9);
        }
    }

    @Test
    void feedbackCapableRescaledVariantMatchesEagerFullVariantFeedbackProjection() {
        List<DBNationSnapshot> attackers = List.of(
                exhaustedCurrentBuys(nation(1, 1, 60).toBuilder().maxOff(3).build()),
                nation(2, 1, 10_000).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build(),
                nation(103, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 2, 79.0f, 0.0f);
        CandidateEdgeTable variantEdges = CandidateEdgeTable.copyOf(edges);
        variantEdges.rescaleAttackerEdgesFromProjectedState(0, 0.4f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103};
        CounterAdjustedForwardWarObjective objective = new CounterAdjustedForwardWarObjective();

        LongHorizonControlProjection seedProjection = LongHorizonControlProjection.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                72,
                1.0d,
                true,
                objective,
                SideOpeningSettings.defaults(objective),
                SideOpeningSettings.defaults(objective),
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        LongHorizonControlProjection eagerFullVariant = seedProjection.sameSettingsFullVariant(
                variantEdges,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks
        );
        LongHorizonControlProjection feedbackCapableVariant = seedProjection.sameSettingsFeedbackCapableRescaledAttackerVariant(
                variantEdges,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                new IntArrayList(new int[]{0})
        );

        LongHorizonMarginalFlowSolver.Result solveResult = LongHorizonMarginalFlowSolver.solve(
                variantEdges,
                feedbackCapableVariant,
                scenario.attackerCount(),
                scenario.defenderCount(),
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of()
        );

        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation eagerFeedback = eagerFullVariant.projectedAttackerFeedbackEvaluation(
                objective,
                attackers.get(0).teamId(),
                solveResult.edgeAssigned(),
                solveResult.attackerCounts(),
                solveResult.defenderCounts()
        );
        LongHorizonForwardProjection.ProjectedAttackerFeedbackEvaluation lazyFeedback = feedbackCapableVariant.projectedAttackerFeedbackEvaluation(
                objective,
                attackers.get(0).teamId(),
                solveResult.edgeAssigned(),
                solveResult.attackerCounts(),
                solveResult.defenderCounts()
        );

        assertEquals(
                eagerFullVariant.assignmentScoreDense(solveResult.edgeAssigned(), solveResult.attackerCounts(), solveResult.defenderCounts()),
                feedbackCapableVariant.assignmentScoreDense(solveResult.edgeAssigned(), solveResult.attackerCounts(), solveResult.defenderCounts()),
                1e-9,
                "Unified feedback-capable variants must preserve solve-time assignment scoring"
        );
        assertEquals(eagerFeedback.projectedEvaluation().objectiveScore(), lazyFeedback.projectedEvaluation().objectiveScore(), 1e-6);
        assertArrayEquals(eagerFeedback.projectedEvaluation().realizedCounterIncidence(), lazyFeedback.projectedEvaluation().realizedCounterIncidence());
        assertEquals(eagerFeedback.attackerMidHorizonSnapshot().attackerEdgeFactor(0), lazyFeedback.attackerMidHorizonSnapshot().attackerEdgeFactor(0), 1e-9);
        assertEquals(eagerFeedback.attackerMidHorizonSnapshot().attackerEdgeFactor(1), lazyFeedback.attackerMidHorizonSnapshot().attackerEdgeFactor(1), 1e-9);
    }

    @Test
    void recedingFeedbackProducesDeterministicOutputAcrossRepeatedRuns() {
        // The fixed-point iteration must remain deterministic: same inputs must produce the same
        // assignment regardless of how many cap-reduction iterations actually fire.
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 60).toBuilder().maxOff(3).build(),
                nation(2, 1, 1_500).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 1_400).toBuilder().maxOff(1).build(),
                nation(102, 2, 1_400).toBuilder().maxOff(1).build(),
                nation(103, 2, 1_400).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(0, 2, 98.0f, 0.0f);
        edges.add(1, 0, 80.0f, 0.0f);
        edges.add(1, 1, 79.0f, 0.0f);
        edges.add(1, 2, 78.0f, 0.0f);
        int[] attackerCaps = {3, 2};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerStrengthRanks = {1, 0};
        int[] attackerNationIds = {1, 2};
        int[] defenderNationIds = {101, 102, 103};

        Map<Integer, List<Integer>> first = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                360,
                heuristicProjectionContext(new TeamDifferenceObjective())
        );
        Map<Integer, List<Integer>> second = LongHorizonAssignmentOptimizer.solve(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                360,
                heuristicProjectionContext(new TeamDifferenceObjective())
        );

        assertEquals(first, second,
                "Fixed-point feedback iteration must remain deterministic across repeated runs with identical inputs");
    }

    @Test
        void forwardProjectionAddsLaterDeclarationsFromProjectedState() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0),
                withTotalScore(nation(2, 1, 900), 2_000.0)
        );
        List<DBNationSnapshot> declaringDefenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> passiveDefenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double passiveReverseWars = projectedAssignedScore(
                attackers,
                passiveDefenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24
        );
        double declaredWars = projectedAssignedScore(
                attackers,
                declaringDefenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24
        );

        assertEquals(0d, passiveReverseWars, 1e-6);
        assertTrue(declaredWars > passiveReverseWars,
                "Projected scoring should emit legal later declarations as active wars");
    }

    @Test
    void forwardProjectionAgesBeigeBeforeLaterDeclarationSelection() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder()
                        .maxOff(1)
                        .beigeTurns(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double reverseWars = projectedAssignedScore(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24
        );

        assertTrue(reverseWars > 0d,
                "Dense projection should age initial beige turns before projected later-declaration eligibility is evaluated");
    }

    @Test
    void forwardProjectionDeclaresLaterWarsWhenProjectedStateAllowsThem() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                nation(102, 2, 900).toBuilder()
                        .maxOff(1)
                        .beigeTurns(18)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double reverseWars = projectedAssignedScore(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                36
        );

        assertTrue(reverseWars > 0d,
                "Projected later declarations should occur as soon as projected beige and slot state allows them, not at a fixed turn window");
    }

    @Test
    void forwardProjectionUsesPerSideLaterDeclarationScoreThreshold() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                nation(102, 2, 900).toBuilder()
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot defaultProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24,
                heuristicProjectionContext(new ReverseLaterDeclarationWarCountObjective())
        );
        PlannerProfiler.ProfileSnapshot thresholdSuppressedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseLaterDeclarationWarCountObjective(),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SidePlannerSettings.defaults(),
                        SidePlannerSettings.defaults().withLaterDeclarationScoreThreshold(1_000_000d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long defaultCounterDeclarations = defaultProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long thresholdSuppressedCounterDeclarations = thresholdSuppressedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertTrue(defaultCounterDeclarations > 0L,
                "Default defender later-declaration threshold should still allow this projected declaration scenario");
        assertEquals(0L, thresholdSuppressedCounterDeclarations,
                "A very high defender-side later-declaration threshold should suppress projected declarations without changing the opening assignment path");
    }

    @Test
    void objectiveDrivenProjectionPolicyCanSuppressPrimitiveLaterDeclarations() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0),
                withTotalScore(nation(2, 1, 900), 2_000.0)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(102, 2, 900).toBuilder()
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        StrategicObjective terminalObjective = new ReverseLaterDeclarationWarCountObjective();

        PlannerProfiler.ProfileSnapshot heuristicProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                terminalObjective,
                24,
                heuristicProjectionContext(terminalObjective)
        );
        PlannerProfiler.ProfileSnapshot objectivePolicyProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                terminalObjective,
                24,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        terminalObjective,
                        SideOpeningSettings.defaults(terminalObjective),
                        SideOpeningSettings.defaults(terminalObjective),
                        SidePlannerSettings.defaults(),
                        SidePlannerSettings.defaults().withLaterDeclarationScoreThreshold(0.0d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.objectiveDriven(
                                new LaterDeclarationRejectingObjective(),
                                SideOpeningSettings.defaults(new LaterDeclarationRejectingObjective())
                        )
                )
        );
        long heuristicCounterDeclarations = heuristicProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long objectivePolicyCounterDeclarations = objectivePolicyProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long heuristicOpeningEvaluations = heuristicProfile.stats(PlannerProfiler.Scope.OPENING_EVALUATE).calls();
        long objectiveOpeningEvaluations = objectivePolicyProfile.stats(PlannerProfiler.Scope.OPENING_EVALUATE).calls();

        assertTrue(heuristicCounterDeclarations > 0L,
                "The legacy later-declaration heuristic should still declare in this fixture");
        assertEquals(0L, objectivePolicyCounterDeclarations,
                "Objective-driven later-declaration policy should be able to reject a legal heuristic declaration instead of only changing attack choice");
        assertEquals(0L, heuristicOpeningEvaluations,
                "Projected later declarations should use primitive dense-state components instead of rebuilding opening evaluations");
        assertEquals(0L, objectiveOpeningEvaluations,
                "Objective-driven projected later declarations should use primitive dense-state components instead of rebuilding opening evaluations");
    }

    @Test
    void forwardProjectionDefenderLaterDeclarationsCanTargetUnassignedAttackers() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0),
                withTotalScore(nation(2, 1, 900), 2_000.0)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder()
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot profile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24,
                heuristicProjectionContext(new ReverseLaterDeclarationWarCountObjective())
        );
        long counterDeclarations = profile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertTrue(counterDeclarations > 0L,
                "Projected later declarations should consider legal targets with free defensive slots, not only targets assigned in the opening");
    }

    @Test
    void forwardProjectionDoesNotStartLaterDeclarationsWithoutActiveWars() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0),
                withTotalScore(nation(2, 1, 900), 2_000.0)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder()
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot profile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24,
                heuristicProjectionContext(new ReverseLaterDeclarationWarCountObjective()),
                false
        );
        long counterDeclarations = profile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertEquals(0L, counterDeclarations,
                "Projected later declarations must not begin from a nonempty inactive opening template");
    }

    @Test
        void forwardProjectionBlocksSamePairDeclarationDuringPostVictoryDelay() {
                // Single attacker with one viable target. Projection should not reuse the same pair before
                // the post-victory reopen delay. A later-profile guardrail covers the resumed declaration path.
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        int lockoutTurns = WarSlotRules.sameOpponentLockoutTurns();
        int reopenDelayTurns = Math.max(lockoutTurns, SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT);

                PlannerProfiler.ProfileSnapshot blockedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                        reopenDelayTurns,
                        heuristicProjectionContext(new WarCountAvoidanceObjective())
        );

                long blockedLaterDeclarations = blockedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                        .counters()
                        .getOrDefault("laterDeclarations", 0L);

                assertEquals(0L, blockedLaterDeclarations,
                        "Projected post-victory delay should block same-pair declarations before the reopen window");
    }

    @Test
    void forwardProjectionCanUseFreeAttackerSlotBeforeTurnSixty() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CompiledScenario scenario = compile(attackers, defenders, Map.of());
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 95.0f, 0.0f);

        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                new int[]{2},
                new int[]{1, 1},
                2,
                1.0d,
                false,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        boolean[] edgeAssigned = {true, false};
        int[] attackerCounts = {1};
        int[] defenderCounts = {1, 0};

        PlannerProfiler.Session session = new PlannerProfiler.Session();
        PlannerProfiler.withSession(session, () -> projection.projectedObjectiveScore(
                new WarCountAvoidanceObjective(),
                attackers.get(0).teamId(),
                edgeAssigned,
                attackerCounts,
                defenderCounts
        ));

        long laterDeclarations = session.snapshot()
                .stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        assertTrue(
                laterDeclarations > 0L,
                "projected later declarations should be able to use a free offensive slot before turn 60 instead of waiting for an arbitrary expiration gate"
        );
    }

    @Test
    void followOnPromotionCandidatePromotesProjectedOpeningSideDeclaration() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CompiledScenario scenario = compile(attackers, defenders, Map.of());
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 95.0f, 0.0f);
        int[] attackerCaps = {2};
        int[] defenderCaps = {1, 1};
        int[] attackerNationIds = {1};
        int[] defenderNationIds = {101, 102};
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                2,
                1.0d,
                false,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        LongHorizonAssignmentOptimizer.Candidate seed = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, new IntArrayList(List.of(101))),
                new boolean[]{true, false},
                new int[]{1},
                new int[]{1, 0},
                projection.assignmentScoreDense(new boolean[]{true, false}, new int[]{1}, new int[]{1, 0})
        );
        LongHorizonCandidateEvaluator evaluator = LongHorizonCandidateEvaluator.create(
                scenario,
                heuristicProjectionContext(new WarCountAvoidanceObjective())
        );

        LongHorizonForwardProjection.ProjectedEvaluation evaluation = evaluator.projectedEvaluation(seed, projection);
        LongHorizonAssignmentOptimizer.Candidate promoted = LongHorizonAssignmentOptimizer.followOnPromotionCandidate(
                edges,
                attackerCaps,
                defenderCaps,
                attackerNationIds,
                defenderNationIds,
                seed,
                projection,
                evaluator
        );

        assertTrue(evaluation.openingSideDelayedDeclarationRegret() > 0d,
                "Test setup must produce a positive delayed opening-side follow-on signal");
        assertEquals(102, evaluation.openingSideLaterDeclarations().getFirst().targetNationId(),
                "Projection should expose the exact delayed follow-on pair instead of only a scalar regret");
        assertNotNull(promoted);
        assertEquals(List.of(101, 102), promoted.assignment().get(1),
                "The bounded promotion candidate should add the projected follow-on as an opening declaration when initial caps allow it");
    }

    @Test
    void followOnRebalanceCandidateSwapsOutWeakOpeningForProjectedDelayedDeclaration() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(0).build(),
                withTotalScore(nation(103, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CompiledScenario scenario = compile(attackers, defenders, Map.of());
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 95.0f, 0.0f);
        edges.add(0, 2, 5.0f, 0.0f);
        int[] attackerCaps = {2};
        int[] defenderCaps = {1, 1, 1};
        int[] attackerNationIds = {1};
        int[] defenderNationIds = {101, 102, 103};
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                2,
                1.0d,
                false,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        LongHorizonAssignmentOptimizer.Candidate seed = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, new IntArrayList(List.of(101, 103))),
                new boolean[]{true, false, true},
                new int[]{2},
                new int[]{1, 0, 1},
                projection.assignmentScoreDense(new boolean[]{true, false, true}, new int[]{2}, new int[]{1, 0, 1})
        );

        LongHorizonForwardProjection.ProjectedEvaluation evaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{0},
                12d,
                List.of(new LongHorizonForwardProjection.OpeningSideLaterDeclaration(1, 102, 24, 95d, 12d)),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(0, 0, 0, 1, 0)
        );
        LongHorizonAssignmentOptimizer.Candidate promoted = LongHorizonAssignmentOptimizer.followOnPromotionCandidate(
                edges,
                attackerCaps,
                defenderCaps,
                attackerNationIds,
                defenderNationIds,
                seed,
                projection,
                evaluation
        );
        LongHorizonAssignmentOptimizer.Candidate rebalanced = LongHorizonAssignmentOptimizer.followOnRebalanceCandidate(
                edges,
                attackerCaps,
                defenderCaps,
                attackerNationIds,
                defenderNationIds,
                new boolean[edges.edgeCount()],
                seed,
                projection,
                evaluation
        );

        assertTrue(evaluation.openingSideDelayedDeclarationRegret() > 0d,
                "Synthetic projected evaluation must expose a delayed opening-side declaration worth promoting into the opening family");
        assertNull(promoted,
                "Direct promotion should stay blocked when the seed is already at the opening cap");
        assertNotNull(rebalanced);
        assertEquals(List.of(101, 102), rebalanced.assignment().get(1),
                "The bounded rebalance candidate should evict the weakest opening and admit the higher-regret delayed follow-on");
    }

    @Test
    void projectedFamilyConsequenceTieBreakPrefersLowerCounterStorm() {
        LongHorizonAssignmentOptimizer.Candidate saferCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate stormierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonForwardProjection.ProjectedEvaluation saferEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(0, 0, 0, 0, 0)
        );
        LongHorizonForwardProjection.ProjectedEvaluation stormierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{3},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(0, 0, 0, 0, 0)
        );

        assertTrue(LongHorizonCandidateEvaluator.preferProjectedFamilyConsequences(
                        saferCandidate,
                        saferEvaluation,
                        stormierCandidate,
                        stormierEvaluation
                ),
                "When projected objective scores tie, the bounded family owner should prefer the family with lower counter-storm overload before falling back to raw opening score");
        assertFalse(LongHorizonCandidateEvaluator.preferProjectedFamilyConsequences(
                        stormierCandidate,
                        stormierEvaluation,
                        saferCandidate,
                        saferEvaluation
                ),
                "Projected family-consequence comparison must be directional so a stormier family cannot win the same tie-break in reverse");
    }

    @Test
    void projectedPrimaryObjectiveScorePenalizesAttackStarvationBeforeTieBreak() {
        LongHorizonAssignmentOptimizer.Candidate steadierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate starvedCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonForwardProjection.ProjectedEvaluation steadierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                10d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(200, 40, 10, 3, 0)
        );
        LongHorizonForwardProjection.ProjectedEvaluation starvedEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                10d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(200, 140, 120, 3, 0)
        );

        assertTrue(
                LongHorizonCandidateEvaluator.projectedPrimaryObjectiveScore(steadierCandidate, steadierEvaluation)
                        > LongHorizonCandidateEvaluator.projectedPrimaryObjectiveScore(starvedCandidate, starvedEvaluation),
                "Projected primary scoring should demote families that burn many turns on no-op or non-positive attack choices even when comparisonScore, opening score, and delayed-declaration regret match"
        );
    }

    @Test
    void projectedPrimaryObjectiveScorePenalizesCounterStormAndUnderStrengthFollowOns() {
        LongHorizonAssignmentOptimizer.Candidate steadierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate stormierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonForwardProjection.ProjectedEvaluation steadierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                10d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(120, 20, 10, 3, 0)
        );
        LongHorizonForwardProjection.ProjectedEvaluation stormierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                10d,
                new int[]{4},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(120, 20, 10, 3, 2)
        );

        assertTrue(
                LongHorizonCandidateEvaluator.projectedPrimaryObjectiveScore(steadierCandidate, steadierEvaluation)
                        > LongHorizonCandidateEvaluator.projectedPrimaryObjectiveScore(stormierCandidate, stormierEvaluation),
                "Projected primary scoring should charge counter-storm overload and under-strength follow-on shape before exact score ties are required"
        );
    }

    @Test
    void projectedFamilyConsequenceTieBreakPrefersFewerUnderStrengthLaterDeclarations() {
        LongHorizonAssignmentOptimizer.Candidate steadierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate weakerFollowOnCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonForwardProjection.ProjectedEvaluation steadierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(0, 0, 0, 3, 0)
        );
        LongHorizonForwardProjection.ProjectedEvaluation weakerFollowOnEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(0, 0, 0, 3, 2)
        );

        assertTrue(LongHorizonCandidateEvaluator.preferProjectedFamilyConsequences(
                        steadierCandidate,
                        steadierEvaluation,
                        weakerFollowOnCandidate,
                        weakerFollowOnEvaluation
                ),
                "When counter pressure ties, the bounded family owner should prefer the candidate whose projected later declarations rely less on under-strength follow-ons");
    }

    @Test
    void projectedFamilyConsequenceTieBreakPrefersLowerNoPositiveAttackRate() {
        LongHorizonAssignmentOptimizer.Candidate steadierCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate starvedCandidate = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                100d
        );
        LongHorizonForwardProjection.ProjectedEvaluation steadierEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(200, 40, 10, 3, 0)
        );
        LongHorizonForwardProjection.ProjectedEvaluation starvedEvaluation = new LongHorizonForwardProjection.ProjectedEvaluation(
                0d,
                0d,
                new int[]{1},
                0d,
                List.of(),
                new LongHorizonForwardProjection.ProjectedFamilyConsequences(200, 40, 60, 3, 0)
        );

        assertTrue(LongHorizonCandidateEvaluator.preferProjectedFamilyConsequences(
                        steadierCandidate,
                        steadierEvaluation,
                        starvedCandidate,
                        starvedEvaluation
                ),
                "When counter pressure ties, the bounded family owner should prefer the family whose projected attack choices more often stay positive-actionable");
    }

    @Test
    void followOnFamilySeedsIncludeReliefCandidatesBeforeCoverageRepair() {
        LongHorizonAssignmentOptimizer.Candidate best = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(101)),
                new boolean[]{true, false, false, false, false},
                new int[]{1, 0},
                new int[]{1, 0, 0},
                100d
        );
        LongHorizonAssignmentOptimizer.Candidate marginal = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(1, List.of(102)),
                new boolean[]{false, true, false, false, false},
                new int[]{1, 0},
                new int[]{0, 1, 0},
                95d
        );
        LongHorizonAssignmentOptimizer.Candidate relief = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(2, List.of(103)),
                new boolean[]{false, false, true, false, false},
                new int[]{0, 1},
                new int[]{0, 0, 1},
                90d
        );
        LongHorizonAssignmentOptimizer.Candidate coverageRepair = new LongHorizonAssignmentOptimizer.Candidate(
                Map.of(2, List.of(101)),
                new boolean[]{false, false, false, true, false},
                new int[]{0, 1},
                new int[]{1, 0, 0},
                85d
        );

        List<LongHorizonAssignmentOptimizer.Candidate> seeds = LongHorizonAssignmentOptimizer.followOnFamilySeeds(
                best,
                marginal,
                null,
                null,
                List.of(relief),
                List.of(coverageRepair)
        );

        assertTrue(seeds.contains(best),
                "The current best family should still seed bounded follow-on promotion and rebalance");
        assertTrue(seeds.contains(marginal),
                "The original marginal family should remain available as a bounded follow-on seed");
        assertTrue(seeds.contains(relief),
                "A non-winning relief family should now be eligible to seed follow-on promotion and rebalance");
        assertTrue(seeds.contains(coverageRepair),
                "When budget remains, coverage-repair families should also remain eligible follow-on seeds");
    }

    @Test
    void projectedFollowOnFeedbackAddsMissingOpeningEdgeBeforeFinalSolve() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900), 2_000.0).toBuilder().maxOff(2).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CompiledScenario scenario = compile(attackers, defenders, Map.of());
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        int[] attackerCaps = {2};
        int[] defenderCaps = {1, 1};
        int[] attackerNationIds = {1};
        int[] defenderNationIds = {101, 102};

        PlannerProfiler.Session session = new PlannerProfiler.Session();
        LongHorizonAssignmentOptimizer.Result result = PlannerProfiler.withSession(session, () ->
                LongHorizonAssignmentOptimizer.solveDetailed(
                        edges,
                        scenario,
                        attackerCaps,
                        defenderCaps,
                        new int[]{1},
                        attackerNationIds,
                        defenderNationIds,
                        List.of(),
                        72,
                        heuristicProjectionContext(new WarCountAvoidanceObjective())
                )
        );

        assertEquals(2, edges.edgeCount(),
                "Projection-selected follow-ons that were missing from the opening table should become real bounded feedback edges");
        assertTrue(result.assignment().getOrDefault(1, List.of()).contains(102),
                "The final solve should be allowed to compare the induced strategy with the projected follow-on declared at turn 0");
        long feedbackEdges = session.snapshot()
                .stats(PlannerProfiler.Scope.LONG_HORIZON_SOLVE)
                .counters()
                .getOrDefault("boundedProjectedFeedbackEdges", 0L);
        assertTrue(feedbackEdges > 0L,
                "The profiler should distinguish projection-feedback visibility from ordinary base-edge promotion");
    }

    @Test
        void forwardProjectionUsesPerSideLaterDeclarationThreshold() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot defaultProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                61 + Math.max(WarSlotRules.sameOpponentLockoutTurns(), SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT),
                heuristicProjectionContext(new WarCountAvoidanceObjective())
        );
        PlannerProfiler.ProfileSnapshot thresholdSuppressedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                61 + Math.max(WarSlotRules.sameOpponentLockoutTurns(), SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT),
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new WarCountAvoidanceObjective(),
                        SideOpeningSettings.defaults(new WarCountAvoidanceObjective()),
                        SideOpeningSettings.defaults(new WarCountAvoidanceObjective()),
                        SidePlannerSettings.defaults().withLaterDeclarationScoreThreshold(1_000_000d),
                        SidePlannerSettings.defaults(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long defaultLaterDeclarations = defaultProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long thresholdSuppressedLaterDeclarations = thresholdSuppressedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertTrue(defaultLaterDeclarations > 0L,
                "Default attacker-side later-declaration threshold should still allow projected declarations after the post-victory delay");
        assertEquals(0L, thresholdSuppressedLaterDeclarations,
                "A very high attacker-side later-declaration threshold should suppress projected declarations after the post-victory delay");
    }

    @Test
        void forwardProjectionRespectsPerTurnLaterDeclarationCap() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(1).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(1).build(),
                withTotalScore(nation(103, 2, 900), 2_000.0).toBuilder().maxOff(1).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot uncappedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                2,
                heuristicProjectionContext(new ReverseLaterDeclarationWarCountObjective())
        );
        PlannerProfiler.ProfileSnapshot cappedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseLaterDeclarationWarCountObjective(),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SidePlannerSettings.defaults(),
                        SidePlannerSettings.defaults().withMaxLaterDeclarationsPerTurn(1),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long uncappedCounterDeclarations = uncappedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long cappedCounterDeclarations = cappedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertTrue(uncappedCounterDeclarations > cappedCounterDeclarations,
                "A per-turn later-declaration cap should spread declarations over time instead of emptying the pool immediately");
        assertEquals(1L, cappedCounterDeclarations,
                "With one later-declaration turn available at horizon=2 and a per-turn cap of 1, only one later declaration should exist");
    }

    @Test
    void forwardProjectionUsesCounterActivityThresholdForEligibility() {
        List<DBNationSnapshot> attackers = List.of(withTotalScore(nation(1, 1, 900), 2_000.0));
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 900), 2_000.0).toBuilder().maxOff(1).build(),
                withTotalScore(nation(102, 2, 900), 2_000.0).toBuilder().maxOff(1).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        Map<Integer, Float> activityWeights = Map.of(
                1, 1.0f,
                101, 1.0f,
                102, 0.2f
        );

        PlannerProfiler.ProfileSnapshot unrestrictedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                activityWeights,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseLaterDeclarationWarCountObjective(),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SidePlannerSettings.defaults(),
                        SidePlannerSettings.defaults()
                                .withActivityActThreshold(0.0d)
                                .withLaterDeclarationScoreThreshold(0.0d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        PlannerProfiler.ProfileSnapshot thresholdedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                activityWeights,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseLaterDeclarationWarCountObjective(),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SideOpeningSettings.defaults(new ReverseLaterDeclarationWarCountObjective()),
                        SidePlannerSettings.defaults(),
                        SidePlannerSettings.defaults()
                                .withActivityActThreshold(0.5d)
                                .withLaterDeclarationScoreThreshold(0.0d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long unrestrictedCounterDeclarations = unrestrictedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);
        long thresholdedCounterDeclarations = thresholdedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("laterDeclarations", 0L);

        assertEquals(2L, unrestrictedCounterDeclarations,
                "Without an activity threshold, both defenders should be eligible for later declarations on the single declaration turn");
        assertEquals(1L, thresholdedCounterDeclarations,
                "Declarer activity threshold should suppress low-activity projected later declarations before pair scoring");
    }

    @Test
        void legacyActingPlannerSettingsReduceIdleAttackersOnMixedStrongDefendersFamily() {
        List<DBNationSnapshot> attackers = new ArrayList<>();
        List<DBNationSnapshot> defenders = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            attackers.add(strategicNation(10_000 + index, 1, index, 20 + index % 4, 1.0d, 3));
            double multiplier = index < 3 ? 1.75d : 0.50d;
            int cities = index < 3 ? 28 + index : 16 + index % 4;
            defenders.add(strategicNation(20_000 + index, 2, index, cities, multiplier, 1));
        }

        CompiledScenario scenario = compile(attackers, defenders);
        int[] attackerCaps = new int[attackers.size()];
        int[] defenderCaps = new int[defenders.size()];
        int[] attackerStrengthRanks = new int[attackers.size()];
        int[] attackerNationIds = new int[attackers.size()];
        int[] defenderNationIds = new int[defenders.size()];
        for (int index = 0; index < attackers.size(); index++) {
            attackerCaps[index] = OverrideSet.EMPTY.effectiveFreeOff(attackers.get(index));
            attackerStrengthRanks[index] = index;
            attackerNationIds[index] = scenario.attackerNationId(index);
            defenderCaps[index] = OverrideSet.EMPTY.effectiveFreeDef(defenders.get(index));
            defenderNationIds[index] = scenario.defenderNationId(index);
        }
        CandidateEdgeTable edges = new CandidateEdgeTable();
        OpeningEvaluator.evaluate(
                scenario,
                SimTuning.defaults(),
                OverrideSet.EMPTY,
                BlitzObjective.NET_DAMAGE.objective(),
                attackerCaps.clone(),
                defenderCaps.clone(),
                edges
        );

        Map<Integer, List<Integer>> zeroIdleAssignment = LongHorizonAssignmentOptimizer.solveWithAttackerCaps(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                false,
                SidePlannerSettings.defaults().withIdlePressureWeight(0d)
        ).assignment();
        Map<Integer, List<Integer>> defaultAssignment = LongHorizonAssignmentOptimizer.solveWithAttackerCaps(
                edges,
                scenario,
                attackerCaps,
                defenderCaps,
                attackerStrengthRanks,
                attackerNationIds,
                defenderNationIds,
                List.of(),
                72,
                false,
                SidePlannerSettings.actingDefaults()
        ).assignment();

        int zeroIdleAttackers = idleAttackersWithEdges(edges, attackerNationIds, zeroIdleAssignment);
        int defaultIdleAttackers = idleAttackersWithEdges(edges, attackerNationIds, defaultAssignment);

        assertTrue(zeroIdleAttackers > 0,
                "The mixed-strong-defenders fixture must leave some viable attackers idle when idle pressure is explicitly disabled");
        assertTrue(defaultIdleAttackers < zeroIdleAttackers,
                "Acting-side planner defaults should reduce idle attackers on the mixed-strong-defenders family (zero="
                        + zeroIdleAttackers + ", acting=" + defaultIdleAttackers + ")");
    }

    @Test
    void idlePressureMarginalScoreFavorsStrongerAttackers() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(1).build(),
                nation(2, 1, 900).toBuilder().maxOff(1).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 100.0f, 0.0f);
        edges.add(1, 1, 99.0f, 0.0f);

        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                new int[]{1, 1},
                new int[]{1, 1},
                new int[]{0, 1},
                72,
                1.0d,
                false,
                SidePlannerSettings.actingDefaults()
        );

        assertTrue(projection.attackerIdlePressureMarginalScore(0) > projection.attackerIdlePressureMarginalScore(1),
                "Idle pressure should weight the stronger attacker more heavily when their strategic value is otherwise comparable");
    }

    @Test
    void idlePressureDoesNotReRewardAlreadyCommittedAttackers() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).currentOffensiveWars(1).build(),
                nation(2, 1, 900).toBuilder().maxOff(1).currentOffensiveWars(0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build()
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 100.0f, 0.0f);
        edges.add(1, 1, 99.0f, 0.0f);

        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                new int[]{1, 1},
                new int[]{1, 1},
                new int[]{0, 1},
                72,
                1.0d,
                false,
                SidePlannerSettings.actingDefaults()
        );

        assertEquals(0d, projection.attackerIdlePressureMarginalScore(0), 1e-9,
                "An attacker that already has an offensive war should not get a fresh idle-pressure bonus for one more later-declaration slot");
        assertTrue(projection.attackerIdlePressureMarginalScore(1) > 0d,
                "A still-idle attacker should retain idle-pressure incentive for its first offensive assignment");
    }

    @Test
    void idlePressureFollowsCompiledFreeOffOwnershipNotRawSnapshotWars() {
        DBNationSnapshot attacker = nation(1, 1, 900).toBuilder()
                .maxOff(7)
                .currentOffensiveWars(6)
                .build();
        DBNationSnapshot peer = nation(2, 1, 900).toBuilder()
                .maxOff(1)
                .currentOffensiveWars(0)
                .build();
        List<DBNationSnapshot> attackers = List.of(attacker, peer);
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().maxOff(1).build(),
                nation(102, 2, 900).toBuilder().maxOff(1).build()
        );
        OverrideSet overrides = OverrideSet.builder()
                .forceFreeOff(attacker.nationId(), attacker.maxOff())
                .build();
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                overrides,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 0, 100.0f, 0.0f);
        edges.add(1, 1, 99.0f, 0.0f);

        LongHorizonControlProjection projection = LongHorizonControlProjection.createScorerOnly(
                edges,
                scenario,
                new int[]{attacker.maxOff(), 1},
                new int[]{1, 1},
                new int[]{0, 1},
                72,
                1.0d,
                false,
                SidePlannerSettings.actingDefaults()
        );

        assertTrue(projection.attackerIdlePressureMarginalScore(0) > 0d,
                "When the compiled scenario grants full free slots for the opening pass, raw snapshot offensive wars must not suppress idle-pressure for that attacker");
    }

    @Test
    void forwardProjectionSuppressesCountersAfterProjectedDamageRemovesMeaningfulValue() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 2_500).toBuilder()
                        .unit(MilitaryUnit.SOLDIER, 600_000)
                        .unit(MilitaryUnit.TANK, 80_000)
                        .build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 25).toBuilder()
                        .unit(MilitaryUnit.SOLDIER, 0)
                        .unit(MilitaryUnit.TANK, 0)
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double reverseWars = projectedAssignedScore(
                attackers,
                defenders,
                edges,
                new ReverseLaterDeclarationWarCountObjective(),
                24
        );

        assertEquals(0d, reverseWars, 1e-6,
                "Later-declaration capacity should require projected post-opening value, not only snapshot free-off slots");
    }

    private static double emptyProjectionScore(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            int horizonTurns
    ) {
        CompiledScenario scenario = compile(attackers, defenders);
        LongHorizonControlProjection projection = LongHorizonControlProjection.createHeuristic(
                edges,
                scenario,
                new int[attackers.size()],
                new int[defenders.size()],
                horizonTurns,
                0.0d
        );
        return projection.projectedObjectiveScore(
                new TeamDifferenceObjective(),
                attackers.get(0).teamId(),
                new boolean[edges.edgeCount()],
                new int[attackers.size()],
                new int[defenders.size()]
        );
    }

    private static double projectedAssignedScore(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            StrategicObjective objective,
            int horizonTurns
    ) {
        return projectedAssignedScore(
                attackers,
                defenders,
                Map.of(),
                edges,
                objective,
                horizonTurns,
                heuristicProjectionContext(objective)
        );
    }

    private static double projectedAssignedScore(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            StrategicObjective objective,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext
    ) {
        return projectedAssignedScore(
                attackers,
                defenders,
                Map.of(),
                edges,
                objective,
                horizonTurns,
                projectionContext
        );
    }

    private static double projectedAssignedScore(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, Float> activityWeights,
            CandidateEdgeTable edges,
            StrategicObjective objective,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext
    ) {
        CompiledScenario scenario = compile(attackers, defenders, activityWeights);
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                fill(attackers.size(), 1),
                fill(defenders.size(), 1),
                horizonTurns,
                1.0d,
                false,
                projectionContext.objective(),
                projectionContext.attackerOpeningSettings(),
                projectionContext.defenderOpeningSettings(),
                projectionContext.attackerPlannerSettings(),
                projectionContext.defenderPlannerSettings(),
                projectionContext.attackerProjectionPolicies(),
                projectionContext.defenderProjectionPolicies()
        );
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        java.util.Arrays.fill(edgeAssigned, true);
        return projection.projectedObjectiveScore(
                objective,
                attackers.get(0).teamId(),
                edgeAssigned,
                fill(attackers.size(), 1),
                fill(defenders.size(), 1)
        );
    }

    private static PlannerProfiler.ProfileSnapshot projectedProfileSnapshot(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            StrategicObjective objective,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext
    ) {
        return projectedProfileSnapshot(attackers, defenders, Map.of(), edges, objective, horizonTurns, projectionContext);
    }

        private static PlannerProfiler.ProfileSnapshot projectedProfileSnapshot(
                        List<DBNationSnapshot> attackers,
                        List<DBNationSnapshot> defenders,
                        CandidateEdgeTable edges,
                        StrategicObjective objective,
                        int horizonTurns,
                        LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext,
                        boolean assignEdges
        ) {
                return projectedProfileSnapshot(attackers, defenders, Map.of(), edges, objective, horizonTurns, projectionContext, assignEdges);
        }

    private static PlannerProfiler.ProfileSnapshot projectedProfileSnapshot(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            Map<Integer, Float> activityWeights,
            CandidateEdgeTable edges,
            StrategicObjective objective,
            int horizonTurns,
            LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext
        ) {
                return projectedProfileSnapshot(attackers, defenders, activityWeights, edges, objective, horizonTurns, projectionContext, true);
        }

        private static PlannerProfiler.ProfileSnapshot projectedProfileSnapshot(
                        List<DBNationSnapshot> attackers,
                        List<DBNationSnapshot> defenders,
                        Map<Integer, Float> activityWeights,
                        CandidateEdgeTable edges,
                        StrategicObjective objective,
                        int horizonTurns,
                        LongHorizonAssignmentOptimizer.ProjectionScoringContext projectionContext,
                        boolean assignEdges
    ) {
        CompiledScenario scenario = compile(attackers, defenders, activityWeights);
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                fill(attackers.size(), 1),
                fill(defenders.size(), 1),
                horizonTurns,
                1.0d,
                false,
                projectionContext.attackerPlannerSettings(),
                projectionContext.defenderPlannerSettings(),
                projectionContext.attackerProjectionPolicies(),
                projectionContext.defenderProjectionPolicies()
        );
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        java.util.Arrays.fill(edgeAssigned, assignEdges);
        PlannerProfiler.Session session = new PlannerProfiler.Session();
        PlannerProfiler.withSession(session, () -> projection.projectedObjectiveScore(
                objective,
                attackers.get(0).teamId(),
                edgeAssigned,
                fill(attackers.size(), assignEdges ? 1 : 0),
                fill(defenders.size(), assignEdges ? 1 : 0)
        ));
        return session.snapshot();
    }

    private static int[] fill(int length, int value) {
        int[] values = new int[length];
        java.util.Arrays.fill(values, value);
        return values;
    }

        private static CompiledScenario compile(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
                return compile(attackers, defenders, Map.of());
        }

        private static CompiledScenario compile(
                        List<DBNationSnapshot> attackers,
                        List<DBNationSnapshot> defenders,
                        Map<Integer, Float> activityWeights
        ) {
        return new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                                activityWeights
        );
    }

    private static CandidateEdgeTable pressureScenarioEdges() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 10.0f, 0.0f);
        edges.add(1, 0, 8.5f, 0.0f);
        edges.add(2, 0, 8.5f, 0.0f);
        edges.add(0, 1, 9.0f, 0.0f);
        edges.add(1, 1, 9.0f, 0.0f);
        edges.add(2, 1, 9.0f, 0.0f);
        return edges;
    }

    private static CandidateEdgeTable deepCommitmentScenarioEdges() {
        // Strong attacker has marginally better edges to every defender; peers are close behind.
        // Without commitment-aware re-solve, the strong attacker would consume both its slots
        // on the highest-marginal-score targets and leave peers idle.
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.5f, 0.0f);
        edges.add(0, 2, 99.0f, 0.0f);
        edges.add(1, 0, 98.8f, 0.0f);
        edges.add(1, 1, 98.7f, 0.0f);
        edges.add(1, 2, 98.6f, 0.0f);
        edges.add(2, 0, 98.5f, 0.0f);
        edges.add(2, 1, 98.4f, 0.0f);
        edges.add(2, 2, 98.3f, 0.0f);
        return edges;
    }

        private static CandidateEdgeTable counterfactualScenarioEdges() {
                CandidateEdgeTable edges = new CandidateEdgeTable();
                edges.add(0, 0, 100.0f, 0.0f);
                edges.add(0, 1, 99.5f, 0.0f);
                edges.add(0, 2, 99.0f, 0.0f);
                edges.add(1, 0, 98.8f, 0.0f);
                edges.add(1, 1, 98.7f, 0.0f);
                edges.add(1, 2, 98.6f, 0.0f);
                return edges;
        }

        private static CandidateEdgeTable counterPressureAssignmentEdges() {
                CandidateEdgeTable edges = new CandidateEdgeTable();
                edges.add(0, 0, 100.0f, 0.0f);
                edges.add(0, 1, 99.0f, 0.0f);
                edges.add(1, 0, 90.0f, 0.0f);
                edges.add(1, 1, 89.5f, 0.0f);
                return edges;
        }

        private static final class WarCountAvoidanceObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        int[] ownDeclaredWars = new int[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        ownDeclaredWars[0]++;
                                }
                        });
                        return -ownDeclaredWars[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class TeamDifferenceObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        double[] totals = new double[2];
                        view.forEachNation((nationId, nationTeamId, score) -> {
                                if (nationTeamId == teamId) {
                                        totals[0] += score;
                                } else {
                                        totals[1] += score;
                                }
                        });
                        return totals[0] - totals[1];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class ActiveWarStateObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        double[] score = new double[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        score[0] += Math.max(0, 100 - defenderResistance);
                                        if (airSuperiorityTeamId == teamId || groundSuperiorityTeamId == teamId || blockadeTeamId == teamId) {
                                                score[0] += 10d;
                                        }
                                }
                        });
                        return score[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class CounterAdjustedForwardWarObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        double[] score = new double[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        score[0] += 100d;
                                } else if (defenderTeamId == teamId) {
                                        score[0] -= 80d;
                                }
                        });
                        return score[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class ReverseLaterDeclarationWarCountObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        int[] reverseWars = new int[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId != teamId && defenderTeamId == teamId) {
                                        reverseWars[0]++;
                                }
                        });
                        return reverseWars[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class LaterDeclarationRejectingObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return -1d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class SlotDenialNeutralObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        int[] ownDeclaredWars = new int[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundSuperiorityTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        ownDeclaredWars[0]++;
                                }
                        });
                        return 1_000_000d * ownDeclaredWars[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public boolean usesWarSlotDenial() {
                        return true;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class OffensiveSlotCostObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamProjectionView controlView)) {
                                return 0d;
                        }
                        double[] cost = new double[1];
                        controlView.forEachActiveWarSlotMetric((attackerTeamId, defenderTeamId, attackerOffensiveSlotCost, defenderDefensiveSlotDenial) -> {
                                if (attackerTeamId == teamId) {
                                        cost[0] += attackerOffensiveSlotCost;
                                }
                        });
                        return cost[0];
                }

                @Override
                public double scoreOpening(
                        double immediateHarm,
                        double selfExposure,
                        double resourceSwing,
                        double controlLeverage,
                        double futureWarLeverage,
                        double targetPressure,
                        int teamId
                ) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

    private static CandidateEdgeTable commitmentScenarioEdges() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        edges.add(0, 1, 99.0f, 0.0f);
        edges.add(1, 1, 98.9f, 0.0f);
        return edges;
    }

    private static int targetCount(Map<Integer, List<Integer>> assignment, int targetNationId) {
        int count = 0;
        for (List<Integer> targets : assignment.values()) {
            for (int assignedTargetNationId : targets) {
                if (assignedTargetNationId == targetNationId) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int distinctCommittedAttackerCount(Map<Integer, List<Integer>> assignment) {
        int distinct = 0;
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                distinct++;
            }
        }
        return distinct;
    }

    private static int totalPairs(Map<Integer, List<Integer>> assignment) {
        int count = 0;
        for (List<Integer> targets : assignment.values()) {
            count += targets.size();
        }
        return count;
    }

        private static int idleAttackersWithEdges(
                        CandidateEdgeTable edges,
                        int[] attackerNationIds,
                        Map<Integer, List<Integer>> assignment
        ) {
                boolean[] attackerHasEdge = new boolean[attackerNationIds.length];
                for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
                        attackerHasEdge[edges.attackerIndex(edgeIndex)] = true;
                }
                int idle = 0;
                for (int attackerIndex = 0; attackerIndex < attackerNationIds.length; attackerIndex++) {
                        if (!attackerHasEdge[attackerIndex]) {
                                continue;
                        }
                        if (assignment.getOrDefault(attackerNationIds[attackerIndex], List.of()).isEmpty()) {
                                idle++;
                        }
                }
                return idle;
        }

    private static DBNationSnapshot nation(int nationId, int teamId, int aircraft) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .cities(10)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(1)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

        private static DBNationSnapshot withTotalScore(DBNationSnapshot snapshot, double totalScore) {
                double staticScore = snapshot.staticScoreComponent();
                int cities = Math.max(1, snapshot.cityInfraCount());
                double totalInfra = Math.max(0d, (totalScore - staticScore) * 40d);
                return snapshot.toBuilder()
                                .cityInfra(uniformInfra(cities, totalInfra / cities))
                                .build();
        }

    private static DBNationSnapshot strategicNation(
            int nationId,
            int teamId,
            int offset,
            int cities,
            double militaryMultiplier,
            int freeOffSlots
    ) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .cities(cities)
                .cityInfra(uniformInfra(cities, 1_800.0d + (offset % 4) * 150.0d))
                .maxOff(freeOffSlots)
                .unit(MilitaryUnit.SOLDIER, scaled(250_000 + offset * 2_000, militaryMultiplier))
                .unit(MilitaryUnit.TANK, scaled(20_000 + offset * 150, militaryMultiplier))
                .unit(MilitaryUnit.AIRCRAFT, scaled(1_600 + offset * 20, militaryMultiplier))
                .unit(MilitaryUnit.SHIP, scaled(250 + offset * 4, militaryMultiplier))
                .resource(ResourceType.MONEY, 100_000_000d)
                .resource(ResourceType.FOOD, 10_000_000d)
                .resource(ResourceType.GASOLINE, 2_000_000d)
                .resource(ResourceType.MUNITIONS, 2_000_000d)
                .resource(ResourceType.STEEL, 2_000_000d)
                .resource(ResourceType.ALUMINUM, 2_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static int scaled(int value, double multiplier) {
        return Math.max(0, (int) Math.round(value * multiplier));
    }

    private static DBNationSnapshot exhaustedCurrentBuys(DBNationSnapshot snapshot) {
        DBNationSnapshot.Builder builder = snapshot.toBuilder();
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            builder.unitBoughtToday(unit, 1_000_000);
        }
        return builder.build();
    }

        private static DBNationSnapshot noCurrentBuysNationWithTotalScore(int nationId, int teamId, double totalScore) {
                DBNationSnapshot baseline = DBNationSnapshot.synthetic(nationId)
                                .teamId(teamId)
                                .allianceId(teamId)
                                .cities(1)
                                .cityInfra(new double[]{0d})
                                .maxOff(1)
                                .warPolicy(WarPolicy.ATTRITION)
                                .build();
                return withTotalScore(baseline, totalScore);
        }

        private static DBNationSnapshot exhaustedBuysNationWithTotalScore(int nationId, int teamId, double totalScore) {
                DBNationSnapshot.Builder builder = noCurrentBuysNationWithTotalScore(nationId, teamId, totalScore).toBuilder();
                for (MilitaryUnit unit : List.of(MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP)) {
                        builder.unitBoughtToday(unit, 1_000_000);
                }
                return builder.build();
        }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        java.util.Arrays.fill(values, infra);
        return values;
    }
}
