package link.locutus.discord.sim.planners;

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
import link.locutus.discord.sim.TeamWarControlView;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongHorizonAssignmentOptimizerTest {
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
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solve(
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
        Map<Integer, List<Integer>> optimizerAssignment = LongHorizonAssignmentOptimizer.solve(
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
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solve(
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

        Map<Integer, List<Integer>> first = LongHorizonAssignmentOptimizer.solve(
                edges, scenario, attackerCaps, defenderCaps, attackerStrengthRanks,
                attackerNationIds, defenderNationIds, List.of(), 360
        );
        Map<Integer, List<Integer>> second = LongHorizonAssignmentOptimizer.solve(
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
        Map<Integer, List<Integer>> longAssignment = LongHorizonAssignmentOptimizer.solve(
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

        Map<Integer, List<Integer>> projectionOnly = LongHorizonAssignmentOptimizer.solve(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new WarCountAvoidanceObjective())
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
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(BlitzObjective.NET_DAMAGE.objective())
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(BlitzObjective.NET_DAMAGE.objective())
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
    void forwardProjectionDerivesScoreFromMutableStateAndCurrentRebuyCapacity() {
        DBNationSnapshot attackerNoBuysUsed = noCurrentBuysScoreNation(1, 1, 100.0, 400.0);
        DBNationSnapshot attackerNoRebuyLeft = exhaustedBuysScoreNation(1, 1, 100.0, 400.0);
        DBNationSnapshot defender = exhaustedBuysScoreNation(101, 2, 50.0, 400.0);
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
    void forwardProjectionLimitsProjectedBuysWhenResourcesAreKnownAndInsufficient() {
        DBNationSnapshot unknownResourceAttacker = noCurrentBuysScoreNation(1, 1, 100.0, 400.0);
        DBNationSnapshot constrainedAttacker = noCurrentBuysScoreNation(1, 1, 100.0, 400.0)
                .toBuilder()
                .resource(ResourceType.CREDITS, 1.0)
                .build();
        DBNationSnapshot defender = exhaustedBuysScoreNation(101, 2, 50.0, 400.0);
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
                nation(1, 1, 80).toBuilder().maxOff(2).build(),
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

        Map<Integer, List<Integer>> noCounterAssignment = LongHorizonAssignmentOptimizer.solve(
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
        Map<Integer, List<Integer>> counterAwareAssignment = LongHorizonAssignmentOptimizer.solve(
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new CounterAdjustedForwardWarObjective())
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
        LongHorizonControlProjection peerProjection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new CounterAdjustedForwardWarObjective())
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
                                SidePlannerSettings.legacyActing()
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
                                SidePlannerSettings.legacyActing()
                );

                List<LongHorizonAssignmentOptimizer.Candidate> reliefCandidates = LongHorizonFeedbackSearch.selectiveAttackerReliefCandidates(
                                edges,
                                scenario,
                                attackerCaps,
                                defenderCaps,
                                attackerStrengthRanks,
                                attackerNationIds,
                                defenderNationIds,
                                List.of(),
                                72,
                                seed,
                                projection,
                                new int[]{5, 5, 0},
                                SidePlannerSettings.legacyActing()
                );

                assertTrue(
                                reliefCandidates.stream().anyMatch(candidate -> candidate.attackerCounts()[0] <= 3 && candidate.attackerCounts()[1] <= 3),
                                "Selective relief should be able to reach deeper moderate-cap variants when multiple attackers are heavily overextended"
                );
        }

    @Test
    void midHorizonSnapshotReducesAttackerEdgeFactorAfterProjectedCounters() {
        // A vulnerable attacker exposed to a counter-capable defender for long enough should have
        // a projected mid-horizon edge factor strictly less than 1.0; a passive defender should
        // leave the factor at 1.0. This locks in that the optimizer's edge rebuild is keyed off
        // real projected nation state, not a fixed scalar penalty.
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build()
        );
        List<DBNationSnapshot> counterCapableDefenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build()
        );
        List<DBNationSnapshot> passiveDefenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        LongHorizonControlProjection counterCapableProjection = LongHorizonControlProjection.create(
                edges,
                compile(attackers, counterCapableDefenders),
                new int[]{1},
                new int[]{1},
                72,
                1.0d
        );
        LongHorizonControlProjection passiveProjection = LongHorizonControlProjection.create(
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
                "An attacker absorbing projected counter wars should not project to its full baseline strength + score");
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(objective)
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(objective)
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
                        SidePlannerSettings.legacy().withProjectedAuditLimit(1),
                        SidePlannerSettings.legacy(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        ));
        PlannerProfiler.ProfileSnapshot snapshot = session.snapshot();

        PlannerProfiler.ScopeStats solveStats = snapshot.stats(PlannerProfiler.Scope.LONG_HORIZON_SOLVE);
        assertEquals(1L, solveStats.counters().getOrDefault("boundedProjectedPortfolio", 0L),
                "Small projected portfolios should now flow through the canonical bounded audit owner");
        assertEquals(1L, solveStats.counters().getOrDefault("boundedProjectedReliefAudits", 0L),
                "Projected audit limit 1 should cap the replay-heavy relief family even on small portfolios");
        assertTrue(solveStats.counters().getOrDefault("boundedProjectedAudits", 0L)
                        < solveStats.counters().getOrDefault("boundedProjectedCandidates", 0L),
                "The small-portfolio path should no longer eagerly replay every generated variant");
        assertEquals(2L, solveStats.counters().getOrDefault("boundedProjectedAudits", 0L),
                "Bounded audit should spend one slot on ranked relief plus at most one reserved cap-limit hedge");
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
                        SidePlannerSettings.legacy().withProjectedAuditLimit(1),
                        SidePlannerSettings.legacy(),
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
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new TeamDifferenceObjective())
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new TeamDifferenceObjective())
        );

        assertEquals(first, second,
                "Fixed-point feedback iteration must remain deterministic across repeated runs with identical inputs");
    }

    @Test
    void forwardProjectionAddsReverseCounterWarsFromProjectedState() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> counterCapableDefenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build()
        );
        List<DBNationSnapshot> passiveDefenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double passiveReverseWars = projectedAssignedScore(
                attackers,
                passiveDefenders,
                edges,
                new ReverseCounterWarCountObjective(),
                24
        );
        double counterReverseWars = projectedAssignedScore(
                attackers,
                counterCapableDefenders,
                edges,
                new ReverseCounterWarCountObjective(),
                24
        );

        assertEquals(0d, passiveReverseWars, 1e-6);
        assertTrue(counterReverseWars > passiveReverseWars,
                "Projected scoring should emit defender-side counter declarations as reverse-team active wars");
    }

    @Test
    void forwardProjectionAgesBeigeBeforeProjectedCounterSelection() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder()
                        .nonInfraScoreBase(2_000.0)
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
                new ReverseCounterWarCountObjective(),
                24
        );

        assertTrue(reverseWars > 0d,
                "Dense projection should age initial beige turns before projected counter eligibility is evaluated");
    }

    @Test
    void forwardProjectionDeclaresCountersWhenProjectedStateAllowsThem() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(102, 2, 900).toBuilder()
                        .nonInfraScoreBase(2_000.0)
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
                new ReverseCounterWarCountObjective(),
                36
        );

        assertTrue(reverseWars > 0d,
                "Receding counter projection should declare as soon as projected beige and slot state allows it, not at a fixed turn window");
    }

    @Test
    void forwardProjectionUsesPerSideCounterScoreThreshold() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(102, 2, 900).toBuilder()
                        .nonInfraScoreBase(2_000.0)
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot defaultProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseCounterWarCountObjective(),
                24,
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new ReverseCounterWarCountObjective())
        );
        PlannerProfiler.ProfileSnapshot thresholdSuppressedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseCounterWarCountObjective(),
                24,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseCounterWarCountObjective(),
                        SidePlannerSettings.legacy(),
                        SidePlannerSettings.legacy().withCounterScoreThreshold(1_000_000d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long defaultCounterDeclarations = defaultProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);
        long thresholdSuppressedCounterDeclarations = thresholdSuppressedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);

        assertTrue(defaultCounterDeclarations > 0L,
                "Default defender counter threshold should still allow this projected counter scenario");
        assertEquals(0L, thresholdSuppressedCounterDeclarations,
                "A very high defender-side counter threshold should suppress projected counters without changing the opening assignment path");
    }

    @Test
    void forwardProjectionDefenderLaterDeclarationsCanTargetUnassignedAttackers() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build(),
                nation(2, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder()
                        .nonInfraScoreBase(2_000.0)
                        .maxOff(1)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot profile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseCounterWarCountObjective(),
                24,
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new ReverseCounterWarCountObjective())
        );
        long counterDeclarations = profile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);

        assertTrue(counterDeclarations > 0L,
                "Projected defender later declarations should consider legal attacker-side targets with free defensive slots, not only attackers assigned in the opening");
    }

    @Test
        void forwardProjectionBlocksSamePairRedeclareDuringPostVictoryDelay() {
                // Single attacker with one viable target. Projection should not reuse the same pair before
                // the post-victory reopen delay. A later-profile guardrail covers the resumed redeclare path.
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
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
                        LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new WarCountAvoidanceObjective())
        );

                long blockedRedeclarations = blockedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                        .counters()
                        .getOrDefault("redeclareDeclarations", 0L);

                assertEquals(0L, blockedRedeclarations,
                        "Projected post-victory delay should block same-pair redeclarations before the reopen window");
    }

    @Test
    void forwardProjectionCanUseFreeAttackerSlotBeforeTurnSixty() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().maxOff(2).nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build(),
                nation(102, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
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
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
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

        long redeclareDeclarations = session.snapshot()
                .stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("redeclareDeclarations", 0L);
        assertTrue(
                redeclareDeclarations > 0L,
                "projected later declarations should be able to use a free offensive slot before turn 60 instead of waiting for an arbitrary expiration gate"
        );
    }

    @Test
    void forwardProjectionUsesPerSideRedeclareScoreThreshold() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot defaultProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                61 + Math.max(WarSlotRules.sameOpponentLockoutTurns(), SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT),
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new WarCountAvoidanceObjective())
        );
        PlannerProfiler.ProfileSnapshot thresholdSuppressedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                61 + Math.max(WarSlotRules.sameOpponentLockoutTurns(), SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT),
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new WarCountAvoidanceObjective(),
                        SidePlannerSettings.legacy().withRedeclareScoreThreshold(1_000_000d),
                        SidePlannerSettings.legacy(),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long defaultRedeclarations = defaultProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("redeclareDeclarations", 0L);
        long thresholdSuppressedRedeclarations = thresholdSuppressedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("redeclareDeclarations", 0L);

        assertTrue(defaultRedeclarations > 0L,
                "Default attacker-side redeclare threshold should still allow projected redeclarations after the post-victory delay");
        assertEquals(0L, thresholdSuppressedRedeclarations,
                "A very high attacker-side redeclare threshold should suppress projected redeclarations after the post-victory delay");
    }

    @Test
    void forwardProjectionRespectsPerTurnCounterCap() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build(),
                nation(102, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build(),
                nation(103, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        PlannerProfiler.ProfileSnapshot uncappedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseCounterWarCountObjective(),
                2,
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(new ReverseCounterWarCountObjective())
        );
        PlannerProfiler.ProfileSnapshot cappedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                edges,
                new ReverseCounterWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseCounterWarCountObjective(),
                        SidePlannerSettings.legacy(),
                        SidePlannerSettings.legacy().withMaxCountersPerTurn(1),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long uncappedCounterDeclarations = uncappedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);
        long cappedCounterDeclarations = cappedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);

        assertTrue(uncappedCounterDeclarations > cappedCounterDeclarations,
                "A defender-side per-turn counter cap should spread counter declarations over time instead of emptying the pool immediately");
        assertEquals(1L, cappedCounterDeclarations,
                "With one counter turn available at horizon=2 and a per-turn cap of 1, only one reverse counter war should exist");
    }

    @Test
    void forwardProjectionUsesCounterActivityThresholdForEligibility() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build(),
                nation(102, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(1).build()
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
                new ReverseCounterWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseCounterWarCountObjective(),
                        SidePlannerSettings.legacy(),
                        SidePlannerSettings.legacy()
                                .withActivityActThreshold(0.0d)
                                .withCounterScoreThreshold(0.0d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        PlannerProfiler.ProfileSnapshot thresholdedProfile = projectedProfileSnapshot(
                attackers,
                defenders,
                activityWeights,
                edges,
                new ReverseCounterWarCountObjective(),
                2,
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(
                        new ReverseCounterWarCountObjective(),
                        SidePlannerSettings.legacy(),
                        SidePlannerSettings.legacy()
                                .withActivityActThreshold(0.5d)
                                .withCounterScoreThreshold(0.0d),
                        SideProjectionPolicies.heuristic(),
                        SideProjectionPolicies.heuristic()
                )
        );
        long unrestrictedCounterDeclarations = unrestrictedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);
        long thresholdedCounterDeclarations = thresholdedProfile.stats(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION)
                .counters()
                .getOrDefault("counterDeclarations", 0L);

        assertEquals(2L, unrestrictedCounterDeclarations,
                "Without an activity threshold, both defenders should be eligible to counter on the single counter turn");
        assertEquals(1L, thresholdedCounterDeclarations,
                "Defender-side activity threshold should suppress low-activity projected counters before pair scoring");
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
                SidePlannerSettings.legacy().withIdlePressureWeight(0d)
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
                SidePlannerSettings.legacyActing()
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
                SidePlannerSettings.legacyActing()
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
                SidePlannerSettings.legacyActing()
        );

        assertEquals(0d, projection.attackerIdlePressureMarginalScore(0), 1e-9,
                "An attacker that already has an offensive war should not get a fresh idle-pressure bonus for one more redeclare slot");
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
                SidePlannerSettings.legacyActing()
        );

        assertTrue(projection.attackerIdlePressureMarginalScore(0) > 0d,
                "When the compiled scenario grants full free slots for the opening pass, raw snapshot offensive wars must not suppress idle-pressure for that attacker");
    }

    @Test
    void forwardProjectionSuppressesCountersAfterProjectedDamageRemovesMeaningfulValue() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 2_500).toBuilder()
                        .nonInfraScoreBase(3_000.0)
                        .unit(MilitaryUnit.SOLDIER, 600_000)
                        .unit(MilitaryUnit.TANK, 80_000)
                        .build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 25).toBuilder()
                        .nonInfraScoreBase(3_000.0)
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
                new ReverseCounterWarCountObjective(),
                24
        );

        assertEquals(0d, reverseWars, 1e-6,
                "Counter capacity should require projected post-opening value, not only snapshot free-off slots");
    }

    private static double emptyProjectionScore(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            int horizonTurns
    ) {
        CompiledScenario scenario = compile(attackers, defenders);
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
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
                LongHorizonAssignmentOptimizer.ProjectionScoringContext.legacy(objective)
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
                projectionContext.attackerPlannerSettings(),
                projectionContext.defenderPlannerSettings(),
                projectionContext.attackerProjectionPolicies(),
                projectionContext.defenderProjectionPolicies()
        );
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        java.util.Arrays.fill(edgeAssigned, true);
        PlannerProfiler.Session session = new PlannerProfiler.Session();
        PlannerProfiler.withSession(session, () -> projection.projectedObjectiveScore(
                objective,
                attackers.get(0).teamId(),
                edgeAssigned,
                fill(attackers.size(), 1),
                fill(defenders.size(), 1)
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
                        if (!(view instanceof TeamWarControlView controlView)) {
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
                public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
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
                public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
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
                        if (!(view instanceof TeamWarControlView controlView)) {
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
                public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
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
                        if (!(view instanceof TeamWarControlView controlView)) {
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
                public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class ReverseCounterWarCountObjective implements StrategicObjective {
                @Override
                public double scoreTerminal(StrategicValueView view, int teamId) {
                        if (!(view instanceof TeamWarControlView controlView)) {
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
                public double scoreOpening(StrategicEvaluationComponents metrics, int teamId) {
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
                .score(1_000.0)
                .cities(10)
                .nonInfraScoreBase(1_000.0)
                .cityInfra(uniformInfra(10, 1_000.0))
                .maxOff(1)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .warPolicy(WarPolicy.ATTRITION)
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
                .score(900.0d + cities * 45.0d + offset)
                .cities(cities)
                .nonInfraScoreBase(400.0d + cities * 35.0d)
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

        private static DBNationSnapshot noCurrentBuysScoreNation(int nationId, int teamId, double nonInfraScoreBase, double cityInfra) {
                return DBNationSnapshot.synthetic(nationId)
                                .teamId(teamId)
                                .allianceId(teamId)
                                .score(999_999.0)
                                .cities(1)
                                .nonInfraScoreBase(nonInfraScoreBase)
                                .cityInfra(new double[]{cityInfra})
                                .maxOff(1)
                                .warPolicy(WarPolicy.ATTRITION)
                                .build();
        }

        private static DBNationSnapshot exhaustedBuysScoreNation(int nationId, int teamId, double nonInfraScoreBase, double cityInfra) {
                DBNationSnapshot.Builder builder = DBNationSnapshot.synthetic(nationId)
                                .teamId(teamId)
                                .allianceId(teamId)
                                .score(999_999.0)
                                .cities(1)
                                .nonInfraScoreBase(nonInfraScoreBase)
                                .cityInfra(new double[]{cityInfra})
                                .maxOff(1)
                                .warPolicy(WarPolicy.ATTRITION);
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
