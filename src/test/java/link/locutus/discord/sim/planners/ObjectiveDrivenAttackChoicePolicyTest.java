package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicValueView;
import link.locutus.discord.sim.actions.SimAction;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObjectiveDrivenAttackChoicePolicyTest {
    @Test
    void choosesLowerExposureWhenObjectivePenalizesSelfDamage() {
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                new LinearOpeningObjective(0.10d, 1.00d, 0d, 0d),
                null
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_SOLDIER},
                6,
                attackType -> attackType == AttackType.GROUND
                        ? candidate(10d, 0d, 0d, SuperiorityFlagDelta.NONE)
                        : candidate(30d, 5d, 0d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(AttackType.GROUND, choice);
    }

    @Test
    void controlObjectiveCanPreferControlTransitionOverRawDamage() {
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                new LinearOpeningObjective(0.05d, 0d, 4.00d, 0d),
                null
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_TANK},
                6,
                attackType -> attackType == AttackType.GROUND
                        ? candidate(2d, 0d, -10d, SuperiorityFlagDelta.of(1, 0, 0, false, false, false))
                        : candidate(40d, 0d, -12d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(AttackType.GROUND, choice);
    }

    @Test
    void attackTypeWeightsRemainPartOfObjectiveDrivenSelection() {
        double[] attackWeights = neutralAttackWeights();
        attackWeights[AttackType.AIRSTRIKE_TANK.ordinal()] = 3d;
        SideOpeningSettings openingSettings = new SideOpeningSettings(
                neutralWarWeights(),
                attackWeights,
                CandidateEdgeAdmissionPolicy.defaultPolicy()
        );
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                new LinearOpeningObjective(1.00d, 0d, 0d, 0d),
                openingSettings
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_TANK},
                6,
                attackType -> attackType == AttackType.GROUND
                        ? candidate(10d, 0d, 0d, SuperiorityFlagDelta.NONE)
                        : candidate(5d, 0d, 0d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(AttackType.AIRSTRIKE_TANK, choice);
    }

    @Test
    void futureWarLeverageComesFromActionSpaceQualityNotResistanceDrain() {
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                new LinearOpeningObjective(0d, 0d, 0d, 5.00d),
                null
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_TANK},
                6,
                attackType -> attackType == AttackType.GROUND
                        ? candidate(0d, 0d, -50d, 0d, 0d, SuperiorityFlagDelta.NONE)
                        : candidate(0d, 0d, -5d, 0d, 0.25d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(AttackType.AIRSTRIKE_TANK, choice);
    }

    @Test
    void resourceSwingIsNotSmuggledIntoImmediateHarm() {
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                new LinearOpeningObjective(1.00d, 0d, 0d, 0d),
                null
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.MISSILE},
                8,
                attackType -> candidate(0d, 0d, 0d, 100d, 0d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(null, choice);
    }

    @Test
    void controlObjectiveCanValueSpecialistResourcePressure() {
        ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                link.locutus.discord.sim.BlitzObjective.CONTROL.objective(),
                null
        );

        AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                new AttackType[]{AttackType.MISSILE},
                8,
                attackType -> candidate(0d, 0d, 0d, 120d, 0d, SuperiorityFlagDelta.NONE)
        ));

        assertEquals(AttackType.MISSILE, choice);
    }

        @Test
        void controlObjectiveDoesNotTreatConventionalLootAsControlProgress() {
                ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                                link.locutus.discord.sim.BlitzObjective.CONTROL.objective(),
                                null
                );

                AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                                new AttackType[]{AttackType.AIRSTRIKE_MONEY, AttackType.GROUND},
                                8,
                                attackType -> attackType == AttackType.AIRSTRIKE_MONEY
                                ? candidate(2d, 0d, -12d, 500d, 0.01d, SuperiorityFlagDelta.NONE)
                                                : candidate(20d, 0d, -10d, 0d, 0.10d, SuperiorityFlagDelta.NONE)
                ));

                assertEquals(AttackType.GROUND, choice);
        }

        @Test
        void futureWarLeverageIncludesTimingWindowAdvantage() {
                ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                                new LinearOpeningObjective(0d, 0d, 0d, 5.00d),
                                null
                );

                AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_TANK},
                                6,
                                attackType -> attackType == AttackType.GROUND
                                                ? candidate(0d, 0d, -10d, 0d, 0d, 0.40d, SuperiorityFlagDelta.NONE)
                                                : candidate(0d, 0d, -10d, 0d, 0.25d, 0d, SuperiorityFlagDelta.NONE)
                ));

                assertEquals(AttackType.GROUND, choice);
        }

        @Test
        void specialistStockpileWaitsForUsefulConventionalFollowThrough() {
                ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                                new LinearOpeningObjective(0.10d, 0d, 0d, 0d),
                                null
                );

                AttackType choice = policy.chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                                new AttackType[]{AttackType.GROUND, AttackType.MISSILE},
                                8,
                                attackType -> attackType == AttackType.GROUND
                                                ? candidate(50d, 0d, -10d, 0d, 0d, 0d, 0d, SuperiorityFlagDelta.NONE)
                                                : candidate(90d, 0d, -10d, 0d, 0d, 0d, 100d, SuperiorityFlagDelta.NONE)
                ));

                assertEquals(AttackType.GROUND, choice);
        }

        @Test
        void mutableProjectionPathCarriesTargetPressure() {
                ObjectiveDrivenAttackChoicePolicy policy = new ObjectiveDrivenAttackChoicePolicy(
                                new TargetPressureObjective(),
                                null
                );
                ObjectiveDrivenAttackChoicePolicy.MutableAttackCandidate scratch =
                                new ObjectiveDrivenAttackChoicePolicy.MutableAttackCandidate();

                AttackType choice = policy.chooseAttackType(
                                new AttackType[]{AttackType.GROUND, AttackType.AIRSTRIKE_TANK},
                                6,
                                (attackType, out) -> {
                                        if (attackType == AttackType.GROUND) {
                                                out.set(true, 3, 0d, 0d, 0d, -10d, 0d, 0d, 25d, 0d, SuperiorityFlagDelta.NONE);
                                        } else {
                                                out.set(true, 4, 50d, 0d, 0d, -12d, 0d, 0d, 0d, 0d, SuperiorityFlagDelta.NONE);
                                        }
                                },
                                scratch,
                                null
                );

                assertEquals(AttackType.GROUND, choice);
        }

    private static AttackChoicePolicy.AttackCandidate candidate(
            double defenderDamage,
            double attackerDamage,
            double defenderResistanceDelta,
            SuperiorityFlagDelta controlDelta
    ) {
        return new AttackChoicePolicy.AttackCandidate(
                true,
                3,
                defenderDamage,
                attackerDamage,
                0d,
                defenderResistanceDelta,
                0d,
                0d,
                                0d,
                                0d,
                controlDelta
        );
    }

    private static AttackChoicePolicy.AttackCandidate candidate(
            double defenderDamage,
            double attackerDamage,
            double defenderResistanceDelta,
            double resourceSwing,
            double actionSpaceQuality,
            SuperiorityFlagDelta controlDelta
    ) {
        return candidate(
                defenderDamage,
                attackerDamage,
                defenderResistanceDelta,
                resourceSwing,
                actionSpaceQuality,
                0d,
                controlDelta
        );
    }

    private static AttackChoicePolicy.AttackCandidate candidate(
            double defenderDamage,
            double attackerDamage,
            double defenderResistanceDelta,
            double resourceSwing,
            double actionSpaceQuality,
            double timingWindowAdvantage,
                        double conventionalFollowThroughValue,
            SuperiorityFlagDelta controlDelta
    ) {
        return new AttackChoicePolicy.AttackCandidate(
                true,
                3,
                defenderDamage,
                attackerDamage,
                resourceSwing,
                defenderResistanceDelta,
                actionSpaceQuality,
                                timingWindowAdvantage,
                0d,
                conventionalFollowThroughValue,
                controlDelta
        );
    }

    private static AttackChoicePolicy.AttackCandidate candidate(
            double defenderDamage,
            double attackerDamage,
            double defenderResistanceDelta,
            double resourceSwing,
            double actionSpaceQuality,
            double timingWindowAdvantage,
            SuperiorityFlagDelta controlDelta
    ) {
        return candidate(
                defenderDamage,
                attackerDamage,
                defenderResistanceDelta,
                resourceSwing,
                actionSpaceQuality,
                timingWindowAdvantage,
                0d,
                controlDelta
        );
    }

    private static double[] neutralWarWeights() {
        double[] weights = new double[link.locutus.discord.apiv1.enums.WarType.values.length];
        java.util.Arrays.fill(weights, 1d);
        return weights;
    }

    private static double[] neutralAttackWeights() {
        double[] weights = new double[AttackType.values.length];
        java.util.Arrays.fill(weights, 1d);
        return weights;
    }

    private record LinearOpeningObjective(
            double harmWeight,
            double exposureWeight,
            double controlWeight,
            double futureWeight
    ) implements StrategicObjective {
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
            return (harmWeight * immediateHarm)
                    - (exposureWeight * selfExposure)
                    + (controlWeight * controlLeverage)
                    + (futureWeight * futureWarLeverage);
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
