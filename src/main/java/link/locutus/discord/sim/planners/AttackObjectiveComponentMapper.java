package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

final class AttackObjectiveComponentMapper {
    private AttackObjectiveComponentMapper() {
    }

    static double resourceSwingForObjective(
            AttackType attackType,
            double rawResourceSwing
    ) {
        if (!(rawResourceSwing > 0d)) {
            return 0d;
        }
        if (isSpecialist(attackType)) {
            return rawResourceSwing;
        }
        return 0d;
    }

    static double controlLeverage(SuperiorityFlagDelta controlDelta) {
        SuperiorityFlagDelta delta = controlDelta == null ? SuperiorityFlagDelta.NONE : controlDelta;
        double leverage = 0d;
        leverage += positiveFlag(delta.groundSuperiority());
        leverage += positiveFlag(delta.airSuperiority());
        leverage += positiveFlag(delta.blockade());
        leverage += delta.clearGroundSuperiority() ? 1d : 0d;
        leverage += delta.clearAirSuperiority() ? 1d : 0d;
        leverage += delta.clearBlockade() ? 1d : 0d;
        return leverage;
    }

    static boolean isSpecialist(AttackType attackType) {
        return attackType == AttackType.MISSILE || attackType == AttackType.NUKE;
    }

    private static double positiveFlag(int value) {
        return value > 0 ? 1d : 0d;
    }
}