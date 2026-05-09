package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
