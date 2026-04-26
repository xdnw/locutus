package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.function.Predicate;

/**
 * Shared owner for projectile-defense effects that modify missile and nuke execution.
 */
public final class ProjectileDefenseMath {
    private static final double IRON_DOME_INTERCEPTION = 0.30d;
    private static final double VITAL_DEFENSE_SYSTEM_INTERCEPTION = 0.25d;

    private ProjectileDefenseMath() {
    }

    public static boolean isProjectileAttack(AttackType type) {
        return type == AttackType.MISSILE || type == AttackType.NUKE;
    }

    public static double interceptionChance(AttackType type, AttackType.CasualtyNationView defender) {
        return interceptionChance(type, defender::hasProject);
    }

    public static double interceptionChance(AttackType type, Predicate<Project> hasProject) {
        if (type == AttackType.MISSILE && hasProject.test(Projects.IRON_DOME)) {
            return IRON_DOME_INTERCEPTION;
        }
        if (type == AttackType.NUKE && hasProject.test(Projects.VITAL_DEFENSE_SYSTEM)) {
            return VITAL_DEFENSE_SYSTEM_INTERCEPTION;
        }
        return 0d;
    }

    public static int preventedImprovementLosses(AttackType type, AttackType.CasualtyNationView attacker, AttackType.CasualtyNationView defender) {
        if (!isProjectileAttack(type)) {
            return 0;
        }
        return Math.min(baseImprovementLosses(attacker), defenderProtection(type, defender));
    }

    public static int improvementLossesOnHit(AttackType type, AttackType.CasualtyNationView attacker, AttackType.CasualtyNationView defender) {
        if (!isProjectileAttack(type)) {
            return 0;
        }
        return Math.max(0, baseImprovementLosses(attacker) - defenderProtection(type, defender));
    }

    public static boolean isIntercepted(AttackType type, SuccessType success) {
        return isProjectileAttack(type) && success == SuccessType.UTTER_FAILURE;
    }

    private static int baseImprovementLosses(AttackType.CasualtyNationView attacker) {
        return attacker.hasProject(Projects.GUIDING_SATELLITE) ? 2 : 1;
    }

    private static int defenderProtection(AttackType type, AttackType.CasualtyNationView defender) {
        if (type == AttackType.MISSILE && defender.hasProject(Projects.IRON_DOME)) {
            return 1;
        }
        if (type == AttackType.NUKE && defender.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) {
            return 1;
        }
        return 0;
    }
}