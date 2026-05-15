package link.locutus.discord.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzObjectiveTest {
    @Test
    void defaultObjectivePreservesExistingNetDamageBehavior() {
        assertEquals(BlitzObjective.NET_DAMAGE, BlitzObjective.defaultObjective());

        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        assertEquals(70.0, BlitzObjective.defaultObjective().objective().scoreOpening(metrics, 1), 1e-9);
    }

    @Test
    void objectiveScalarizersAreDistinctAndComponentBacked() {
        OpeningMetricVector metrics = new OpeningMetricVector(100.0, 30.0, 1_000_000.0, 5.0, 0.0, 8.0, 0.0, 0.0, 0.0);

        assertEquals(100.0, BlitzObjective.DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(70.0, BlitzObjective.NET_DAMAGE.objective().scoreOpening(metrics, 1), 1e-9);
        assertEquals(5.0, BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1), 1e-9);
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(metrics, 1) > BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().scoreOpening(metrics, 1));
        assertTrue(BlitzObjective.BALANCED.objective().candidateEdgeComponentPolicy().retainsAny());
    }

    @Test
    void futureWarLeverageCompatibilityIgnoresRawResistanceDrain() {
        OpeningMetricVector momentumOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0, 0.0);
        OpeningMetricVector actionSpaceQualityOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.9, 0.0, 0.0, 0.0);

        assertEquals(0.0, momentumOnly.futureWarLeverage(), 1e-9);
        assertEquals(0.9, actionSpaceQualityOnly.futureWarLeverage(), 1e-9);
        assertTrue(
                BlitzObjective.CONTROL.objective().scoreOpening(actionSpaceQualityOnly, 1)
                        > BlitzObjective.CONTROL.objective().scoreOpening(momentumOnly, 1)
        );
        assertTrue(
                BlitzObjective.BALANCED.objective().scoreOpening(actionSpaceQualityOnly, 1)
                        > BlitzObjective.BALANCED.objective().scoreOpening(momentumOnly, 1)
        );
    }

    @Test
    void targetPressureIsControlAndBalancedOnly() {
        OpeningMetricVector lowPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 0.0, 3.0, 0.0, 0.0, 1.0);
        OpeningMetricVector highPressure = new OpeningMetricVector(100.0, 30.0, 0.0, 2.0, 0.0, 3.0, 0.0, 0.0, 12.0);

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
    void controlOpeningDoesNotRewardPressureWithoutActionableLeverage() {
        OpeningMetricVector pressureOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 12.0);
        OpeningMetricVector pressureWithControl = new OpeningMetricVector(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 12.0);

        assertEquals(0.0, BlitzObjective.CONTROL.objective().scoreOpening(pressureOnly, 1), 1e-9);
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(pressureWithControl, 1)
                > BlitzObjective.CONTROL.objective().scoreOpening(pressureOnly, 1));
    }

    @Test
    void controlOpeningTreatsDeclarationReadinessAsWeakerThanRealLeverage() {
        OpeningMetricVector readinessOnly = new OpeningMetricVector(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                1.0,
                12.0
        );
        OpeningMetricVector realLeverage = new OpeningMetricVector(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 12.0);

        double readinessScore = BlitzObjective.CONTROL.objective().scoreOpening(readinessOnly, 1);
        double leverageScore = BlitzObjective.CONTROL.objective().scoreOpening(realLeverage, 1);

        assertTrue(readinessScore > 0d);
        assertTrue(leverageScore > readinessScore);
    }

    @Test
    void controlOpeningDoesNotTreatTacticalPostureAsControlByItself() {
        OpeningMetricVector tacticalPostureOnly = new OpeningMetricVector(
            0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0, 0.0, 0.0
        );
        OpeningMetricVector durableWindow = new OpeningMetricVector(
            0.0, 0.0, 0.0, 0.0, 0.0, 3.0, 0.0, 0.0, 0.0
        );

        assertEquals(0.0, BlitzObjective.CONTROL.objective().scoreOpening(tacticalPostureOnly, 1), 1e-9);
        assertTrue(BlitzObjective.CONTROL.objective().scoreOpening(durableWindow, 1)
                > BlitzObjective.CONTROL.objective().scoreOpening(tacticalPostureOnly, 1));
    }

    @Test
    void controlOpeningCapturesTargetPressureOnlyThroughFollowThroughProgress() {
        OpeningMetricVector hugeTargetTinyProgress = new OpeningMetricVector(
            0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1_000.0
        );
        OpeningMetricVector hugeTargetDurableProgress = new OpeningMetricVector(
            0.0, 0.0, 0.0, 5.0, 0.0, 8.0, 0.0, 0.0, 1_000.0
        );

        double tinyProgressScore = BlitzObjective.CONTROL.objective().scoreOpening(hugeTargetTinyProgress, 1);
        double durableProgressScore = BlitzObjective.CONTROL.objective().scoreOpening(hugeTargetDurableProgress, 1);

        assertTrue(tinyProgressScore < 100.0,
                "A small control nudge must not unlock a huge target's whole pressure value");
        assertTrue(durableProgressScore > tinyProgressScore * 5.0,
                "Target-pressure value should scale with durable follow-through progress");
    }

    @Test
    void controlOpeningDoesNotCaptureTargetPressureThroughLosingTrades() {
        OpeningMetricVector costlyProgress = new OpeningMetricVector(
            0.0, 500.0, 0.0, 5.0, 0.0, 8.0, 0.0, 0.0, 1_000.0
        );
        OpeningMetricVector cleanerProgress = new OpeningMetricVector(
            0.0, 0.0, 0.0, 5.0, 0.0, 8.0, 0.0, 0.0, 1_000.0
        );

        double costlyScore = BlitzObjective.CONTROL.objective().scoreOpening(costlyProgress, 1);
        double cleanerScore = BlitzObjective.CONTROL.objective().scoreOpening(cleanerProgress, 1);

        assertTrue(costlyScore < cleanerScore * 0.25,
                "Casualty-heavy progress should not capture the same target-pressure value as clean progress");
    }

    @Test
    void balancedOpeningDoesNotRewardPressureWithoutActionableLeverage() {
        OpeningMetricVector pressureOnly = new OpeningMetricVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 12.0);

        assertEquals(0.0, BlitzObjective.BALANCED.objective().scoreOpening(pressureOnly, 1), 1e-9);
    }

    @Test
    void controlTerminalUsesProjectedStrategicTotals() {
        StrategicValueView view = new StrategicValueView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 140.0);
                consumer.accept(202, 2, 100.0);
            }
        };

        assertEquals(40.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-40.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 2), 1e-9);
        assertEquals(
                BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 1),
                BlitzObjective.CONTROL.objective().scoreTerminal(view, 1),
                1e-9
        );
    }

    @Test
    void controlTerminalComparisonSubtractsOpponentTotals() {
        StrategicValueView view = new StrategicValueView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 140.0);
                consumer.accept(202, 2, 100.0);
            }
        };

        StrategicObjective objective = BlitzObjective.CONTROL.objective();
        assertEquals(
                objective.scoreTerminal(view, 1) - objective.scoreTerminal(view, 2),
                objective.scoreTerminalComparison(view, 1, 2),
                1e-9
        );
    }

    @Test
    void terminalScoringIgnoresProjectionDiagnosticsOnceTotalsMatch() {
        TeamProjectionView lowerDiagnostics = new TeamProjectionView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 140.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 0, 0, 100, 95);
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                consumer.accept(1, 2, 1.0, 0.1, 0.0);
            }

            @Override
            public void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
                consumer.accept(1, 2, 10.0, 20.0);
            }
        };

        TeamProjectionView higherDiagnostics = new TeamProjectionView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, 140.0);
                consumer.accept(202, 2, 100.0);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                consumer.accept(1, 2, 1, 1, 1, 100, 5);
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                consumer.accept(1, 2, 20.0, 0.9, 4.0);
            }

            @Override
            public void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
                consumer.accept(1, 2, 10.0, 20.0);
            }
        };

        assertEquals(
                BlitzObjective.CONTROL.objective().scoreTerminal(lowerDiagnostics, 1),
                BlitzObjective.CONTROL.objective().scoreTerminal(higherDiagnostics, 1),
                1e-9
        );
        assertEquals(
                BlitzObjective.BALANCED.objective().scoreTerminal(lowerDiagnostics, 1),
                BlitzObjective.BALANCED.objective().scoreTerminal(higherDiagnostics, 1),
                1e-9
        );
        assertEquals(
                BlitzObjective.DAMAGE.objective().scoreTerminal(lowerDiagnostics, 1),
                BlitzObjective.DAMAGE.objective().scoreTerminal(higherDiagnostics, 1),
                1e-9
        );
    }

    @Test
    void terminalScoringStillPricesProjectedSlotOccupancy() {
        TeamProjectionView view = new TeamProjectionView() {
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

        assertEquals(110.0, BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-110.0, BlitzObjective.NET_DAMAGE.objective().scoreTerminal(view, 2), 1e-9);
        assertEquals(165.0, BlitzObjective.CONTROL.objective().scoreTerminal(view, 1), 1e-9);
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
