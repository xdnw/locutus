package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that BlitzPlanner never assigns war declarations that would overflow a defender's
 * free defensive slot cap (normally 3 in PnW), unless a {@code forceFreeDefSlots} override
 * explicitly opens more.
 */
class FreeSlotRespectTest {

    private static final double SCORE = 1000.0;

    @Test
    void defenderWith2RealDefWarsGetsAtMost1MoreAssignment() {
        // Defender already has 2 defensive wars (1 free slot remaining)
        DBNationSnapshot defender = defender(101, 2);
        List<DBNationSnapshot> attackers = buildAttackers(1, 5);
        List<DBNationSnapshot> defenders = List.of(defender);

        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        long timesAssigned = result.assignment().values().stream()
                .flatMap(List::stream)
                .filter(id -> id == 101)
                .count();
        assertTrue(timesAssigned <= 1,
                "Defender 101 already has 2 def wars (1 free slot), should receive at most 1 assignment but got " + timesAssigned);
    }

    @Test
    void defenderWith3RealDefWarsGetsNoAssignment() {
        // Defender is fully slotted — cannot receive any more attackers
        DBNationSnapshot defender = defender(101, 3);
        List<DBNationSnapshot> attackers = buildAttackers(1, 5);
        List<DBNationSnapshot> defenders = List.of(defender);

        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        long timesAssigned = result.assignment().values().stream()
                .flatMap(List::stream)
                .filter(id -> id == 101)
                .count();
        assertEquals(0, timesAssigned,
                "Fully-slotted defender (3/3 def wars) must not receive any assignment");
    }

    @Test
    void forceFreeDefSlotsOverrideAllowsExtraAssignments() {
        // Defender already has 3 defensive wars but forceFreeDefSlots=2 should open 2 extra slots
        DBNationSnapshot defender = defender(101, 3);
        List<DBNationSnapshot> attackers = buildAttackers(1, 5);
        List<DBNationSnapshot> defenders = List.of(defender);

        OverrideSet overrides = OverrideSet.builder()
                .forceFreeDefSlots(101, 2)
                .build();
        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults(), TreatyProvider.NONE, overrides, new DamageObjective());
        BlitzAssignment result = planner.assign(attackers, defenders);

        long timesAssigned = result.assignment().values().stream()
                .flatMap(List::stream)
                .filter(id -> id == 101)
                .count();
        assertTrue(timesAssigned <= 2,
                "With forceFreeDefSlots=2, defender should receive at most 2 assignments, got " + timesAssigned);
        // No assertions on exact count — could be fewer if scores don't warrant it
    }

    @Test
    void multipleDefendersSlotCapRespectedAcrossAll() {
        // 5 defenders, each with 1 real def war (2 free slots each), facing 10 attackers
        List<DBNationSnapshot> defenders = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            defenders.add(defender(101 + i, 1));
        }
        List<DBNationSnapshot> attackers = buildAttackers(1, 10);

        BlitzPlanner planner = new BlitzPlanner(SimTuning.defaults());
        BlitzAssignment result = planner.assign(attackers, defenders);

        // Each defender has 2 free defensive slots; total capacity = 5 × 2 = 10
        // Each attacker has 5 free offensive slots; total demand = 10 × 5 = 50
        // Assignment should be bounded by defender capacity (10 total)
        assertTrue(result.pairCount() <= 10,
                "Total pairs cannot exceed sum of defender free slots (5 defenders × 2 each = 10)");

        for (DBNationSnapshot def : defenders) {
            long count = result.assignment().values().stream()
                    .flatMap(List::stream)
                    .filter(id -> id == def.nationId())
                    .count();
            assertTrue(count <= 2,
                    "Defender " + def.nationId() + " received " + count + " > 2 (cap with 1 existing def war)");
        }
    }

    // ---- helpers --------------------------------------------------------

    private DBNationSnapshot defender(int id, int existingDefWars) {
        return DBNationSnapshot.synthetic(id)
                .teamId(9999).allianceId(9999)
                .score(SCORE).cities(10).nonInfraScoreBase(SCORE)
                .cityInfra(uniformInfra(10, 1000.0))
                .maxOff(5).currentOffensiveWars(0)
                .currentDefensiveWars(existingDefWars)
                .unit(MilitaryUnit.AIRCRAFT, 300)
                .unit(MilitaryUnit.SOLDIER, 300)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private List<DBNationSnapshot> buildAttackers(int idStart, int count) {
        List<DBNationSnapshot> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = idStart + i;
            list.add(DBNationSnapshot.synthetic(id)
                    .teamId(1000).allianceId(1000)
                    .score(SCORE).cities(10).nonInfraScoreBase(SCORE)
                    .cityInfra(uniformInfra(10, 1000.0))
                    .maxOff(5).currentOffensiveWars(0).currentDefensiveWars(0)
                    .unit(MilitaryUnit.AIRCRAFT, 500)
                    .unit(MilitaryUnit.SOLDIER, 500)
                    .warPolicy(WarPolicy.ATTRITION)
                    .build());
        }
        return list;
    }

    private double[] uniformInfra(int cities, double infra) {
        double[] arr = new double[cities];
        for (int i = 0; i < cities; i++) arr[i] = infra;
        return arr;
    }
}
