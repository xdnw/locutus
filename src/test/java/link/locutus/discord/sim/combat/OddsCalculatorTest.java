package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.PW;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OddsCalculatorTest {

    @Test
    void oddsVectorSumsToOneForNonDegenerateStrengths() {
        double[] odds = OddsCalculator.odds(1000, 800, AttackType.GROUND);

        assertEquals(SuccessType.values.length, odds.length);
        double sum = 0;
        for (double p : odds) {
            assertTrue(p >= 0, "probabilities must be non-negative");
            sum += p;
        }
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void oddsMatchPwGetOddsPerSuccessLevel() {
        double attStr = 1250;
        double defStr = 1100;

        double[] odds = OddsCalculator.odds(attStr, defStr, AttackType.GROUND);

        for (int i = 0; i < SuccessType.values.length; i++) {
            assertEquals(PW.getOdds(attStr, defStr, i), odds[i], 1e-12,
                    "success level " + i + " should match PW.getOdds exactly");
        }
    }

    @Test
    void weakAttackerGuaranteedUtterFailure() {
        double[] odds = OddsCalculator.odds(0, 1000, AttackType.GROUND);

        assertArrayEquals(new double[]{1.0, 0.0, 0.0, 0.0}, odds, 1e-12);
    }

    @Test
    void overwhelmingAttackerGuaranteedImmenseTriumph() {
        // defStr * 2.5 <= attStr triggers the early-return IT branch inside PW.getOdds.
        double[] odds = OddsCalculator.odds(10_000, 1_000, AttackType.GROUND);

        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 1.0}, odds, 1e-12);
    }

    @Test
    void batchResultsMatchScalarCalls() {
        double[] attStr = {500, 1000, 1500, 2000};
        double[] defStr = {1000, 1000, 1000, 1000};
        double[][] out = new double[attStr.length][SuccessType.values.length];

        OddsCalculator.oddsBatch(attStr, defStr, AttackType.GROUND, out);

        for (int i = 0; i < attStr.length; i++) {
            double[] scalar = OddsCalculator.odds(attStr[i], defStr[i], AttackType.GROUND);
            assertArrayEquals(scalar, out[i], 1e-12, "row " + i);
        }
    }

    @Test
    void cumulativeOddsMatchesTailSum() {
        double attStr = 900, defStr = 1000;
        double[] odds = OddsCalculator.odds(attStr, defStr, AttackType.GROUND);

        double tail = odds[SuccessType.MODERATE_SUCCESS.ordinal()] + odds[SuccessType.IMMENSE_TRIUMPH.ordinal()];
        assertEquals(tail,
                OddsCalculator.cumulativeOdds(attStr, defStr, SuccessType.MODERATE_SUCCESS),
                1e-12);
    }
}
