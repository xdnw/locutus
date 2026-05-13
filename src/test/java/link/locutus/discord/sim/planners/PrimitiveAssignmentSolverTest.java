package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveAssignmentSolverTest {

    @Test
    void optionalFlowSkipsNonProfitableCandidate() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 0f, 0f);

        Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                new float[]{0f},
                1,
                1,
                new int[]{1},
                new int[]{1},
                new int[]{0},
                new int[]{101},
                new int[]{201},
                List.of(),
                new boolean[1],
                new int[1],
                new int[1]
        );

        assertTrue(assignment.isEmpty(), "Optional flow should leave supply unused when every remaining candidate has non-negative cost");
    }

    @Test
    void profitableResidualEdgesCanAugmentMultipleTimes() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(0, 0, 10f, 0f);
        edges.add(0, 1, 9f, 0f);
        edges.add(1, 0, 1f, 0f);

        boolean[] edgeAssigned = new boolean[3];
        int[] attackerAssignedCounts = new int[2];
        int[] defenderAssignedCounts = new int[2];

        Map<Integer, List<Integer>> assignment = PrimitiveAssignmentSolver.solveAssignment(
                edges,
                null,
                2,
                2,
                new int[]{2, 1},
                new int[]{1, 1},
                new int[]{0, 1},
                new int[]{101, 102},
                new int[]{201, 202},
                List.of(),
                edgeAssigned,
                attackerAssignedCounts,
                defenderAssignedCounts
        );

        assertEquals(Map.of(101, List.of(201, 202)), assignment);
        assertEquals(List.of(true, true, false), List.of(edgeAssigned[0], edgeAssigned[1], edgeAssigned[2]));
        assertEquals(List.of(2, 0), List.of(attackerAssignedCounts[0], attackerAssignedCounts[1]));
        assertEquals(List.of(1, 1), List.of(defenderAssignedCounts[0], defenderAssignedCounts[1]));
    }
}