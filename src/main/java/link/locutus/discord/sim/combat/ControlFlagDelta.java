package link.locutus.discord.sim.combat;

/**
 * Transitions in {@code {groundControl, airSuperiority, blockade}} ownership produced by a
 * single attack resolution from the current attacker's perspective.
 *
 * <p>Owner fields encode the post-attack owner side:
 * {@code 0} = unchanged, {@code +1} = attacker gained/holds, {@code -1} = defender gained/holds.
 * Clear flags express successful strip-to-none behavior for defender-held control in the current war.
 * Acquisition takes precedence over a matching clear when both are present.</p>
 */
public record ControlFlagDelta(
        int groundControl,
        int airSuperiority,
        int blockade,
        boolean clearGroundControl,
        boolean clearAirSuperiority,
        boolean clearBlockade
) {
    public static final ControlFlagDelta NONE = new ControlFlagDelta(0, 0, 0, false, false, false);

    public ControlFlagDelta {
        validateDelta(groundControl, "groundControl");
        validateDelta(airSuperiority, "airSuperiority");
        validateDelta(blockade, "blockade");
    }

    public static ControlFlagDelta of(
            int groundControl,
            int airSuperiority,
            int blockade,
            boolean clearGroundControl,
            boolean clearAirSuperiority,
            boolean clearBlockade
    ) {
        if (groundControl == 0
                && airSuperiority == 0
                && blockade == 0
                && !clearGroundControl
                && !clearAirSuperiority
                && !clearBlockade) {
            return NONE;
        }
        return new ControlFlagDelta(
                groundControl,
                airSuperiority,
                blockade,
                clearGroundControl,
                clearAirSuperiority,
                clearBlockade
        );
    }

    private static void validateDelta(int value, String field) {
        if (value < -1 || value > 1) {
            throw new IllegalArgumentException(field + " must be in [-1, 1]");
        }
    }
}
