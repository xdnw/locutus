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
    void controlLaterDeclarationDoesNotLetReadinessOnlyTurnPositive() {
    StrategicObjective objective = BlitzObjective.CONTROL.objective();

    double readinessOnlyScore = objective.scoreLaterDeclaration(
            new LaterDeclarationEvaluationFixture(
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            12.0,
            100.0,
            100.0,
            0.0,
            1,
            1,
            1.0,
            1.0,
            0.0,
            1.0
            ),
        1
    );

    assertEquals(0.0, readinessOnlyScore, 1e-9);
    }

    @Test
    void controlLaterDeclarationUsesSupportInObjectiveSeam() {
    StrategicObjective objective = BlitzObjective.CONTROL.objective();
    StrategicObjective.LaterDeclarationEvaluation unsupported = new LaterDeclarationEvaluationFixture(
            0.0,
            0.0,
            0.0,
            0.75,
            0.0,
            0.40,
            100.0,
            90.0,
            100.0,
            0.0,
            1,
            2,
            0.65,
            0.65,
            0.0,
            1.0
    );
    StrategicObjective.LaterDeclarationEvaluation supported = new LaterDeclarationEvaluationFixture(
            0.0,
            0.0,
            0.0,
            0.75,
            0.0,
            0.40,
            100.0,
            90.0,
            100.0,
            0.0,
            1,
            2,
            0.65,
            0.65,
            0.85,
            1.0
    );

    assertTrue(objective.scoreLaterDeclaration(supported, 1) > objective.scoreLaterDeclaration(unsupported, 1));
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
    void controlHoldabilityRequiresBackingUnitsForControlFlags() {
        assertEquals(
                0,
                ControlHoldability.backedControlCount(
                        1,
                        1,
                        1,
                        1,
                        unit -> 0
                )
        );
        assertEquals(
                3,
                ControlHoldability.backedControlCount(
                        1,
                        1,
                        1,
                        1,
                        unit -> switch (unit) {
                            case SOLDIER -> 1;
                            case AIRCRAFT -> 1;
                            case SHIP -> 1;
                            default -> 0;
                        }
                )
        );
    }

    @Test
    void explicitDurableControlMetricOverridesRawFlagGuessing() {
        TeamWarControlView rawFlagsOnly = new TeamWarControlView() {
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
                consumer.accept(1, 2, 1, 1, 1, 100, 40);
            }
        };

        TeamWarControlView explicitUnbackedFlags = new TeamWarControlView() {
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
                consumer.accept(1, 2, 1, 1, 1, 100, 40);
            }

            @Override
            public void forEachDurableWarControlMetric(DurableWarControlMetricConsumer consumer) {
                consumer.accept(1, 2, 0.0, 0.0);
            }
        };

        assertTrue(StrategicControlReducer.reduce(rawFlagsOnly, 1).durableControl() > 0.0);
        assertEquals(0.0, StrategicControlReducer.reduce(explicitUnbackedFlags, 1).durableControl(), 1e-9);
        assertEquals(0.0, BlitzObjective.CONTROL.objective().scoreTerminal(explicitUnbackedFlags, 1), 1e-9);
    }

    private record LaterDeclarationEvaluationFixture(
            double immediateHarm,
            double selfExposure,
            double resourceSwing,
            double controlLeverage,
            double declarationReadiness,
            double futureWarLeverage,
            double targetPressure,
            double declarerStrength,
            double targetStrength,
            double declarerRebuildStrengthGain,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double slotActionability,
            double targetBestActionability,
            double targetSupportActionability,
            double activityWeight
    ) implements StrategicObjective.LaterDeclarationEvaluation {
    }

    @Test
    void controlTerminalComparisonSubtractsOpponentControl() {
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

        StrategicObjective objective = BlitzObjective.CONTROL.objective();
        assertEquals(
                objective.scoreTerminal(view, 1) - objective.scoreTerminal(view, 2),
                objective.scoreTerminalComparison(view, 1, 2),
                1e-9
        );
    }

    @Test
    void tacticalPostureScoreDoesNotDoubleCountResistanceDrain() {
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

        assertEquals(
            StrategicControlReducer.reduce(slowerDrain, 1).tacticalPosture(),
            StrategicControlReducer.reduce(fasterDrain, 1).tacticalPosture(),
            1e-9
        );
        assertEquals(
            StrategicControlReducer.reduce(slowerDrain, 2).tacticalPosture(),
            StrategicControlReducer.reduce(fasterDrain, 2).tacticalPosture(),
            1e-9
        );
    }

    @Test
    void controlTerminalDoesNotTreatTacticalPostureAsDurableControl() {
        TeamWarControlView groundForAirAgainst = new TeamWarControlView() {
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
                consumer.accept(1, 2, 1, 2, 0, 100, 100);
            }
        };

        TeamWarControlView airForGroundAgainst = new TeamWarControlView() {
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
                consumer.accept(1, 2, 2, 1, 0, 100, 100);
            }
        };

        assertTrue(StrategicControlReducer.reduce(groundForAirAgainst, 1).tacticalPosture()
                != StrategicControlReducer.reduce(airForGroundAgainst, 1).tacticalPosture());
        assertEquals(
                BlitzObjective.CONTROL.objective().scoreTerminal(groundForAirAgainst, 1),
                BlitzObjective.CONTROL.objective().scoreTerminal(airForGroundAgainst, 1),
                1e-9
        );
    }

    @Test
    void controlTerminalUsesActiveWarQualityThroughSharedVector() {
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

        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 1) > 0.0);
        assertTrue(BlitzObjective.CONTROL.objective().scoreTerminal(view, 2) < 0.0);
        assertEquals(15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 1), 1e-9);
        assertEquals(-15.0, BlitzObjective.BALANCED.objective().scoreTerminal(view, 2), 1e-9);
    }

    @Test
    void controlTerminalPricesTimingQualityWithoutMakingItDamageValue() {
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

        assertTrue(
                BlitzObjective.CONTROL.objective().scoreTerminal(highMomentum, 1)
                        > BlitzObjective.CONTROL.objective().scoreTerminal(lowMomentum, 1)
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

        assertEquals(110.0, StrategicControlReducer.reduce(view, 1).slotDenial(), 1e-9);
        assertEquals(-110.0, StrategicControlReducer.reduce(view, 2).slotDenial(), 1e-9);
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

        assertTrue(StrategicControlReducer.reduce(favorable, 1).durableControl() > 0.0);
        assertTrue(StrategicControlReducer.reduce(favorable, 2).durableControl() < 0.0);
        assertTrue(StrategicControlReducer.reduce(lost, 1).durableControl() < 0.0);
        assertTrue(StrategicControlReducer.reduce(lost, 2).durableControl() > 0.0);
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
                StrategicControlReducer.reduce(lowerGlobalEdge, 1).durableControl(),
                StrategicControlReducer.reduce(higherGlobalEdge, 1).durableControl(),
                1e-9
        );
        assertEquals(
                StrategicControlReducer.reduce(lowerGlobalEdge, 2).durableControl(),
                StrategicControlReducer.reduce(higherGlobalEdge, 2).durableControl(),
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
