package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.PW;

/**
 * Vectorised wrapper over {@link PW#getOdds(double, double, int)}.
 *
 * <p>PW's base formula depends only on attacker/defender strength and the success level.
 * The {@link AttackType} parameter is carried through so future per-attack-type distribution
 * adjustments (swappable via {@code OddsModel} in the sim layer) have somewhere to land; M0
 * ignores it and returns the raw PW distribution.
 */
public final class OddsCalculator {
    private OddsCalculator() {}

    /** Length-4 distribution over {@link SuccessType}, indexed by {@code SuccessType.ordinal()}. */
    public static double[] odds(double attStrength, double defStrength, AttackType type) {
        double[] out = new double[SuccessType.values.length];
        writeOdds(attStrength, defStrength, out);
        return out;
    }

    /**
     * Vectorised variant. {@code out[i]} is written as the length-4 distribution for pair {@code i}.
     * Caller sizes {@code out} to {@code attStr.length} and each row to {@code 4}.
     */
    public static void oddsBatch(double[] attStr, double[] defStr, AttackType type, double[][] out) {
        if (attStr.length != defStr.length || out.length != attStr.length) {
            throw new IllegalArgumentException("attStr, defStr, and out must be equal length");
        }
        for (int i = 0; i < attStr.length; i++) {
            writeOdds(attStr[i], defStr[i], out[i]);
        }
    }

    /** {@code P(success >= threshold)} summed from the distribution. */
    public static double cumulativeOdds(double attStrength, double defStrength, SuccessType threshold) {
        double p = 0;
        for (int i = threshold.ordinal(); i < SuccessType.values.length; i++) {
            p += PW.getOdds(attStrength, defStrength, i);
        }
        return p;
    }

    static void writeOdds(double attStr, double defStr, double[] target) {
        if (target.length != SuccessType.values.length) {
            throw new IllegalArgumentException("target must be sized to SuccessType.values.length");
        }
        for (int i = 0; i < target.length; i++) {
            target[i] = PW.getOdds(attStr, defStr, i);
        }
    }
}
