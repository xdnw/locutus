package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.Arrays;
import java.util.Objects;

public record NationCombatProfile(
        int researchBits,
        boolean blitzkriegActive,
        double[] infraAttackModifiers,
        double[] infraDefendModifiers,
        double groundLooterModifier,
        double nonGroundLooterModifier,
        double lootModifier
) {
    private static final AttackType[] ATTACK_TYPES = AttackType.values;

    public NationCombatProfile {
        if (researchBits < 0) {
            throw new IllegalArgumentException("researchBits must be >= 0");
        }
        infraAttackModifiers = validateModifiers(infraAttackModifiers, "infraAttackModifiers");
        infraDefendModifiers = validateModifiers(infraDefendModifiers, "infraDefendModifiers");
        groundLooterModifier = validatePositiveFinite(groundLooterModifier, "groundLooterModifier");
        nonGroundLooterModifier = validatePositiveFinite(nonGroundLooterModifier, "nonGroundLooterModifier");
        lootModifier = validatePositiveFinite(lootModifier, "lootModifier");
    }

    public static NationCombatProfile derived(WarPolicy policy, long projectBits) {
        return derived(policy, projectBits, 0, policy == WarPolicy.BLITZKRIEG);
    }

    public static NationCombatProfile derived(
            WarPolicy policy,
            long projectBits,
            int researchBits,
            boolean blitzkriegActive
    ) {
        WarPolicy validatedPolicy = Objects.requireNonNull(policy, "policy");
        double lootModifier = switch (validatedPolicy) {
            case TURTLE, GUARDIAN -> 1.2;
            case MONEYBAGS -> 0.6;
            default -> 1.0;
        };

        double groundLooter = 1.0;
        double nonGroundLooter = 1.0;
        if (validatedPolicy == WarPolicy.PIRATE) {
            groundLooter += 0.4;
            nonGroundLooter += 0.4;
        } else if (validatedPolicy == WarPolicy.ATTRITION) {
            groundLooter -= 0.2;
            nonGroundLooter -= 0.2;
        }
        if (hasProject(projectBits, Projects.ADVANCED_PIRATE_ECONOMY)) {
            groundLooter += 0.05;
            nonGroundLooter += 0.1;
        }
        if (hasProject(projectBits, Projects.PIRATE_ECONOMY)) {
            groundLooter += 0.05;
        }

        double[] attack = filledModifiers(1.0);
        double[] defend = filledModifiers(1.0);
        for (AttackType type : ATTACK_TYPES) {
            if (!isGroundAirOrNaval(type)) {
                continue;
            }
            if (validatedPolicy == WarPolicy.ATTRITION || (validatedPolicy == WarPolicy.BLITZKRIEG && blitzkriegActive)) {
                attack[type.ordinal()] = 1.1;
            }
            defend[type.ordinal()] = switch (validatedPolicy) {
                case TURTLE -> 0.9;
                case COVERT, ARCANE -> 1.05;
                default -> defend[type.ordinal()];
            };
        }
        if (validatedPolicy == WarPolicy.MONEYBAGS) {
            Arrays.fill(defend, 1.05);
        }

        return new NationCombatProfile(
                researchBits,
                blitzkriegActive,
                attack,
                defend,
                groundLooter,
                nonGroundLooter,
                lootModifier
        );
    }

    public double infraAttackModifier(AttackType type) {
        return infraAttackModifiers[type.ordinal()];
    }

    @Override
    public double[] infraAttackModifiers() {
        return infraAttackModifiers.clone();
    }

    public double infraDefendModifier(AttackType type) {
        return infraDefendModifiers[type.ordinal()];
    }

    @Override
    public double[] infraDefendModifiers() {
        return infraDefendModifiers.clone();
    }

    public double looterModifier(boolean ground) {
        return ground ? groundLooterModifier : nonGroundLooterModifier;
    }

    private static double[] validateModifiers(double[] values, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != ATTACK_TYPES.length) {
            throw new IllegalArgumentException(name + " must match AttackType.values length");
        }
        double[] copy = values.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] = validatePositiveFinite(copy[i], name + "[" + i + "]");
        }
        return copy;
    }

    private static double validatePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
        return value;
    }

    private static boolean hasProject(long projectBits, Project project) {
        return (projectBits & (1L << project.ordinal())) != 0L;
    }

    private static double[] filledModifiers(double value) {
        double[] values = new double[ATTACK_TYPES.length];
        Arrays.fill(values, value);
        return values;
    }

    private static boolean isGroundAirOrNaval(AttackType type) {
        return switch (type) {
            case GROUND, AIRSTRIKE_INFRA, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, AIRSTRIKE_MONEY,
                    AIRSTRIKE_SHIP, AIRSTRIKE_AIRCRAFT, NAVAL, NAVAL_GROUND, NAVAL_AIR, NAVAL_INFRA -> true;
            default -> false;
        };
    }
}