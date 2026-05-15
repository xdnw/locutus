package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.actions.SimAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectiveDrivenLaterDeclarationScoringPolicyTest {
    @Test
    void rejectsNonPositiveProjectedDeclarationValue() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new UnusedObjective()
        );

        assertEquals(0d, policy.score(context(0d, 1d)), 1e-9);
        assertEquals(0d, policy.score(context(-50d, 1d)), 1e-9);
    }

    @Test
    void usesProjectedDeclarationValueDirectly() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new UnusedObjective()
        );

        assertEquals(120d, policy.score(context(120d, 1d)), 1e-9);
    }

    @Test
    void activityWeightRemainsAHardMultiplier() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new UnusedObjective()
        );

        assertEquals(0d, policy.score(context(120d, 0d)), 1e-9);
        assertEquals(30d, policy.score(context(120d, 0.25d)), 1e-9);
        assertEquals(120d, policy.score(context(120d, 2d)), 1e-9);
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext context(
            double projectedValue,
            double activityWeight
    ) {
        return new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
                projectedValue,
                0d,
                0d,
                0d,
                0d,
                100d,
                100d,
                1,
                1,
                activityWeight
        );
    }

    private static final class UnusedObjective implements StrategicObjective {
        @Override
        public double scoreTerminal(StrategicValueView view, int teamId) {
            return 0d;
        }

        @Override
        public double scoreOpening(
                double immediateHarm,
                double selfExposure,
                double resourceSwing,
                double controlLeverage,
                double futureWarLeverage,
                double targetPressure,
                int teamId
        ) {
            throw new AssertionError("Objective-driven later declarations must not use opening metrics");
        }

        @Override
        public CandidateEdgeComponentPolicy candidateEdgeComponentPolicy() {
            return CandidateEdgeComponentPolicy.none();
        }

        @Override
        public double scoreAction(SimWorld world, SimAction action, int teamId) {
            return 0d;
        }
    }
}
