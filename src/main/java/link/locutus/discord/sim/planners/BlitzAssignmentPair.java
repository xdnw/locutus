package link.locutus.discord.sim.planners;

import java.util.List;
import java.util.Map;

/**
 * Result owner for symmetric, role-swapped blitz assignment.
 */
public record BlitzAssignmentPair(
        BlitzAssignment sideAAssignment,
        BlitzAssignment sideBAssignment,
        Map<String, List<PlannerDiagnostic>> diagnosticsByPass
) {
    public static final String SIDE_A_PASS = "sideA";
    public static final String SIDE_B_PASS = "sideB";

    public BlitzAssignmentPair {
        if (sideAAssignment == null) {
            throw new IllegalArgumentException("sideAAssignment must not be null");
        }
        if (sideBAssignment == null) {
            throw new IllegalArgumentException("sideBAssignment must not be null");
        }
        diagnosticsByPass = diagnosticsByPass == null ? Map.of() : Map.copyOf(diagnosticsByPass);
    }
}