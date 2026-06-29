package link.locutus.discord.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlitzObjectiveTest {
    @Test
    void defaultObjectivePreservesNetDamageIdentity() {
        assertEquals(BlitzObjective.NET_DAMAGE, BlitzObjective.defaultObjective());
    }

    @Test
    void objectiveComponentRetentionPoliciesStayExplicit() {
        assertTrue(BlitzObjective.NET_DAMAGE.objective().candidateEdgeComponentPolicy().retainImmediateHarm());
        assertTrue(BlitzObjective.DAMAGE.objective().candidateEdgeComponentPolicy().retainImmediateHarm());
        assertTrue(BlitzObjective.MINIMUM_DAMAGE_RECEIVED.objective().candidateEdgeComponentPolicy().retainImmediateHarm());
        assertTrue(BlitzObjective.CONTROL.objective().candidateEdgeComponentPolicy().retainsAny());
        assertTrue(BlitzObjective.BALANCED.objective().candidateEdgeComponentPolicy().retainsAny());
    }

    @Test
    void controlTerminalUsesProjectedStrategicTotals() {
        StrategicValueView view = totalsView(140.0, 100.0);

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
        StrategicValueView view = totalsView(140.0, 100.0);

        StrategicObjective objective = BlitzObjective.CONTROL.objective();
        assertEquals(
                objective.scoreTerminal(view, 1) - objective.scoreTerminal(view, 2),
                objective.scoreTerminalComparison(view, 1, 2),
                1e-9
        );
    }

    @Test
    void terminalScoringIgnoresProjectionDiagnosticsOnceTotalsAndSlotsMatch() {
        TeamProjectionView lowerDiagnostics = projectionView(140.0, 100.0, 10.0, 20.0, false);
        TeamProjectionView higherDiagnostics = projectionView(140.0, 100.0, 10.0, 20.0, true);

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
        TeamProjectionView view = projectionView(100.0, 100.0, 40.0, 150.0, false);

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

    private static StrategicValueView totalsView(double teamOneValue, double teamTwoValue) {
        return new StrategicValueView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, teamOneValue);
                consumer.accept(202, 2, teamTwoValue);
            }
        };
    }

    private static TeamProjectionView projectionView(
            double teamOneValue,
            double teamTwoValue,
            double attackerSlotCost,
            double defenderSlotDenial,
            boolean highDiagnostics
    ) {
        return new TeamProjectionView() {
            @Override
            public void forEachNation(NationScoreConsumer consumer) {
                consumer.accept(101, 1, 1_000.0);
                consumer.accept(202, 2, 1_000.0);
            }

            @Override
            public void forEachNationStrategicValue(NationValueConsumer consumer) {
                consumer.accept(101, 1, teamOneValue);
                consumer.accept(202, 2, teamTwoValue);
            }

            @Override
            public void forEachWarControl(WarControlConsumer consumer) {
                if (highDiagnostics) {
                    consumer.accept(1, 2, 1, 1, 1, 100, 5);
                } else {
                    consumer.accept(1, 2, 1, 0, 0, 100, 95);
                }
            }

            @Override
            public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
                if (highDiagnostics) {
                    consumer.accept(1, 2, 20.0, 0.9, 4.0);
                } else {
                    consumer.accept(1, 2, 1.0, 0.1, 0.0);
                }
            }

            @Override
            public void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
                consumer.accept(1, 2, attackerSlotCost, defenderSlotDenial);
            }
        };
    }
}
