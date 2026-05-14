package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.actions.SimAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void scarceTargetSlotDoesNotAmplifyWeakConventionalDeclarer() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            new TargetPressureObjective()
        );

        double lastSlotScore = policy.score(context(55d, 100d, 1, 1));
        double openTargetScore = policy.score(context(55d, 100d, 1, 3));

        assertTrue(lastSlotScore < openTargetScore,
            "Weak conventional declarations should not be rewarded for consuming the target's scarce final defensive slot");
    }

    @Test
    void scarceTargetSlotStillRewardsActionableDeclarer() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            new TargetPressureObjective()
        );

        double lastSlotScore = policy.score(context(130d, 100d, 1, 1));
        double openTargetScore = policy.score(context(130d, 100d, 1, 3));

        assertTrue(lastSlotScore > openTargetScore,
            "Actionable declarations can still value a scarce target slot");
    }

    @Test
    void scarceTargetSlotPenalizesWeakDeclarerWhenBetterDeclarerIsAvailable() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            new TargetPressureObjective()
        );

        double noBetterDeclarerScore = policy.score(context(55d, 100d, 1, 1, LaterDeclarationFit.actionability(55d, 100d)));
        double betterDeclarerAvailableScore = policy.score(context(55d, 100d, 1, 1, LaterDeclarationFit.actionability(150d, 100d)));

        assertTrue(betterDeclarerAvailableScore < noBetterDeclarerScore * 0.50d,
            "Weak declarations should not spend a scarce target slot as if stronger available declarers did not exist");
    }

    @Test
    void controlPolicyDoesNotTreatTargetPressureAsAlreadyCapturedDamage() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            link.locutus.discord.sim.BlitzObjective.CONTROL.objective()
        );

        double pressureOnlyScore = policy.score(new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
            0d,
            0d,
            0d,
            0d,
            0d,
            0d,
            250d,
            100d,
            100d,
            0d,
            1,
            1,
            1d,
            0d,
            1d
        ));
        double actionableScore = policy.score(new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
            0d,
            0d,
            0d,
            0d,
            1d,
            1d,
            250d,
            100d,
            100d,
            0d,
            1,
            1,
            1d,
            0d,
            1d
        ));

        assertEquals(0d, pressureOnlyScore, 1e-9,
            "CONTROL later declarations must not score pressure-only targets as if damage already happened");
        assertTrue(actionableScore > pressureOnlyScore,
            "Target pressure should become valuable again once the declaration has control/future-war actionability");
    }

    @Test
    void controlPolicyTreatsSpecialistResourcePressureAsActionable() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            link.locutus.discord.sim.BlitzObjective.CONTROL.objective()
        );

        double specialistScore = policy.score(new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
            0d,
            0d,
            0d,
            320d,
            0d,
            0d,
            250d,
            60d,
            200d,
            0d,
            1,
            1,
            LaterDeclarationFit.specialistSlotActionability(320d, 250d),
            0d,
            1d
        ));

        assertTrue(specialistScore > 0d,
            "Legal specialist pressure should be visible to CONTROL even when conventional control is not attainable");
    }

    @Test
    void controlPolicyDoesNotLetReadinessOnlyLaterDeclarationTurnPositive() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                link.locutus.discord.sim.BlitzObjective.CONTROL.objective()
        );

        double readinessOnlyScore = policy.score(new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
            0d,
            0d,
            0d,
            0d,
            0d,
            0d,
            1d,
            250d,
            100d,
            100d,
            0d,
            1,
            1,
            1d,
            0d,
            1d
        ));

        assertEquals(0d, readinessOnlyScore, 1e-9,
                "Declaration readiness alone should preserve opening visibility, not make later declarations positive by itself");
    }

    @Test
    void specialistPressureIsDampedBySevereSelfExposure() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
            link.locutus.discord.sim.BlitzObjective.CONTROL.objective()
        );

        double cleanSpecialistScore = policy.score(specialistContext(0d));
        double exposedSpecialistScore = policy.score(specialistContext(500d));

        assertTrue(cleanSpecialistScore > 0d);
        assertTrue(exposedSpecialistScore < cleanSpecialistScore * 0.25d,
                "Specialist resource pressure should not overwhelm severe projected self-exposure");
    }

    @Test
    void unsupportedWeakDeclarationScoresBelowSupportedWarOnSameTarget() {
        ObjectiveDrivenLaterDeclarationScoringPolicy policy = new ObjectiveDrivenLaterDeclarationScoringPolicy(
                new TargetPressureObjective()
        );

        double isolatedScore = policy.score(context(65d, 100d, 1, 2, LaterDeclarationFit.actionability(90d, 100d), 0d));
        double supportedScore = policy.score(context(65d, 100d, 1, 2, LaterDeclarationFit.actionability(90d, 100d), 0.85d));

        assertTrue(supportedScore > isolatedScore * 1.35d,
                "A weak marginal declaration should be worth more when committed allies can support the same target");
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
                LaterDeclarationFit.actionability(declarerStrength, targetStrength),
                0d,
                1d
        );
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext context(
            double declarerStrength,
            double targetStrength,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double targetBestActionability
    ) {
        return context(declarerStrength, targetStrength, remainingDeclarerSlots, remainingTargetSlots, targetBestActionability, 0d);
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext context(
            double declarerStrength,
            double targetStrength,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double targetBestActionability,
            double targetSupportActionability
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
                targetBestActionability,
                targetSupportActionability,
                1d
            );
    }

    private static LaterDeclarationScoringPolicy.LaterDeclarationScoreContext specialistContext(double selfExposure) {
        return new LaterDeclarationScoringPolicy.LaterDeclarationScoreContext(
                0d,
                0d,
                selfExposure,
                320d,
                0d,
                0d,
                250d,
                60d,
                200d,
                0d,
                1,
                1,
                LaterDeclarationFit.specialistSlotActionability(320d, 250d),
                0d,
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
