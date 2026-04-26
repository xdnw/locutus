package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackCommandsOutputGoldenTest {

    @Test
    void groundSimUtterFailureOutputMatchesGolden() {
        AttackCommands commands = new AttackCommands();

        String actual = commands.groundSim(0, 0, 0, 0, 1000, 50);

        String expected = "Attacker cannot use GROUND with current units/projects.";

        assertEquals(expected, actual);
    }

    @Test
    void airSimUtterFailureOutputMatchesGolden() {
        AttackCommands commands = new AttackCommands();

        String actual = commands.airSim(0, 1000);

        String expected = "Attacker cannot use AIRSTRIKE_AIRCRAFT with current units/projects.";

        assertEquals(expected, actual);
    }

    @Test
    void navalSimUtterFailureOutputMatchesGolden() {
        AttackCommands commands = new AttackCommands();

        String actual = commands.navalSim(0, 500);

        String expected = "Attacker cannot use NAVAL with current units/projects.";

        assertEquals(expected, actual);
    }

    @Test
    void casualtiesRejectsIllegalAttackTypeForCurrentUnits() {
        AttackCommands commands = new AttackCommands();
        DBNation attacker = nation(1, 9, 49, 2_000, 0, 0, 2_000_000, WarPolicy.BLITZKRIEG);
        DBNation defender = nation(2, 8, 45_000, 1_700, 1_700, 10, 1_500_000, WarPolicy.FORTRESS);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> commands.casualties(
                AttackType.GROUND,
                WarType.ORD,
                defender,
                attacker,
                false,
                Map.of(),
                Map.of(),
                false,
                false,
                WarPolicy.BLITZKRIEG,
                WarPolicy.FORTRESS,
                false,
                false,
                false,
                false,
                Set.of(),
                Set.of(),
                2000,
                2000
        ));

        assertEquals("Attacker cannot use GROUND with current units/projects.", error.getMessage());
    }

    @Test
    void casualtiesOutputContainsExpectedSections() {
        AttackCommands commands = new AttackCommands();
        DBNation attacker = nation(1, 9, 50_000, 2_000, 2_000, 12, 2_000_000, WarPolicy.BLITZKRIEG);
        DBNation defender = nation(2, 8, 45_000, 1_700, 1_700, 10, 1_500_000, WarPolicy.FORTRESS);

        String actual = commands.casualties(
                AttackType.GROUND,
                WarType.ORD,
                defender,
                attacker,
                false,
                Map.of(),
                Map.of(),
                false,
                false,
                WarPolicy.BLITZKRIEG,
                WarPolicy.FORTRESS,
                false,
                false,
                false,
                false,
                Set.of(),
                Set.of(),
                2000,
                2000
        );

        assertTrue(actual.startsWith("**GROUND**:"));
        assertTrue(actual.contains("Attacker Consumption (worth: ~$"));
        assertTrue(actual.contains("IMMENSE_TRIUMPH="));
        assertTrue(actual.contains("MODERATE_SUCCESS="));
        assertTrue(actual.contains("PYRRHIC_VICTORY="));
        assertTrue(actual.contains("UTTER_FAILURE="));
        assertTrue(actual.contains("Attacker losses:"));
        assertTrue(actual.contains("Defender losses:"));
    }

    private static DBNation nation(
            int id,
            int cities,
            int soldiers,
            int tanks,
            int aircraft,
            int ships,
            int money,
            WarPolicy warPolicy
    ) {
        TestNation nation = new TestNation(new DBNationData());
        nation.edit().setNation_id(id);
        nation.setCities(cities);
        nation.setUnits(MilitaryUnit.SOLDIER, soldiers);
        nation.setUnits(MilitaryUnit.TANK, tanks);
        nation.setUnits(MilitaryUnit.AIRCRAFT, aircraft);
        nation.setUnits(MilitaryUnit.SHIP, ships);
        nation.setUnits(MilitaryUnit.MONEY, money);
        nation.setWarPolicy(warPolicy);
        nation.setProjectsRaw(0L);
        return nation;
    }

    private static final class TestNation extends SimpleDBNation {
        private TestNation(DBNationData data) {
            super(data);
        }

        @Override
        public Map<Integer, DBCity> _getCitiesV3() {
            return Collections.emptyMap();
        }

        @Override
        public DBNation copy() {
            return new TestNation(new DBNationData(this.data()));
        }
    }
}
