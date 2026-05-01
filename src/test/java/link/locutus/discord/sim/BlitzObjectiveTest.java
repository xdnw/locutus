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
    void targetPressureIsControlAndBalancedOnly() {
        OpeningMetricVector lowPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 3.0, 1.0);
        OpeningMetricVector highPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 3.0, 12.0);

        assertEquals(
                BlitzObjective.NET_DAMAGE.objective().scoreOpening(lowPressure, 1),
                BlitzObjective.NET_DAMAGE.objective().scoreOpening(highPressure, 1),
                1e-9
        );
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(highPressure, 1)
                > BlitzObjective.CONTROL.objective().scoreOpening(lowPressure, 1));
        assertTrue(BlitzObjective.BALANCED.objective().scoreOpening(highPressure, 1)
                > BlitzObjective.BALANCED.objective().scoreOpening(lowPressure, 1));
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
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 2, 100, 70);
            }
        };

        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 1) > 0.0);
        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 2) < 0.0);
    }

    @Test
    void controlAndBalancedTerminalScoringRetainActiveWarStrategicPressure() {
        TeamWarControlView view = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                consumer.accept(1, 2, 12.0, 3.0);
            }
        };

        assertEquals(57.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-57.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 2), 1e-9);
        assertEquals(15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 2), 1e-9);
    }

    @Test
    void controlRegimeScoreRewardsTenableWarsAndPenalizesLostControlStates() {
        TeamWarControlView favorable = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 2, 90, 45);
            }
        };

        TeamWarControlView lost = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 2, 2, 2, 25, 70);
            }
        };

        assertTrue(favorable.controlRegimeScoreForTeam(1) > 0.0);
        assertTrue(favorable.controlRegimeScoreForTeam(2) < 0.0);
        assertTrue(lost.controlRegimeScoreForTeam(1) < 0.0);
        assertTrue(lost.controlRegimeScoreForTeam(2) > 0.0);
        assertTrue(
                BlitzObjective.CONTROL.objective().scoreTerminal(favorable, 1)
                        > BlitzObjective.CONTROL.objective().scoreTerminal(lost, 1),
                "Control objective should prefer wars where current control and resistance state make future leverage tenable"
        );
        assertTrue(
                BlitzObjective.BALANCED.objective().scoreTerminal(favorable, 1)
                        > BlitzObjective.BALANCED.objective().scoreTerminal(lost, 1),
                "Balanced objective should down-rank lost-control wars instead of pricing them like stable leverage"
        );
    }

    @Test
    void terminalObjectivesUseStrategicValueNotNationScore() {
        TeamScoreView view = new TeamScoreView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 50_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 10.0);
                consumer.accept(202, 2, 40.0);
            }
        };

        assertEquals(-30.0, BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-40.0, BlitzObjective.DAMAGE.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-4.0, BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreTerminal(view, 1), 1e-9);
    }
}
