package link.locutus.discord.sim.ab;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.planners.SidePolicy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionPolicyAbHarnessTest {
    @Test
    void renderCsvIncludesBothRoleSwappedPasses() {
        String csv = ProductionPolicyAbHarness.renderCsv(
                24,
                1,
                4,
                ProductionPolicyAbHarness.PolicySpec.parse("A:projection:CONTROL", "A"),
                ProductionPolicyAbHarness.PolicySpec.parse("B:projection:NET_DAMAGE", "B"),
                "parity"
        );

        String[] lines = csv.split("\\R");
        assertEquals(3, lines.length);
        assertTrue(lines[1].contains(",AvsB,"));
        assertTrue(lines[2].contains(",BvsA,"));
        assertFalse(csv.contains("legacy"));
    }

    @Test
    void policySpecBuildsProductionPoliciesWithoutLegacyFactories() {
        ProductionPolicyAbHarness.PolicySpec spec = ProductionPolicyAbHarness.PolicySpec.parse(
                "A:attackChoice:CONTROL:audit=3;laterCap=4;war=RAID:1.5,ORD:0.7;attack=MISSILE:2.0,NUKE:2.5;minProbe=0.05;specialists=true;positiveBaseline=false",
                "A"
        );

        SidePolicy acting = spec.actingPolicy();
        SidePolicy passive = spec.passivePolicy();

        assertEquals(ProductionPolicyAbHarness.PolicyMode.ATTACK_CHOICE, spec.mode());
        assertTrue(acting.allowInitialDeclarations());
        assertFalse(passive.allowInitialDeclarations());
        assertEquals(3, acting.planner().projectedAuditLimit());
        assertEquals(4, acting.planner().maxLaterDeclarationsPerTurn());
        assertEquals(1.5d, acting.opening().warTypeWeight(WarType.RAID), 1e-9);
        assertEquals(0.7d, acting.opening().warTypeWeight(WarType.ORD), 1e-9);
        assertEquals(2.0d, acting.opening().attackTypeWeight(AttackType.MISSILE), 1e-9);
        assertEquals(2.5d, acting.opening().attackTypeWeight(AttackType.NUKE), 1e-9);
        assertEquals(0.05d, acting.opening().minimumViabilityProbe(), 1e-9);
        assertTrue(acting.opening().admissionPolicy().allowLegalSpecialistFallback());
        assertFalse(acting.opening().admissionPolicy().admitPositiveOpeningBaseline());
    }
}