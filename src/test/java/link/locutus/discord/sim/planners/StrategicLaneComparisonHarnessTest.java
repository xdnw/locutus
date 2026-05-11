package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicLaneComparisonHarnessTest {
    @Test
    void explicitLegacyProjectionPoliciesAreByteEquivalentToDefaultHarnessOutput() {
        String defaultCsv = normalizeDeterministicColumns(StrategicLaneComparisonHarness.renderCsv(
                72,
                1,
                8,
                StrategicLaneComparisonHarness.ProjectionPolicyPath.DEFAULT
        ));
        String explicitLegacyCsv = normalizeDeterministicColumns(StrategicLaneComparisonHarness.renderCsv(
                72,
                1,
                8,
                StrategicLaneComparisonHarness.ProjectionPolicyPath.EXPLICIT_LEGACY
        ));

        assertEquals(defaultCsv, explicitLegacyCsv);
    }

    @Test
    void laneHarnessHonorsScenarioAndLaneFilters() {
        String csv = StrategicLaneComparisonHarness.renderCsv(
                72,
                1,
                8,
                StrategicLaneComparisonHarness.ProjectionPolicyPath.DEFAULT,
                "activeWarSlotPressure",
                "projectedObjective"
        );

        String[] lines = csv.split("\\R");
        assertEquals(6, lines.length);
        for (int index = 1; index < lines.length; index++) {
            String[] columns = lines[index].split(",", -1);
            assertEquals("activeWarSlotPressure", columns[0]);
            assertEquals("projectedObjective", columns[1]);
        }
    }

    @Test
    void headToHeadHarnessRunsBothRoleSwappedPasses() {
        String csv = HeadToHeadComparisonHarness.renderCsv(
                72,
                1,
                8,
                HeadToHeadComparisonHarness.PolicySpec.parse("legacy:projectedObjective:NET_DAMAGE", "A"),
                HeadToHeadComparisonHarness.PolicySpec.parse("control:projectedObjective:CONTROL", "B"),
                "parity"
        );

        String[] lines = csv.split("\\R");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains(",AvsB,"));
        assertTrue(lines[2].contains(",BvsA,"));
    }

    private static String normalizeDeterministicColumns(String csv) {
        String[] lines = csv.split("\\R");
        StringBuilder normalized = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            int lastComma = line.lastIndexOf(',');
            int secondLastComma = lastComma < 0 ? -1 : line.lastIndexOf(',', lastComma - 1);
            normalized.append(secondLastComma < 0 ? line : line.substring(0, secondLastComma))
                    .append(System.lineSeparator());
        }
        return normalized.toString();
    }
}
