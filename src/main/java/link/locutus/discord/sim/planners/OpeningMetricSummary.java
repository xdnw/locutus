package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.UnitEconomy;

final class OpeningMetricSummary {

    private OpeningMetricSummary() {
    }

    static double immediateHarm(MutableAttackResult result) {
        return expectedScoreLoss(result, false);
    }

    static double selfExposure(MutableAttackResult result) {
        return expectedScoreLoss(result, true);
    }

    static double controlLeverage(boolean attackerHasGroundControl, boolean attackerHasAirControl, boolean attackerHasBlockade) {
        double leverage = 0d;
        if (attackerHasGroundControl) {
            leverage += 1d;
        }
        if (attackerHasAirControl) {
            leverage += 1d;
        }
        if (attackerHasBlockade) {
            leverage += 1d;
        }
        return leverage;
    }

    static double futureWarLeverage(
            double initialAttackerGround,
            double currentAttackerGround,
            double initialDefenderGround,
            double currentDefenderGround,
            double initialAttackerAir,
            double currentAttackerAir,
            double initialDefenderAir,
            double currentDefenderAir,
            double initialAttackerNaval,
            double currentAttackerNaval,
            double initialDefenderNaval,
            double currentDefenderNaval,
            double initialDefenderInfra,
            double currentDefenderInfra,
            int defenderResistance
    ) {
        double resistancePressure = clamp01(
                (SimWar.INITIAL_RESISTANCE - Math.max(0, defenderResistance)) / (double) SimWar.INITIAL_RESISTANCE
        );
        double groundWindow = positiveRelativeGain(
                initialAttackerGround,
                currentAttackerGround,
                initialDefenderGround,
                currentDefenderGround
        );
        double airWindow = positiveRelativeGain(
                initialAttackerAir,
                currentAttackerAir,
                initialDefenderAir,
                currentDefenderAir
        );
        double navalWindow = positiveRelativeGain(
                initialAttackerNaval,
                currentAttackerNaval,
                initialDefenderNaval,
                currentDefenderNaval
        );
        double infraPressure = initialDefenderInfra > 0d
                ? clamp01((initialDefenderInfra - Math.max(0d, currentDefenderInfra)) / initialDefenderInfra)
                : 0d;
        return resistancePressure + groundWindow + airWindow + navalWindow + infraPressure;
    }

    static double groundStrength(double soldiers, double tanks, boolean underAir) {
        return UnitEconomy.groundStrengthRaw(
                clampNonNegativeRound(soldiers),
                clampNonNegativeRound(tanks),
                true,
                underAir
        );
    }

    private static double expectedScoreLoss(MutableAttackResult result, boolean attackerSide) {
        double total = attackerSide ? 0d : result.infraDestroyed() * MilitaryUnit.INFRASTRUCTURE.getScore(1);
        for (MilitaryUnit unit : MilitaryUnit.values) {
            if (unit == MilitaryUnit.MONEY || unit == MilitaryUnit.INFRASTRUCTURE) {
                continue;
            }
            double losses = attackerSide
                    ? result.attackerLossesEv()[unit.ordinal()]
                    : result.defenderLossesEv()[unit.ordinal()];
            if (losses > 0d) {
                total += losses * unit.getScore(1);
            }
        }
        return total;
    }

    private static double positiveRelativeGain(
            double initialAttackerStrength,
            double currentAttackerStrength,
            double initialDefenderStrength,
            double currentDefenderStrength
    ) {
        double defenderLossFraction = lossFraction(initialDefenderStrength, currentDefenderStrength);
        double attackerLossFraction = lossFraction(initialAttackerStrength, currentAttackerStrength);
        return Math.max(0d, defenderLossFraction - attackerLossFraction);
    }

    private static double lossFraction(double initial, double current) {
        if (!(initial > 0d)) {
            return 0d;
        }
        return clamp01((initial - Math.max(0d, current)) / initial);
    }

    private static int clampNonNegativeRound(double value) {
        return Math.max(0, (int) Math.round(value));
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}