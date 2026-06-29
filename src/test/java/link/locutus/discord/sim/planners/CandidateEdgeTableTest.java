package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CandidateEdgeTableTest {
    @Test
    void setScalarScoreSanitizesNaNToNegativeInfinity() {
        CandidateEdgeTable edges = oneEdge();

        edges.setScalarScore(0, Float.NaN);

        assertEquals(Float.NEGATIVE_INFINITY, edges.scalarScore(0));
    }

    @Test
    void scalingNegativeInfinityByZeroDoesNotProduceNan() {
        CandidateEdgeTable edges = oneEdge();
        edges.setScalarScore(0, Float.NEGATIVE_INFINITY);

        edges.scaleScalarScore(0, 0.0f);

        assertEquals(Float.NEGATIVE_INFINITY, edges.scalarScore(0));
        assertFalse(Float.isNaN(edges.scalarScore(0)));
    }

    private static CandidateEdgeTable oneEdge() {
        CandidateEdgeTable edges = new CandidateEdgeTable();
        edges.add(
                0,
                0,
                (byte) WarType.ORD.ordinal(),
                (byte) AttackType.GROUND.ordinal(),
                100.0f,
                0.0f
        );
        return edges;
    }
}