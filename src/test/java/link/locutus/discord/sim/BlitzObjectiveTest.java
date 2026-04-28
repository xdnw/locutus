package link.locutus.discord.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzObjectiveTest {
    @Test
    void defaultObjectivePreservesExistingNetDamageBehavior() {
        assertEquals(BlitzObjective.NET_DAMAGE, BlitzObjective.defaultObjective());

        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 0.0, 0.0, 0.0);

        assertEquals(70.0, BlitzObjective.defaultObjective().objective().scoreOpening(metrics, 1), 1e-9);
    }

    @Test
    void objectiveScalarizersAreDistinctAndComponentBacked() {
        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 1_000_000.0, 5.0, 8.0);

        assertEquals(100.0, BlitzObjective.DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(70.0, BlitzObjective.NET_DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(5.0, BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1), 1e-9);
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(metrics, 1) > BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1));
        assertTrue(BlitzObjective.BALANCED.objective().candidateEdgeComponentPolicy().retainsAny());
    }

    @Test
    void controlTerminalScoringReadsPlannerWarControlViewWhenAvailable() {
        TeamWarControlView view = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 2, 100, 70);
            }
        };

        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 1) > 0.0);
        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 2) < 0.0);
    }
}