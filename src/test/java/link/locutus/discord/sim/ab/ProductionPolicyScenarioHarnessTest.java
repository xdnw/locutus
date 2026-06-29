package link.locutus.discord.sim.ab;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPolicyScenarioHarnessTest {
    @Test
    void scenarioHarnessHonorsScenarioModeAndObjectiveFilters() {
        String csv = ProductionPolicyScenarioHarness.renderCsv(
                24,
                1,
                8,
                "activeWarSlotPressure",
                "projection",
                "CONTROL"
        );

        String[] lines = csv.split("\\R");
        assertEquals(2, lines.length);
        String[] columns = lines[1].split(",", -1);
        assertEquals("activeWarSlotPressure", columns[0]);
        assertEquals("projection", columns[1]);
        assertEquals("CONTROL", columns[2]);
    }

    @Test
    void samePolicyScenarioHarnessMatchesAbHarnessSharedMetrics() {
        String scenarioCsv = ProductionPolicyScenarioHarness.renderCsv(
                24,
                1,
                4,
                "parity",
                "projection",
                "CONTROL"
        );
        String headToHeadCsv = ProductionPolicyAbHarness.renderCsv(
                24,
                1,
                4,
                ProductionPolicyAbHarness.PolicySpec.parse("A:projection:CONTROL", "A"),
                ProductionPolicyAbHarness.PolicySpec.parse("B:projection:CONTROL", "B"),
                "parity"
        );

        Map<String, String> scenario = firstDataRow(scenarioCsv);
        Map<String, String> headToHead = firstDataRow(headToHeadCsv);

        assertEquals(scenario.get("assignmentPairs"), headToHead.get("assignmentPairs"));
        assertEquals(scenario.get("idleAttackersFreeSlot"), headToHead.get("idleAttackersFreeSlot"));
        assertEquals(scenario.get("objectiveMean"), headToHead.get("objectiveMean"));
        assertEquals(scenario.get("assignedWarTypes"), headToHead.get("assignedWarTypes"));
        assertEquals(scenario.get("assignedAttackTypes"), headToHead.get("assignedAttackTypes"));
    }

    @Test
    void beigeRebuildBreakoutPreservesOpeningRebuildWindowForControl() {
        String csv = ProductionPolicyScenarioHarness.renderCsv(
                72,
                1,
                8,
                "beigeRebuildBreakout",
                "projection",
                "CONTROL"
        );

        Map<String, String> control = firstDataRow(csv);

        assertEquals("0", control.get("assignmentPairs"));
        assertEquals("8", control.get("idleAttackersFreeSlot"));
        assertEquals("none", control.get("assignedAttackTypes"));
    }

    @Test
    void conventionalThenSpecialistsUsesConventionalOpeningBeforeMissilesForControl() {
        String csv = ProductionPolicyScenarioHarness.renderCsv(
                72,
                1,
                50,
                "conventionalThenSpecialists",
                "projection",
                "CONTROL"
        );

        Map<String, String> control = firstDataRow(csv);

        assertTrue(Integer.parseInt(control.get("assignmentPairs")) > 0);
        assertTrue(control.get("assignedAttackTypes").contains("GROUND"));
        assertFalse(control.get("assignedAttackTypes").contains("AIRSTRIKE_MONEY"));
        assertFalse(control.get("assignedAttackTypes").contains("MISSILE"));
    }

    @Test
    void activeWarFixtureRetainsExactSeedWarsForDecisiveScenario() {
        ProductionPolicyAbHarness.Fixture fixture = ProductionPolicyAbHarness.ScenarioFamily.ACTIVE_WAR_DECISIVE.fixture(6);

        assertEquals(6, fixture.activeWars().size());
        assertEquals(10_000, fixture.activeWars().get(0).attackerNationId());
        assertEquals(20_000, fixture.activeWars().get(0).defenderNationId());

        String csv = ProductionPolicyScenarioHarness.renderCsv(
                24,
                1,
                6,
                "activeWarDecisive",
                "projection",
                "CONTROL"
        );

        Map<String, String> row = firstDataRow(csv);
        assertEquals("activeWarDecisive", row.get("family"));
        assertTrue(Integer.parseInt(row.get("assignmentPairs")) >= 0);
    }

    private static Map<String, String> firstDataRow(String csv) {
        String[] lines = csv.split("\\R");
        String[] headers = lines[0].split(",", -1);
        String[] values = lines[1].split(",", -1);
        Map<String, String> row = new HashMap<>();
        for (int index = 0; index < headers.length; index++) {
            row.put(headers[index], index < values.length ? values[index] : "");
        }
        return row;
    }
}