package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.compile.CompiledActiveWar;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongHorizonForwardProjectionStateTest {
    @Test
    void projectionViewExposesRawHorizonStateForAssignedOpening() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 2_400, 240_000, 24_000, 2_400, 220, 2)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900, 90_000, 9_000, 900, 80, 0)
        );
        CandidateEdgeTable edges = oneEdge();

        LongHorizonForwardProjection.ProjectionView empty = project(
                attackers,
                defenders,
                edges,
                new boolean[]{false},
                new int[]{0},
                new int[]{0},
                24
        );
        LongHorizonForwardProjection.ProjectionView assigned = project(
                attackers,
                defenders,
                edges,
                new boolean[]{true},
                new int[]{1},
                new int[]{1},
                24
        );

        LongHorizonForwardProjection.ProjectedNationState emptyDefender = empty.projectedNationState(101);
        LongHorizonForwardProjection.ProjectedNationState assignedDefender = assigned.projectedNationState(101);

        assertTrue(
                assignedDefender.combatUnitCount() < emptyDefender.combatUnitCount()
                        || assignedDefender.totalInfra() < emptyDefender.totalInfra()
                        || assignedDefender.beigeTurns() > emptyDefender.beigeTurns(),
                "Assigned opening must be visible through raw horizon-end units, infra, or beige state"
        );
    }

        @Test
        void plannerChosenOpeningProjectsNoWorseRawEnemyStateThanHandBuiltKnownGoodOpening() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 650, 16_000, 1_600, 650, 0, 1), 1_050.0d),
                withTotalScore(nation(2, 1, 220, 5_000, 500, 220, 0, 1), 900.0d)
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 520, 13_000, 1_300, 520, 0, 0), 1_020.0d),
                withTotalScore(nation(102, 2, 180, 4_000, 400, 180, 0, 0), 840.0d)
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 0, 0, 100.0f);
        addGroundEdge(edges, 0, 1, 99.0f);
        addGroundEdge(edges, 1, 0, 98.0f);
        addGroundEdge(edges, 1, 1, 97.0f);

        CompiledScenario scenario = compile(attackers, defenders);
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment chosen = planner.assign(
                attackers,
                defenders,
                SidePolicy.heuristicActing("acting", planner.objective()),
                SidePolicy.heuristicPassive("defending", planner.objective()),
                0,
                List.of(),
                1
        );

        assertTrue(
                chosen.pairCount() > 0,
                "Representative fixture is invalid because the planner produced an empty opening: " + chosen.assignment()
        );

        boolean[] chosenEdgeAssigned = edgeAssigned(scenario, edges, chosen.assignment());
        int[] chosenAttackerCounts = countsByAttacker(edges, chosenEdgeAssigned, scenario.attackerCount());
        int[] chosenDefenderCounts = countsByDefender(edges, chosenEdgeAssigned, scenario.defenderCount());
        LongHorizonForwardProjection.ProjectionView chosenView = project(
                scenario,
                edges,
                chosenEdgeAssigned,
                chosenAttackerCounts,
                chosenDefenderCounts,
                36,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );

        Map<Integer, List<Integer>> knownGoodAssignment = Map.of(
                1, List.of(101),
                2, List.of(102)
        );
        boolean[] knownGoodEdgeAssigned = edgeAssigned(scenario, edges, knownGoodAssignment);
        int[] knownGoodAttackerCounts = countsByAttacker(edges, knownGoodEdgeAssigned, scenario.attackerCount());
        int[] knownGoodDefenderCounts = countsByDefender(edges, knownGoodEdgeAssigned, scenario.defenderCount());
        LongHorizonForwardProjection.ProjectionView knownGoodView = project(
                scenario,
                edges,
                knownGoodEdgeAssigned,
                knownGoodAttackerCounts,
                knownGoodDefenderCounts,
                36,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );

        LongHorizonForwardProjection.ProjectedNationState chosenPriorityDefender = chosenView.projectedNationState(101);
        LongHorizonForwardProjection.ProjectedNationState knownGoodPriorityDefender = knownGoodView.projectedNationState(101);
        LongHorizonForwardProjection.ProjectedNationState chosenTrapDefender = chosenView.projectedNationState(102);
        LongHorizonForwardProjection.ProjectedNationState knownGoodTrapDefender = knownGoodView.projectedNationState(102);

        assertTrue(
                chosenPriorityDefender.money() <= knownGoodPriorityDefender.money(),
                "The planner-chosen opening should project at least as much damage onto the higher-value defender as the hand-built known-good opening"
                        + "; chosen=" + chosen.assignment()
                        + ", defender101 chosen=" + describeState(chosenPriorityDefender)
                        + ", defender101 knownGood=" + describeState(knownGoodPriorityDefender)
        );
        assertTrue(
                chosenTrapDefender.beigeTurns() <= knownGoodTrapDefender.beigeTurns(),
                "The planner-chosen opening should avoid a worse low-value beige trap than the hand-built known-good opening"
                        + "; chosen=" + chosen.assignment()
                        + ", defender102 chosen=" + describeState(chosenTrapDefender)
                        + ", defender102 knownGood=" + describeState(knownGoodTrapDefender)
        );
    }

    @Test
    void beigeTrapProjectsToHigherDefenderRebuildThanSustainedAttrition() {
        DBNationSnapshot attackerOne = withTotalScore(nation(1, 1, 350, 38_000, 3_800, 350, 35, 1), 1_820.0d);
        DBNationSnapshot attackerTwo = withTotalScore(nation(2, 1, 1_900, 210_000, 21_000, 1_900, 160, 1), 2_320.0d);
        DBNationSnapshot defenderOne = withTotalScore(nation(101, 2, 500, 55_000, 5_500, 500, 50, 1), 1_860.0d);
        DBNationSnapshot defenderTwo = withTotalScore(nation(102, 2, 700, 80_000, 8_000, 700, 70, 1), 1_900.0d);

        LongHorizonForwardProjection.ProjectionView beigeTrap = project(
                List.of(attackerOne, attackerTwo),
                List.of(defenderOne.toBuilder().beigeTurns(19).build(), defenderTwo),
                List.of(),
                new CandidateEdgeTable(),
                new boolean[0],
                new int[]{0, 0},
                new int[]{0, 0},
                8,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );
        LongHorizonForwardProjection.ProjectionView attrition = project(
                List.of(
                        attackerOne.toBuilder()
                                .currentOffensiveWars(1)
                                .activeOpponentNationId(101)
                                .build(),
                        attackerTwo
                ),
                List.of(
                        defenderOne.toBuilder()
                                .currentDefensiveWars(1)
                                .activeOpponentNationId(1)
                                .build(),
                        defenderTwo
                ),
                List.of(
                        new CompiledActiveWar(
                                1,
                                101,
                                WarType.ORD,
                                0,
                                10,
                                6,
                                84,
                                24,
                                CompiledActiveWar.FlagOwner.ATTACKER,
                                CompiledActiveWar.FlagOwner.ATTACKER,
                                CompiledActiveWar.FlagOwner.NONE,
                                false,
                                false
                        )
                ),
                new CandidateEdgeTable(),
                new boolean[0],
                new int[]{0, 0},
                new int[]{0, 0},
                8,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );

        LongHorizonForwardProjection.ProjectedNationState beigeDefender = beigeTrap.projectedNationState(101);
        LongHorizonForwardProjection.ProjectedNationState attritionDefender = attrition.projectedNationState(101);

        assertTrue(
                beigeDefender.beigeTurns() < attritionDefender.beigeTurns(),
                "Expected the beige-trap line to have fewer remaining beige turns at the horizon because the defender entered beige earlier; beige="
                        + describeState(beigeDefender)
                        + ", attrition=" + describeState(attritionDefender)
        );
        assertTrue(
                projectedCombatUnits(beigeTrap, 101) >= projectedCombatUnits(attrition, 101),
                "The beige-trap opening should preserve at least as much defender combat capacity at horizon end because the beige rebuild bonus is active"
                        + "; beige=" + describeState(beigeDefender)
                        + ", attrition=" + describeState(attritionDefender)
        );
    }

    @Test
    void slotOccupancyPreservesProtectedFriendlyStateWhenEnemyAttackerIsAlreadyOccupied() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 350, 42_000, 4_000, 350, 35, 1), 1_780.0d),
                withTotalScore(nation(2, 1, 260, 30_000, 3_000, 260, 25, 1), 1_700.0d)
        );
        List<DBNationSnapshot> idleDefenders = List.of(
                withTotalScore(nation(201, 2, 950, 110_000, 11_000, 950, 90, 1), 2_000.0d)
                        .toBuilder()
                        .maxOff(1)
                        .build(),
                withTotalScore(nation(202, 2, 500, 55_000, 5_500, 500, 50, 1), 1_850.0d)
                        .toBuilder()
                        .maxOff(0)
                        .build()
        );
        List<DBNationSnapshot> occupiedDefenders = List.of(
                idleDefenders.get(0).toBuilder()
                        .currentOffensiveWars(1)
                        .activeOpponentNationId(2)
                        .build(),
                idleDefenders.get(1)
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 1, 1, 100.0f);
        List<CompiledActiveWar> occupiedEnemyWar = List.of(
                new CompiledActiveWar(
                        201,
                        2,
                        WarType.ORD,
                        0,
                        8,
                        8,
                        68,
                        58,
                        CompiledActiveWar.FlagOwner.ATTACKER,
                        CompiledActiveWar.FlagOwner.NONE,
                        CompiledActiveWar.FlagOwner.NONE,
                        false,
                        false
                )
        );

        ProjectionRun occupied = projectRun(
                attackers,
                occupiedDefenders,
                occupiedEnemyWar,
                edges,
                new boolean[]{true},
                new int[]{0, 1},
                new int[]{0, 1},
                36,
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
        ProjectionRun idle = projectRun(
                attackers,
                idleDefenders,
                List.of(),
                edges,
                new boolean[]{true},
                new int[]{0, 1},
                new int[]{0, 1},
                36,
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );

        LongHorizonForwardProjection.ProjectedNationState occupiedFriendly = occupied.view().projectedNationState(1);
        LongHorizonForwardProjection.ProjectedNationState idleFriendly = idle.view().projectedNationState(1);
        int occupiedCounters = occupied.evaluation().realizedCounterIncidence()[0];
        int idleCounters = idle.evaluation().realizedCounterIncidence()[0];

        assertTrue(
                occupiedFriendly.combatUnitCount() > idleFriendly.combatUnitCount()
                        || occupiedFriendly.totalInfra() > idleFriendly.totalInfra(),
                "An already-occupied enemy attacker should leave the protected friendly in a better horizon-end state than an unused slot"
                        + "; occupied=" + describeState(occupiedFriendly)
                        + ", idle=" + describeState(idleFriendly)
        );
        assertTrue(
                occupiedCounters < idleCounters,
                "Slot occupation should suppress realized counter incidence against the protected friendly"
                        + "; occupiedCounters=" + occupiedCounters
                        + ", idleCounters=" + idleCounters
        );
    }

    @Test
    void projectedEvaluationExposesOpeningSideLaterDeclarations() {
        List<DBNationSnapshot> attackers = List.of(
                withTotalScore(nation(1, 1, 900, 100_000, 10_000, 900, 90, 2), 2_000.0d)
                        .toBuilder()
                        .maxOff(2)
                        .build()
        );
        List<DBNationSnapshot> defenders = List.of(
                withTotalScore(nation(101, 2, 500, 55_000, 5_500, 500, 50, 0), 1_850.0d)
                        .toBuilder()
                        .maxOff(0)
                        .build(),
                withTotalScore(nation(102, 2, 480, 52_000, 5_200, 480, 48, 0), 1_830.0d)
                        .toBuilder()
                        .maxOff(0)
                        .build()
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 0, 0, 100.0f);
        addGroundEdge(edges, 0, 1, 95.0f);

        ProjectionRun projected = projectRun(
                attackers,
                defenders,
                edges,
                new boolean[]{true, false},
                new int[]{1},
                new int[]{1, 0},
                24,
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );

        assertFalse(
                projected.evaluation().openingSideLaterDeclarations().isEmpty(),
                "Projection should expose the delayed opening-side follow-on instead of hiding it behind only objective scores"
        );
        assertEquals(
                102,
                projected.evaluation().openingSideLaterDeclarations().getFirst().targetNationId(),
                "Projected later declarations should name the concrete reserve target"
        );
    }

    @Test
    void unitBackedFlagsProjectBetterThanEmptyFlags() {
        List<DBNationSnapshot> backedAttackers = List.of(
                nation(1, 1, 2_200, 230_000, 20_000, 2_200, 210, 2),
                nation(2, 1, 2_200, 230_000, 20_000, 2_200, 210, 2)
        );
        List<DBNationSnapshot> emptyAttackers = List.of(
                nation(1, 1, 0, 0, 0, 0, 0, 2),
                nation(2, 1, 0, 0, 0, 0, 0, 2)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 1_050, 98_000, 11_000, 1_050, 100, 1)
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 0, 0, 100.0f);
        addGroundEdge(edges, 1, 0, 99.0f);
        CompiledActiveWar fortifiedWar = new CompiledActiveWar(
                1,
                101,
                WarType.ORD,
                0,
                8,
                8,
                70,
                64,
                CompiledActiveWar.FlagOwner.ATTACKER,
                CompiledActiveWar.FlagOwner.ATTACKER,
                CompiledActiveWar.FlagOwner.ATTACKER,
                false,
                false
        );

        LongHorizonForwardProjection.ProjectionView backed = project(
                backedAttackers,
                defenders,
                List.of(fortifiedWar),
                edges,
                new boolean[]{true, false},
                new int[]{1, 0},
                new int[]{1},
                24,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );
        LongHorizonForwardProjection.ProjectionView empty = project(
                emptyAttackers,
                defenders,
                List.of(fortifiedWar),
                edges,
                new boolean[]{true, false},
                new int[]{1, 0},
                new int[]{1},
                24,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );

        assertTrue(
                projectedCombatUnits(backed, 101) < projectedCombatUnits(empty, 101),
                "Unit-backed control should project to lower enemy combat units than the same flags without units behind them"
        );
    }

    private static LongHorizonForwardProjection.ProjectionView project(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        LongHorizonForwardProjection projection = LongHorizonForwardProjection.create(
                edges,
                scenario,
                fill(scenario.attackerCount(), 3),
                horizonTurns,
                1.0d,
                null,
                null,
                null,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
        return projection.project(edgeAssigned, attackerCounts, defenderCounts);
    }

    private static ProjectionRun projectRun(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        LongHorizonForwardProjection projection = LongHorizonForwardProjection.create(
                edges,
                scenario,
                fill(scenario.attackerCount(), 3),
                horizonTurns,
                1.0d,
                null,
                null,
                null,
                SidePlannerSettings.defaults(),
                SidePlannerSettings.defaults(),
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
        return new ProjectionRun(
                projection.project(edgeAssigned, attackerCounts, defenderCounts),
                projection.projectedEvaluation(
                        new link.locutus.discord.sim.DamageObjective(),
                        scenario.attacker(0).teamId(),
                        edgeAssigned,
                        attackerCounts,
                        defenderCounts
                )
        );
    }

    private static ProjectionRun projectRun(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        return projectRun(
                compile(attackers, defenders),
                edges,
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                horizonTurns,
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
    }

    private static ProjectionRun projectRun(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            List<CompiledActiveWar> activeWars,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        return projectRun(
                compile(attackers, defenders, activeWars),
                edges,
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                horizonTurns,
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
    }

    private static LongHorizonForwardProjection.ProjectionView project(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns
    ) {
        return project(
                attackers,
                defenders,
                List.of(),
                edges,
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                horizonTurns,
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );
    }

    private static LongHorizonForwardProjection.ProjectionView project(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            List<CompiledActiveWar> activeWars,
            CandidateEdgeTable edges,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int horizonTurns,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        return project(
                compile(attackers, defenders, activeWars),
                edges,
                edgeAssigned,
                attackerCounts,
                defenderCounts,
                horizonTurns,
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
    }

    private static CompiledScenario compile(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
    ) {
        return compile(attackers, defenders, List.of());
    }

    private static CompiledScenario compile(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            List<CompiledActiveWar> activeWars
    ) {
        return new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(),
                activeWars
        );
    }

    private static boolean[] edgeAssigned(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            Map<Integer, List<Integer>> assignment
    ) {
        boolean[] edgeAssigned = new boolean[edges.edgeCount()];
        long[] pairKeys = edges.edgePairKeys(attackerNationIds(scenario), defenderNationIds(scenario));
        java.util.Set<Long> assignedPairs = new java.util.HashSet<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            int attackerNationId = entry.getKey();
            for (int defenderNationId : entry.getValue()) {
                assignedPairs.add(pairKey(attackerNationId, defenderNationId));
            }
        }
        for (int edgeIndex = 0; edgeIndex < pairKeys.length; edgeIndex++) {
            if (assignedPairs.contains(pairKeys[edgeIndex])) {
                edgeAssigned[edgeIndex] = true;
            }
        }
        return edgeAssigned;
    }

    private static int[] countsByAttacker(CandidateEdgeTable edges, boolean[] edgeAssigned, int attackerCount) {
        int[] attackerCounts = new int[attackerCount];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (edgeAssigned[edgeIndex]) {
                attackerCounts[edges.attackerIndex(edgeIndex)]++;
            }
        }
        return attackerCounts;
    }

    private static int[] countsByDefender(CandidateEdgeTable edges, boolean[] edgeAssigned, int defenderCount) {
        int[] defenderCounts = new int[defenderCount];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (edgeAssigned[edgeIndex]) {
                defenderCounts[edges.defenderIndex(edgeIndex)]++;
            }
        }
        return defenderCounts;
    }

    private static int[] attackerNationIds(CompiledScenario scenario) {
        int[] ids = new int[scenario.attackerCount()];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = scenario.attackerNationId(index);
        }
        return ids;
    }

    private static int[] defenderNationIds(CompiledScenario scenario) {
        int[] ids = new int[scenario.defenderCount()];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = scenario.defenderNationId(index);
        }
        return ids;
    }

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) ^ (defenderNationId & 0xffffffffL);
    }

    private static CandidateEdgeTable oneEdge() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 0, 0, 100.0f);
        return edges;
    }

    private static void addGroundEdge(CandidateEdgeTable edges, int attackerIndex, int defenderIndex, float score) {
        edges.add(
                attackerIndex,
                defenderIndex,
                (byte) WarType.ORD.ordinal(),
                (byte) AttackType.GROUND.ordinal(),
                score,
                0.0f
        );
    }

    private static double projectedCombatUnits(LongHorizonForwardProjection.ProjectionView view, int nationId) {
        LongHorizonForwardProjection.ProjectedNationState state = view.projectedNationState(nationId);
        return state.soldiers()
                + 10.0d * state.tanks()
                + 100.0d * state.aircraft()
                + 500.0d * state.ships();
    }

    private record ProjectionRun(
            LongHorizonForwardProjection.ProjectionView view,
            LongHorizonForwardProjection.ProjectedEvaluation evaluation
    ) {
    }

        private static String describeState(LongHorizonForwardProjection.ProjectedNationState state) {
                return "ProjectedNationState{"
                                + "nationId=" + state.nationId()
                                + ", teamId=" + state.teamId()
                                + ", score=" + state.score()
                                + ", infra=" + state.totalInfra()
                                + ", money=" + state.money()
                                + ", soldiers=" + state.soldiers()
                                + ", tanks=" + state.tanks()
                                + ", aircraft=" + state.aircraft()
                                + ", ships=" + state.ships()
                                + ", beigeTurns=" + state.beigeTurns()
                                + ", activeOffensiveWars=" + state.activeOffensiveWars()
                                + ", activeDefensiveWars=" + state.activeDefensiveWars()
                                + '}';
        }

    private static int[] fill(int length, int value) {
        int[] values = new int[length];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static DBNationSnapshot nation(
            int nationId,
            int teamId,
            int aircraft,
            int soldiers,
            int tanks,
            int planes,
            int ships,
            int maxOff
    ) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(teamId)
                .allianceId(teamId)
                .cities(12)
                .cityInfra(uniformInfra(12, 1_500.0d))
                .maxOff(maxOff)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .unit(MilitaryUnit.TANK, tanks)
                .unit(MilitaryUnit.AIRCRAFT, Math.max(aircraft, planes))
                .unit(MilitaryUnit.SHIP, ships)
                .resource(ResourceType.MONEY, 500_000_000d)
                .resource(ResourceType.FOOD, 100_000_000d)
                .resource(ResourceType.GASOLINE, 50_000_000d)
                .resource(ResourceType.MUNITIONS, 50_000_000d)
                .resource(ResourceType.STEEL, 50_000_000d)
                .resource(ResourceType.ALUMINUM, 50_000_000d)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] values = new double[cities];
        java.util.Arrays.fill(values, infra);
        return values;
    }

        private static DBNationSnapshot withTotalScore(DBNationSnapshot snapshot, double totalScore) {
                double unitScore = 0d;
                for (MilitaryUnit unit : List.of(MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP)) {
                        unitScore += unit.getScore(snapshot.unit(unit));
                }
                int cities = Math.max(1, snapshot.cityInfraCount());
                double totalInfra = Math.max(0d, (totalScore - snapshot.staticScoreComponent() - unitScore) * 40d);
                return snapshot.toBuilder()
                                .cityInfra(uniformInfra(cities, totalInfra / cities))
                                .build();
        }

}
