package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.ControlFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;

final class OpeningRolloutMetricProjector {
    private OpeningRolloutMetricProjector() {
    }

    static void project(
            OpeningEvaluator.OpeningBaseline baseline,
            CombatKernel.AttackContext context,
            OpeningMetricVector currentMetrics,
            MutableAttackResult result,
            OpeningMetricVector.Mutable out
    ) {
        ControlFlagDelta controlDelta = result.controlDelta();
        boolean attackerHasGroundControl = projectedAttackerHasGroundControl(context, controlDelta);
        boolean attackerHasAirControl = projectedAttackerHasAirControl(context, controlDelta);
        boolean attackerHasBlockade = projectedAttackerHasBlockade(context, controlDelta);
        boolean defenderHasAirControl = projectedDefenderHasAirControl(context, controlDelta);
        double immediateHarm = currentMetrics.immediateHarm()
            + OpeningMetricSummary.immediateHarm(result, context.defender().researchBits());
        double selfExposure = currentMetrics.selfExposure()
            + OpeningMetricSummary.selfExposure(result, context.attacker().researchBits());
        double resourceSwing = currentMetrics.resourceSwing() + result.loot();
        double controlLeverage = OpeningMetricSummary.controlLeverage(
                attackerHasGroundControl,
                attackerHasAirControl,
                attackerHasBlockade
        );
        double futureWarLeverage = OpeningMetricSummary.futureWarLeverage(
                baseline.attackerGround(),
                OpeningMetricSummary.groundStrength(
                        remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.SOLDIER),
                        remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.TANK),
                        defenderHasAirControl
                ),
                baseline.defenderGround(),
                OpeningMetricSummary.groundStrength(
                        remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.SOLDIER),
                        remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.TANK),
                        attackerHasAirControl
                ),
                baseline.attackerAir(),
                remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.AIRCRAFT),
                baseline.defenderAir(),
                remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.AIRCRAFT),
                baseline.attackerNaval(),
                remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.SHIP),
                baseline.defenderNaval(),
                remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.SHIP),
                projectedDefenderResistance(context, result)
        );
        out.set(
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                futureWarLeverage,
                baseline.targetPressure()
        );
    }

    private static boolean projectedAttackerHasGroundControl(
            CombatKernel.AttackContext context,
            ControlFlagDelta controlDelta
    ) {
        if (controlDelta == null) {
            return context.attackerHasGroundControl();
        }
        if (controlDelta.groundControl() == 0) {
            return context.attackerHasGroundControl();
        }
        return controlDelta.groundControl() > 0;
    }

    private static boolean projectedAttackerHasAirControl(
            CombatKernel.AttackContext context,
            ControlFlagDelta controlDelta
    ) {
        if (controlDelta == null) {
            return context.attackerHasAirControl();
        }
        if (controlDelta.airSuperiority() == 0) {
            return context.attackerHasAirControl();
        }
        return controlDelta.airSuperiority() > 0;
    }

    private static boolean projectedDefenderHasAirControl(
            CombatKernel.AttackContext context,
            ControlFlagDelta controlDelta
    ) {
        if (controlDelta == null) {
            return context.defenderHasAirControl();
        }
        if (controlDelta.airSuperiority() == 0) {
            return controlDelta.clearAirSuperiority() ? false : context.defenderHasAirControl();
        }
        return controlDelta.airSuperiority() < 0;
    }

    private static boolean projectedAttackerHasBlockade(
            CombatKernel.AttackContext context,
            ControlFlagDelta controlDelta
    ) {
        if (controlDelta == null) {
            return context.blockadeOwner() == CombatKernel.AttackContext.BLOCKADE_ATTACKER;
        }
        if (controlDelta.blockade() == 0) {
            return controlDelta.clearBlockade()
                    ? false
                    : context.blockadeOwner() == CombatKernel.AttackContext.BLOCKADE_ATTACKER;
        }
        return controlDelta.blockade() > 0;
    }

    private static int projectedDefenderResistance(
            CombatKernel.AttackContext context,
            MutableAttackResult result
    ) {
        return clampResistance(context.defenderResistance() + result.defenderResistanceDelta());
    }

    private static double remainingUnits(
            CombatKernel.NationState nation,
            double[] losses,
            MilitaryUnit unit
    ) {
        return Math.max(0d, nation.getUnits(unit) - losses[unit.ordinal()]);
    }

    private static int clampResistance(double value) {
        return Math.max(0, Math.min(SimWar.INITIAL_RESISTANCE, (int) Math.round(value)));
    }
}