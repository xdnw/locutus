package link.locutus.discord.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzObjectiveTest {
    @Test
    void defaultObjectivePreservesExistingNetDamageBehavior() {
        assertEquals(BlitzObjective.NET_DAMAGE, BlitzObjective.defaultObjective());

        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 0.0, 0.0, 0.0, 0.0);

        assertEquals(70.0, BlitzObjective.defaultObjective().objective().scoreOpening(metrics, 1), 1e-9);
    }

    @Test
    void objectiveScalarizersAreDistinctAndComponentBacked() {
        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 1_000_000.0, 5.0, 0.0, 8.0);

        assertEquals(100.0, BlitzObjective.DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(70.0, BlitzObjective.NET_DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(5.0, BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1), 1e-9);
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(metrics, 1) > BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1));
        assertTrue(BlitzObjective.BALANCED.objective().candidateEdgeComponentPolicy().retainsAny());
    }

    @Test
    void futureWarLeverageCompatibilityIgnoresRawResistanceDrain() {
        OpeningMetricVector momentumOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.9, 0.0);
        OpeningMetricVector forceWindowOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.9);

        assertEquals(0.0, momentumOnly.futureWarLeverage(), 1e-9);
        assertEquals(0.9, forceWindowOnly.futureWarLeverage(), 1e-9);
        assertTrue(
                BlitzObjective.CONTROL.objective().scoreOpening(forceWindowOnly, 1)
                        > BlitzObjective.CONTROL.objective().scoreOpening(momentumOnly, 1)
        );
        assertTrue(
                BlitzObjective.BALANCED.objective().scoreOpening(forceWindowOnly, 1)
                        > BlitzObjective.BALANCED.objective().scoreOpening(momentumOnly, 1)
        );
    }

    @Test
    void targetPressureIsControlAndBalancedOnly() {
        OpeningMetricVector lowPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 0.0, 3.0, 1.0);
        OpeningMetricVector highPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 0.0, 3.0, 12.0);

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
    void controlOwnershipScoreDoesNotDoubleCountResistanceDrain() {
        TeamWarControlView slowerDrain = new TeamWarControlView() {
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
                consumer.accept(1, 2, 1, 1, 2, 100, 95);
            }
        };

        TeamWarControlView fasterDrain = new TeamWarControlView() {
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
                consumer.accept(1, 2, 1, 1, 2, 100, 10);
            }
        };

        assertEquals(slowerDrain.controlScoreForTeam(1), fasterDrain.controlScoreForTeam(1), 1e-9);
        assertEquals(slowerDrain.controlScoreForTeam(2), fasterDrain.controlScoreForTeam(2), 1e-9);
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
                consumer.accept(1, 2, 12.0, 0.0, 3.0);
            }
        };

        assertEquals(57.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-57.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 2), 1e-9);
        assertEquals(15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 2), 1e-9);
    }

    @Test
    void terminalObjectivesIgnoreTacticalMomentumOnlyPressure() {
        TeamWarControlView lowMomentum = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                consumer.accept(1, 2, 0.0, 0.1, 0.0);
            }
        };

        TeamWarControlView highMomentum = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 100.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                consumer.accept(1, 2, 0.0, 0.9, 0.0);
            }
        };

        assertEquals(
                BlitzObjective.CONTROL.objective().scoreTerminal(lowMomentum, 1),
                BlitzObjective.CONTROL.objective().scoreTerminal(highMomentum, 1),
                1e-9
        );
        assertEquals(
                BlitzObjective.BALANCED.objective().scoreTerminal(lowMomentum, 1),
                BlitzObjective.BALANCED.objective().scoreTerminal(highMomentum, 1),
                1e-9
        );
        assertEquals(
                BlitzObjective.DAMAGE.objective().scoreTerminal(lowMomentum, 1),
                BlitzObjective.DAMAGE.objective().scoreTerminal(highMomentum, 1),
                1e-9
        );
    }

    @Test
    void terminalScoringPricesExplicitWarSlotDenial() {
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
            public void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
                consumer.accept(1, 2, 40.0, 150.0);
            }
        };

        assertEquals(110.0, view.activeWarSlotDenialScoreForTeam(1), 1e-9);
        assertEquals(-110.0, view.activeWarSlotDenialScoreForTeam(2), 1e-9);
        assertEquals(110.0, BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-110.0, BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 2), 1e-9);
        assertEquals(165.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 1), 1e-9);
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
    void controlRegimeScoreDoesNotRepeatGlobalStrategicEdgePerWar() {
        TeamWarControlView lowerGlobalEdge = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 110.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 2, 90, 45);
            }
        };

        TeamWarControlView higherGlobalEdge = new TeamWarControlView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 310.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 2, 90, 45);
            }
        };

        assertEquals(
                lowerGlobalEdge.controlRegimeScoreForTeam(1),
                higherGlobalEdge.controlRegimeScoreForTeam(1),
                1e-9
        );
        assertEquals(
                lowerGlobalEdge.controlRegimeScoreForTeam(2),
                higherGlobalEdge.controlRegimeScoreForTeam(2),
                1e-9
        );
    }

    @Test
    void terminalObjectivesUseStrategicValueNotNationScore() {
        StrategicValueView view = new StrategicValueView() {
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
