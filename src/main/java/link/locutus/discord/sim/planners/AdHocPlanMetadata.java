package link.locutus.discord.sim.planners;

/**
 * Structured ad hoc planning metadata that used to be encoded in free-form notes.
 */
public record AdHocPlanMetadata(
    boolean exactValidationDefault,
        boolean runtimePreviewApplied
) {
    public static final AdHocPlanMetadata DEFAULT = new AdHocPlanMetadata(true, false);
}