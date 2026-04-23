package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.util.PW;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarOutcomeMathTest {

    @Test
    void expectedAllianceLootPercentUsesMidpointRandomFactor() {
        double attackerScore = 2_400d;
        double allianceScore = 80_000d;

        double midpoint = WarOutcomeMath.expectedAllianceLootPercent(attackerScore, allianceScore);
        double explicitMidpoint = WarOutcomeMath.allianceLootPercent(attackerScore, allianceScore, 0.5);

        assertEquals(explicitMidpoint, midpoint, 1e-12);
    }

    @Test
    void allianceLootPercentClampsRandomFactorAndPercentCap() {
        double capped = WarOutcomeMath.allianceLootPercent(10_000_000d, 1d, 5.0);
        double zero = WarOutcomeMath.allianceLootPercent(5_000d, 50_000d, -1.0);

        assertEquals(0.33d, capped, 1e-12);
        assertEquals(0.0d, zero, 1e-12);
    }

    @Test
    void victoryNationLootPercentUsesRoleAwareWarTypeModifier() {
        double attackerWin = WarOutcomeMath.victoryNationLootPercent(
                WarOutcomeMath.combinedLootModifier(1.0, 1.0, WarType.ATT, true)
        );
        double defenderWin = WarOutcomeMath.victoryNationLootPercent(
                WarOutcomeMath.combinedLootModifier(1.0, 1.0, WarType.ATT, false)
        );

        assertEquals(0.025d, attackerWin, 1e-12);
        assertEquals(0.05d, defenderWin, 1e-12);
        assertTrue(defenderWin > attackerWin);
    }

    @Test
    void victoryInfraPercentUsesRoleAwareWarTypeModifier() {
        double attackerWin = WarOutcomeMath.victoryInfraPercent(1.0, 1.0, WarType.RAID, true);
        double defenderWin = WarOutcomeMath.victoryInfraPercent(1.0, 1.0, WarType.RAID, false);

        assertEquals(0.01d, attackerWin, 1e-12);
        assertEquals(0.02d, defenderWin, 1e-12);
        assertTrue(defenderWin > attackerWin);
    }

    @Test
    void victoryInfraTotalsUseRoundedPerCityPercentageRule() {
        double[] cityInfraBefore = {1_000.00d, 999.99d};

        WarOutcomeMath.VictoryInfraTotals totals = WarOutcomeMath.victoryInfraTotals(cityInfraBefore, 0.333d);

        assertEquals(66_600, totals.infraDestroyedCents());
        double expectedValue = PW.City.Infra.calculateInfra(667.00d, 1_000.00d)
                + PW.City.Infra.calculateInfra(666.99d, 999.99d);
        assertEquals(expectedValue, totals.infraDestroyedValue(), 1e-9);
    }
}