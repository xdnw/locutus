package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void headToHeadSamePolicyAvsBMatchesLaneHarnessScorecard() {
        String laneCsv = StrategicLaneComparisonHarness.renderCsv(
                72,
                1,
                8,
                StrategicLaneComparisonHarness.ProjectionPolicyPath.DEFAULT,
                "parity",
                "openingPrimitive"
        );
        String headToHeadCsv = HeadToHeadComparisonHarness.renderCsv(
                72,
                1,
                8,
                HeadToHeadComparisonHarness.PolicySpec.parse("legacy:openingPrimitive:NET_DAMAGE", "A"),
                HeadToHeadComparisonHarness.PolicySpec.parse("legacy:openingPrimitive:NET_DAMAGE", "B"),
                "parity"
        );

        Map<String, String> lane = firstDataRow(laneCsv);
        Map<String, String> headToHead = firstDataRow(headToHeadCsv);

        assertEquals(lane.get("assignments"), headToHead.get("attackerAssignmentCount"));
        assertEquals(lane.get("idleAttackersFreeSlot"), headToHead.get("attackerIdleViable"));
        assertEquals(lane.get("strongDefenderCoveragePct"), headToHead.get("attackerStrongDefenderCoveragePct"));
        assertEquals(lane.get("terminalObjective"), headToHead.get("attackerTerminalObjective"));
        assertEquals(lane.get("attackersAtCap"), headToHead.get("attackersAtCap"));
        assertEquals(lane.get("attackersAtTwoWars"), headToHead.get("attackersAtTwoWars"));
        assertEquals(lane.get("attackerCapSaturationPct"), headToHead.get("attackerCapSaturationPct"));
        assertEquals(lane.get("attackerWarCountHistogram"), headToHead.get("attackerWarCountHistogram"));
        assertEquals(lane.get("respondingSideLaterDeclarations"), headToHead.get("respondingSideLaterDeclarations"));
        assertEquals(lane.get("openingSideLaterDeclarations"), headToHead.get("openingSideLaterDeclarations"));
        assertEquals(lane.get("respondingSideLaterDeclarationsThrottled"), headToHead.get("respondingSideLaterDeclarationsThrottled"));
        assertEquals(lane.get("respondingSideLaterDeclarationCapPressurePct"), headToHead.get("respondingSideLaterDeclarationCapPressurePct"));
        assertEquals(lane.get("attackChoiceCalls"), headToHead.get("attackChoiceCalls"));
        assertEquals(lane.get("noAttackChoices"), headToHead.get("noAttackChoices"));
        assertEquals(lane.get("noAttackChoicePct"), headToHead.get("noAttackChoicePct"));
        assertEquals(lane.get("specialistAttackSelections"), headToHead.get("specialistAttackSelections"));
        assertEquals(lane.get("selectedLaterDeclarations"), headToHead.get("selectedLaterDeclarations"));
        assertEquals(lane.get("selectedLaterDeclarationUnderStrengthPct"), headToHead.get("selectedLaterDeclarationUnderStrengthPct"));
    }

    @Test
    void headToHeadPolicyFlagsConfigureOpeningWeightsAndAdmission() {
        HeadToHeadComparisonHarness.PolicySpec spec = HeadToHeadComparisonHarness.PolicySpec.parse(
                "weighted:projectedObjective:CONTROL:audit=3;laterCap=4;war=RAID:1.5,ORD:0.7;attack=MISSILE:2.0,NUKE:2.5;minProbe=0.05;specialists=true;positiveBaseline=false",
                "A"
        );

        SidePolicy policy = spec.actingPolicy();

        assertEquals(3, policy.planner().projectedAuditLimit());
        assertEquals(4, policy.planner().maxLaterDeclarationsPerTurn());
        assertEquals(1.5d, policy.opening().warTypeWeight(WarType.RAID), 1e-9);
        assertEquals(0.7d, policy.opening().warTypeWeight(WarType.ORD), 1e-9);
        assertEquals(1.0d, policy.opening().warTypeWeight(WarType.ATT), 1e-9);
        assertEquals(2.0d, policy.opening().attackTypeWeight(AttackType.MISSILE), 1e-9);
        assertEquals(2.5d, policy.opening().attackTypeWeight(AttackType.NUKE), 1e-9);
        assertEquals(1.0d, policy.opening().attackTypeWeight(AttackType.GROUND), 1e-9);
        assertEquals(0.05d, policy.opening().minimumViabilityProbe(), 1e-9);
        assertTrue(policy.opening().admissionPolicy().allowLegalSpecialistFallback());
        assertFalse(policy.opening().admissionPolicy().admitPositiveOpeningBaseline());
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
