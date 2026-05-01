package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.TeamScoreView;
import link.locutus.discord.sim.TeamWarControlView;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new WarCountAvoidanceObjective())
        );

        assertEquals(3, totalPairs(projectionOnly));
        assertEquals(totalPairs(projectionOnly), totalPairs(counterfactual),
                "Projected-objective portfolio scoring composes with raw pressure and must not collapse useful openings for a war-count-only objective");
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
                nation(101, 2, 25)
        );
        CompiledScenario scenario = compile(attackers, defenders);
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                new int[]{1},
                new int[]{1},
                3,
                1.0d
        );

        double emptyControlScore = projection.projectedObjectiveScore(
                new ActiveWarStateObjective(),
                1,
                new boolean[edges.edgeCount()],
                new int[1],
                new int[1]
        );
        double assignedControlScore = projection.projectedObjectiveScore(
                new ActiveWarStateObjective(),
                1,
                new boolean[]{true},
                new int[]{1},
                new int[]{1}
        );

        assertEquals(0d, emptyControlScore, 1e-6);
        assertTrue(assignedControlScore > 0d,
                "Forward projection should mutate active-war MAP/resistance/control state through dense combat buffers");
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
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new CounterAdjustedForwardWarObjective())
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
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new CounterAdjustedForwardWarObjective())
        );

        assertEquals(0, assignment.getOrDefault(1, List.of()).size(),
                "Projected wipe risk should be able to reject the vulnerable attacker's only non-fixed war");
        assertEquals(List.of(101), assignment.getOrDefault(2, List.of()),
                "A viable peer should take the target when the over-countered attacker's cap is relieved: " + assignment);
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
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new TeamDifferenceObjective())
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
                new LongHorizonAssignmentOptimizer.ProjectionScoringContext(new TeamDifferenceObjective())
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
    void forwardProjectionReDeclaresAttackerWarAfterOpeningExpires() {
        // Single attacker with one viable target. With horizon past WAR_EXPIRATION_TURN (60),
        // the opening war expires by turn 60. A correct projection should re-declare from the
        // freed offensive slot so the attacker is not idle for the rest of the horizon.
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 900).toBuilder().nonInfraScoreBase(2_000.0).build()
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900).toBuilder().nonInfraScoreBase(2_000.0).maxOff(0).build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 100.0f, 0.0f);

        double shortHorizonOwnWars = -projectedAssignedScore(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                30
        );
        double longHorizonOwnWars = -projectedAssignedScore(
                attackers,
                defenders,
                edges,
                new WarCountAvoidanceObjective(),
                72
        );

        assertEquals(1d, shortHorizonOwnWars, 1e-6,
                "Opening war should still be active before WAR_EXPIRATION_TURN");
        assertTrue(longHorizonOwnWars >= 1d,
                "Re-declare from freed offensive slot should keep at least one active forward war past expiry");
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
            TeamScoreObjective objective,
            int horizonTurns
    ) {
        CompiledScenario scenario = compile(attackers, defenders);
        LongHorizonControlProjection projection = LongHorizonControlProjection.create(
                edges,
                scenario,
                fill(attackers.size(), 1),
                fill(defenders.size(), 1),
                horizonTurns,
                1.0d
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

    private static int[] fill(int length, int value) {
        int[] values = new int[length];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static CompiledScenario compile(List<DBNationSnapshot> attackers, List<DBNationSnapshot> defenders) {
        return new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
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

        private static final class WarCountAvoidanceObjective implements TeamScoreObjective {
                @Override
                public double scoreTerminal(TeamScoreView view, int teamId) {
                        if (!(view instanceof TeamWarControlView controlView)) {
                                return 0d;
                        }
                        int[] ownDeclaredWars = new int[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        ownDeclaredWars[0]++;
                                }
                        });
                        return -ownDeclaredWars[0];
                }

                @Override
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class TeamDifferenceObjective implements TeamScoreObjective {
                @Override
                public double scoreTerminal(TeamScoreView view, int teamId) {
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
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class ActiveWarStateObjective implements TeamScoreObjective {
                @Override
                public double scoreTerminal(TeamScoreView view, int teamId) {
                        if (!(view instanceof TeamWarControlView controlView)) {
                                return 0d;
                        }
                        double[] score = new double[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        score[0] += Math.max(0, 100 - defenderResistance);
                                        if (airSuperiorityTeamId == teamId || groundControlTeamId == teamId || blockadeTeamId == teamId) {
                                                score[0] += 10d;
                                        }
                                }
                        });
                        return score[0];
                }

                @Override
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class CounterAdjustedForwardWarObjective implements TeamScoreObjective {
                @Override
                public double scoreTerminal(TeamScoreView view, int teamId) {
                        if (!(view instanceof TeamWarControlView controlView)) {
                                return 0d;
                        }
                        double[] score = new double[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId == teamId) {
                                        score[0] += 100d;
                                } else if (defenderTeamId == teamId) {
                                        score[0] -= 80d;
                                }
                        });
                        return score[0];
                }

                @Override
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
                        return 0d;
                }

                @Override
                public double scoreAction(SimWorld world, SimAction action, int teamId) {
                        return 0d;
                }
        }

        private static final class ReverseCounterWarCountObjective implements TeamScoreObjective {
                @Override
                public double scoreTerminal(TeamScoreView view, int teamId) {
                        if (!(view instanceof TeamWarControlView controlView)) {
                                return 0d;
                        }
                        int[] reverseWars = new int[1];
                        controlView.forEachWarControl((attackerTeamId, defenderTeamId, groundControlTeamId, airSuperiorityTeamId, blockadeTeamId, attackerResistance, defenderResistance) -> {
                                if (attackerTeamId != teamId && defenderTeamId == teamId) {
                                        reverseWars[0]++;
                                }
                        });
                        return reverseWars[0];
                }

                @Override
                public double scoreOpening(OpeningMetricVector metrics, int teamId) {
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
