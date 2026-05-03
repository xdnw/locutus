package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.UnitEconomy;

final class OpeningMetricSummary {

    private OpeningMetricSummary() {
    }

    static double immediateHarm(MutableAttackResult result, int defenderResearchBits) {
        return expectedUnitValueLoss(result, false, defenderResearchBits);
    }

    static double selfExposure(MutableAttackResult result, int attackerResearchBits) {
        return expectedUnitValueLoss(result, true, attackerResearchBits);
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
        return resistancePressure + groundWindow + airWindow + navalWindow;
    }

    static double targetPressure(
            double attackerGround,
            double defenderGround,
            double attackerAir,
            double defenderAir,
            double attackerNaval,
            double defenderNaval
    ) {
        return defenderControlPressure(defenderGround, defenderAir, defenderNaval);
    }

    static double defenderControlPressure(
            double defenderGround,
            double defenderAir,
            double defenderNaval
    ) {
        // Prices the defender's contribution to enemy control without consulting attacker odds
        // or nation score. Stronger defenders are worth pressuring more regardless of which
        // attacker is asking.
        double defenderMilitary = defenderGround + (3d * defenderAir) + (2d * defenderNaval);
        if (!(defenderMilitary > 0d)) {
            return 0d;
        }
        double absoluteThreat = Math.min(2.5d, Math.log1p(defenderMilitary) / Math.log1p(250_000d));
        return 12d * absoluteThreat;
    }

    static double groundStrength(double soldiers, double tanks, boolean underAir) {
        return UnitEconomy.groundStrengthRaw(
                clampNonNegativeRound(soldiers),
                clampNonNegativeRound(tanks),
                true,
                underAir
        );
    }

    private static double expectedUnitValueLoss(MutableAttackResult result, boolean attackerSide, int researchBits) {
        double total = 0d;
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            double losses = attackerSide
                    ? result.attackerLossesEv()[unit.ordinal()]
                    : result.defenderLossesEv()[unit.ordinal()];
            if (losses > 0d) {
                total += StrategicAssetValue.unitValue(unit, losses, researchBits);
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
