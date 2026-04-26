package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.function.Predicate;

/** Shared war-slot rules owned by the sim layer. */
public final class WarSlotRules {
    private static final int BASE_OFFENSIVE_SLOT_CAP = 5;
    private static final int DEFENSIVE_SLOT_CAP = 3;

    private WarSlotRules() {
    }

    public static int baseOffensiveSlotCap() {
        return BASE_OFFENSIVE_SLOT_CAP;
    }

    public static int offensiveSlotCap(long projectBits) {
        return offensiveSlotCap(project -> (projectBits & (1L << project.ordinal())) != 0L);
    }

    public static int offensiveSlotCap(Predicate<Project> hasProject) {
        int slots = baseOffensiveSlotCap();
        if (hasProject.test(Projects.PIRATE_ECONOMY)) {
            slots++;
        }
        if (hasProject.test(Projects.ADVANCED_PIRATE_ECONOMY)) {
            slots++;
        }
        return slots;
    }

    public static int defensiveSlotCap() {
        return DEFENSIVE_SLOT_CAP;
    }

    public static int freeDefensiveSlots(int defSlotsUsed) {
        return Math.max(0, defensiveSlotCap() - Math.max(0, defSlotsUsed));
    }

    public static int clampFreeDefensiveSlots(int freeSlots) {
        return Math.max(0, Math.min(freeSlots, defensiveSlotCap()));
    }
}