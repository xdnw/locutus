package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;

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

    static boolean isSpecialist(AttackType attackType) {
        return attackType == AttackType.MISSILE || attackType == AttackType.NUKE;
    }
}
