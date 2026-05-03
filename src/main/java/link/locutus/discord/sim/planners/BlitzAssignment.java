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
    private final ObjectiveValueSummary objectiveSummary;

    /** Pair-keyed first attack-type ordinal selected by the opening evaluator. */
    private final Map<Long, Integer> initialAttackTypeOrdinalsByPair;

    /** Pair-keyed war-type ordinal selected by the opening evaluator. */
    private final Map<Long, Integer> initialWarTypeOrdinalsByPair;

    public BlitzAssignment(Map<Integer, List<Integer>> assignment, List<PlannerDiagnostic> diagnostics, double objectiveScore) {
        this(assignment, diagnostics, objectiveScore, ObjectiveValueSummary.identical(objectiveScore));
    }

    public BlitzAssignment(
            Map<Integer, List<Integer>> assignment,
            List<PlannerDiagnostic> diagnostics,
            double objectiveScore,
            ObjectiveValueSummary objectiveSummary
    ) {
        this(assignment, diagnostics, objectiveScore, objectiveSummary, Map.of());
    }

    public BlitzAssignment(
            Map<Integer, List<Integer>> assignment,
            List<PlannerDiagnostic> diagnostics,
            double objectiveScore,
            ObjectiveValueSummary objectiveSummary,
            Map<Long, Integer> initialAttackTypeOrdinalsByPair
    ) {
        this(assignment, diagnostics, objectiveScore, objectiveSummary, Map.of(), initialAttackTypeOrdinalsByPair);
    }

    public BlitzAssignment(
            Map<Integer, List<Integer>> assignment,
            List<PlannerDiagnostic> diagnostics,
            double objectiveScore,
            ObjectiveValueSummary objectiveSummary,
            Map<Long, Integer> initialWarTypeOrdinalsByPair,
            Map<Long, Integer> initialAttackTypeOrdinalsByPair
    ) {
        this.assignment = Collections.unmodifiableMap(assignment);
        this.diagnostics = Collections.unmodifiableList(diagnostics);
        this.objectiveScore = objectiveScore;
        this.objectiveSummary = objectiveSummary == null ? ObjectiveValueSummary.identical(objectiveScore) : objectiveSummary;
        this.initialWarTypeOrdinalsByPair = Collections.unmodifiableMap(initialWarTypeOrdinalsByPair == null ? Map.of() : initialWarTypeOrdinalsByPair);
        this.initialAttackTypeOrdinalsByPair = Collections.unmodifiableMap(initialAttackTypeOrdinalsByPair == null ? Map.of() : initialAttackTypeOrdinalsByPair);
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
    public ObjectiveValueSummary objectiveSummary() {
        return objectiveSummary;
    }

    /**
     * Returns the opening attack-type ordinal selected for a planned pair, or {@code -1}
     * when the pair was fixed/manual and no candidate-edge metadata was available.
     */
    public int initialAttackTypeOrdinal(int attackerId, int defenderId) {
        return initialAttackTypeOrdinalsByPair.getOrDefault(pairKey(attackerId, defenderId), -1);
    }

    /**
     * Returns the war-type ordinal selected for a planned pair, defaulting to ordinary war when
     * the pair was fixed/manual or lacks candidate-edge metadata.
     */
    public int initialWarTypeOrdinal(int attackerId, int defenderId) {
        return initialWarTypeOrdinalsByPair.getOrDefault(pairKey(attackerId, defenderId), link.locutus.discord.apiv1.enums.WarType.ORD.ordinal());
    }

    public Map<Long, Integer> initialWarTypeOrdinalsByPair() {
        return initialWarTypeOrdinalsByPair;
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

    private static long pairKey(int attackerNationId, int defenderNationId) {
        return ((long) attackerNationId << 32) | (defenderNationId & 0xFFFFFFFFL);
    }
}
