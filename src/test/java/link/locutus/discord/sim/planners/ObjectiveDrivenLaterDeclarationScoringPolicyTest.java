package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.actions.SimAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectiveDrivenLaterDeclarationScoringPolicyTest {
    @Test
    void discountsPressureOnlyDeclarationWhenDeclarerCannotActOnTarget() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new TargetPressureObjective()
        );

        double weakScore = policy.score(context(50d, 100d));
        double parityScore = policy.score(context(100d, 100d));
        double strongScore = policy.score(context(400d, 100d));

        assertTrue(weakScore < parityScore * 0.30d);
        assertTrue(strongScore > parityScore);
        assertTrue(strongScore <= parityScore * 1.50d);
    }

    @Test
    void prefersComparableUnderutilizedDeclarerOverLastSlotDeclarer() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new TargetPressureObjective()
        );

        double lastSlotScore = policy.score(context(110d, 100d, 1, 1));
        double openSlotScore = policy.score(context(110d, 100d, 3, 1));

        assertTrue(openSlotScore > lastSlotScore);
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext context(
            double declarerStrength,
            double targetStrength
    ) {
        return context(declarerStrength, targetStrength, 1, 1);
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext context(
            double declarerStrength,
            double targetStrength,
            int remainingDeclarerSlots,
            int remainingTargetSlots
    ) {
        return new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                100d,
                declarerStrength,
                targetStrength,
                0d,
                remainingDeclarerSlots,
                remainingTargetSlots,
                1d
        );
    }

    private static final class TargetPressureObjective implements StrategicObjective {
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
            return targetPressure;
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
