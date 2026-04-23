package link.locutus.discord.sim;

/** Shared war-slot rules owned by the sim layer. */
public final class WarSlotRules {
    private static final int DEFENSIVE_SLOT_CAP = 3;

    private WarSlotRules() {
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