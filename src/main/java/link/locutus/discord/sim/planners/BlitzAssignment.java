package link.locutus.discord.sim.planners;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Output DTO from {@link BlitzPlanner}: the recommended assignment of attackers → defenders plus
 * structured planner diagnostics.
 *
 * <p>The assignment map is {@code attackerId → List<defenderId>}.
 * Each entry is a war declaration the attacker should make.
 * Defenders not in the map's values received no attacker assignment for this blitz.</p>
 */
public final class BlitzAssignment {

    /** attackerId → ordered list of defenderId(s). */
    private final Map<Integer, List<Integer>> assignment;

    /** Structured planner diagnostics emitted during assignment. */
    private final List<PlannerDiagnostic> diagnostics;

    /** Total objective value of this assignment (as evaluated by BlitzPlanner). */
    private final double objectiveScore;

    /** Mean + percentile summary for the assignment score across sampled runs. */
    private final ScoreSummary objectiveSummary;

    public BlitzAssignment(Map<Integer, List<Integer>> assignment, List<PlannerDiagnostic> diagnostics, double objectiveScore) {
        this(assignment, diagnostics, objectiveScore, ScoreSummary.identical(objectiveScore));
    }

    public BlitzAssignment(
            Map<Integer, List<Integer>> assignment,
            List<PlannerDiagnostic> diagnostics,
            double objectiveScore,
            ScoreSummary objectiveSummary
    ) {
        this.assignment = Collections.unmodifiableMap(assignment);
        this.diagnostics = Collections.unmodifiableList(diagnostics);
        this.objectiveScore = objectiveScore;
        this.objectiveSummary = objectiveSummary == null ? ScoreSummary.identical(objectiveScore) : objectiveSummary;
    }

    /** Returns the assignment map: {@code attackerId → list of defenderIds}. */
    public Map<Integer, List<Integer>> assignment() {
        return assignment;
    }

    /** Returns structured diagnostics generated during assignment. */
    public List<PlannerDiagnostic> diagnostics() {
        return diagnostics;
    }

    /** Total objective score of this assignment. */
    public double objectiveScore() {
        return objectiveScore;
    }

    /** Summary bands for the assignment score across sampled runs. */
    public ScoreSummary objectiveSummary() {
        return objectiveSummary;
    }

    /**
     * Returns the list of defenders assigned to {@code attackerId}, or an empty list if unassigned.
     */
    public List<Integer> targetsFor(int attackerId) {
        return assignment.getOrDefault(attackerId, List.of());
    }

    /** Total number of (attacker, defender) pairs in the assignment. */
    public int pairCount() {
        return assignment.values().stream().mapToInt(List::size).sum();
    }
}
