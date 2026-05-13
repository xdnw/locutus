package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;

final class OpeningRolloutMetricProjector {
    private OpeningRolloutMetricProjector() {
    }

    static void project(
            OpeningEvaluator.OpeningBaseline baseline,
            CombatKernel.AttackContext context,
            AttackType attackType,
            OpeningMetricVector currentMetrics,
            MutableAttackResult result,
            OpeningMetricVector.Mutable out
    ) {
        CombatKernel.NationState attacker = context.attacker();
        CombatKernel.NationState defender = context.defender();
        SuperiorityFlagDelta controlDelta = result.controlDelta();
        boolean attackerHadAirControl = context.attackerHasAirControl();
        boolean defenderHadAirControl = context.defenderHasAirControl();
        boolean attackerHadGroundSuperiority = context.attackerHasGroundSuperiority();
        boolean defenderHadGroundSuperiority = context.defenderHasGroundSuperiority();
        int blockadeOwner = context.blockadeOwner();
        double[] attackerLosses = result.attackerLossesEv();
        double[] defenderLosses = result.defenderLossesEv();
        boolean attackerHasGroundSuperiority = projectedAttackerHasGroundSuperiority(context, controlDelta);
        boolean attackerHasAirControl = projectedAttackerHasAirControl(context, controlDelta);
        boolean attackerHasBlockade = projectedAttackerHasBlockade(context, controlDelta);
        boolean defenderHasAirControl = projectedDefenderHasAirControl(context, controlDelta);
        double immediateHarm = currentMetrics.immediateHarm()
            + OpeningMetricSummary.immediateHarm(
                    baseline.defenderSnapshot(),
                    result,
                attackerHadAirControl,
                    attackerHasAirControl,
                defenderHadGroundSuperiority,
                defenderHadAirControl,
                blockadeOwner == CombatKernel.AttackContext.BLOCKADE_DEFENDER
            );
        double selfExposure = currentMetrics.selfExposure()
            + OpeningMetricSummary.selfExposure(
                    baseline.attackerSnapshot(),
                    result,
                defenderHadAirControl,
                    defenderHasAirControl,
                attackerHadGroundSuperiority,
                attackerHadAirControl,
                blockadeOwner == CombatKernel.AttackContext.BLOCKADE_ATTACKER
            );
        double controlLeverage = OpeningMetricSummary.controlLeverage(
                attackerHasGroundSuperiority,
                attackerHasAirControl,
                attackerHasBlockade
        );
        double tacticalMomentum = OpeningMetricSummary.tacticalMomentumScore(
                projectedDefenderResistance(context, result)
        );
        double forceWindowAdvantage = OpeningMetricSummary.forceWindowScore(
                baseline.attackerGround(),
                OpeningMetricSummary.groundStrength(
                remainingUnits(attacker, attackerLosses, MilitaryUnit.SOLDIER),
                remainingUnits(attacker, attackerLosses, MilitaryUnit.TANK),
                        defenderHasAirControl
                ),
                baseline.defenderGround(),
                OpeningMetricSummary.groundStrength(
                remainingUnits(defender, defenderLosses, MilitaryUnit.SOLDIER),
                remainingUnits(defender, defenderLosses, MilitaryUnit.TANK),
                        attackerHasAirControl
                ),
                baseline.attackerAir(),
            remainingUnits(attacker, attackerLosses, MilitaryUnit.AIRCRAFT),
                baseline.defenderAir(),
            remainingUnits(defender, defenderLosses, MilitaryUnit.AIRCRAFT),
                baseline.attackerNaval(),
            remainingUnits(attacker, attackerLosses, MilitaryUnit.SHIP),
                baseline.defenderNaval(),
            remainingUnits(defender, defenderLosses, MilitaryUnit.SHIP)
        );
            double timingWindowAdvantage = 0d;
            double resourceSwing = currentMetrics.resourceSwing()
                + AttackObjectiveComponentMapper.resourceSwingForObjective(
                    attackType,
                    result.loot()
                );
        out.set(
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                tacticalMomentum,
                forceWindowAdvantage,
                timingWindowAdvantage,
                baseline.targetPressure()
        );
    }

        private static boolean projectedAttackerHasGroundSuperiority(
            CombatKernel.AttackContext context,
            SuperiorityFlagDelta controlDelta
    ) {
        if (controlDelta == null) {
            return context.attackerHasGroundSuperiority();
        }
        if (controlDelta.groundSuperiority() == 0) {
            return context.attackerHasGroundSuperiority();
        }
        return controlDelta.groundSuperiority() > 0;
    }

    private static boolean projectedAttackerHasAirControl(
            CombatKernel.AttackContext context,
            SuperiorityFlagDelta controlDelta
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
            SuperiorityFlagDelta controlDelta
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
            SuperiorityFlagDelta controlDelta
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