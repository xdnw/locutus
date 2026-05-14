package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    void distributedKnownGoodOpeningProjectsBetterRawEnemyStateThanOverkillOpening() {
        List<DBNationSnapshot> attackers = List.of(
                nation(1, 1, 2_400, 250_000, 24_000, 2_400, 220, 1),
                nation(2, 1, 2_300, 235_000, 23_000, 2_300, 210, 1)
        );
        List<DBNationSnapshot> defenders = List.of(
                nation(101, 2, 900, 90_000, 9_000, 900, 80, 0),
                nation(102, 2, 900, 90_000, 9_000, 900, 80, 0)
        );
        CandidateEdgeTable edges = new CandidateEdgeTable();
        addGroundEdge(edges, 0, 0, 100.0f);
        addGroundEdge(edges, 0, 1, 99.0f);
        addGroundEdge(edges, 1, 0, 98.0f);
        addGroundEdge(edges, 1, 1, 97.0f);

        LongHorizonForwardProjection.ProjectionView overkill = project(
                attackers,
                defenders,
                edges,
                new boolean[]{true, false, true, false},
                new int[]{1, 1},
                new int[]{2, 0},
                36
        );
        LongHorizonForwardProjection.ProjectionView distributed = project(
                attackers,
                defenders,
                edges,
                new boolean[]{true, false, false, true},
                new int[]{1, 1},
                new int[]{1, 1},
                36
        );

        double overkillEnemyValue = projectedCombatUnits(overkill, 101) + projectedCombatUnits(overkill, 102);
        double distributedEnemyValue = projectedCombatUnits(distributed, 101) + projectedCombatUnits(distributed, 102);

        assertTrue(
                distributedEnemyValue < overkillEnemyValue,
                "The hand-built distributed opening should project to lower enemy combat units than overkilling one target"
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
        CompiledScenario scenario = new ScenarioCompiler().compile(
                attackers,
                defenders,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        LongHorizonForwardProjection projection = LongHorizonForwardProjection.create(
                edges,
                scenario,
                fill(attackers.size(), 3),
                horizonTurns,
                1.0d,
                null,
                null,
                null,
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
                SideProjectionPolicies.noDeclarations(),
                SideProjectionPolicies.noDeclarations()
        );
        return projection.project(edgeAssigned, attackerCounts, defenderCounts);
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

}
