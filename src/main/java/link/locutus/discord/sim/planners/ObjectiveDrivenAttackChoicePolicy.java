package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

/**
 * Attack selector that scores each resolved attack candidate through the side objective.
 */
public final class ObjectiveDrivenAttackChoicePolicy implements AttackChoicePolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final StrategicObjective objective;
    private final SideOpeningSettings openingSettings;
    private final int teamId;
    private final OpeningMetricVector.Mutable metrics = new OpeningMetricVector.Mutable();

    @FunctionalInterface
    interface AttackEvaluator {
        void evaluate(AttackType attackType, MutableAttackCandidate out);
    }

    @FunctionalInterface
    interface BestAttackObserver {
        void recordBestAttack();
    }

    static final class MutableAttackCandidate {
        boolean legal;
        int mapCost;
        double defenderUnitDamage;
        double attackerUnitDamage;
        double resourceSwing;
        double defenderResistanceDelta;
        double actionSpaceQuality;
        double timingWindowAdvantage;
        double targetPressure;
        double conventionalFollowThroughValue;
        SuperiorityFlagDelta controlDelta = SuperiorityFlagDelta.NONE;

        void set(
                boolean legal,
                int mapCost,
                double defenderUnitDamage,
                double attackerUnitDamage,
                double resourceSwing,
                double defenderResistanceDelta,
                double actionSpaceQuality,
                double timingWindowAdvantage,
                double targetPressure,
                double conventionalFollowThroughValue,
                SuperiorityFlagDelta controlDelta
        ) {
            this.legal = legal;
            this.mapCost = mapCost;
            this.defenderUnitDamage = defenderUnitDamage;
            this.attackerUnitDamage = attackerUnitDamage;
            this.resourceSwing = resourceSwing;
            this.defenderResistanceDelta = defenderResistanceDelta;
            this.actionSpaceQuality = actionSpaceQuality;
            this.timingWindowAdvantage = timingWindowAdvantage;
            this.targetPressure = targetPressure;
            this.conventionalFollowThroughValue = conventionalFollowThroughValue;
            this.controlDelta = controlDelta == null ? SuperiorityFlagDelta.NONE : controlDelta;
        }
    }

    public ObjectiveDrivenAttackChoicePolicy(StrategicObjective objective, SideOpeningSettings openingSettings) {
        this(objective, openingSettings, DEFAULT_TEAM_ID);
    }

    public ObjectiveDrivenAttackChoicePolicy(
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            int teamId
    ) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        this.objective = objective;
        this.openingSettings = openingSettings == null ? SideOpeningSettings.defaults(objective) : openingSettings;
        this.teamId = teamId;
    }

    @Override
    public AttackType chooseAttackType(AttackChoiceContext context) {
        AttackType bestAttackType = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AttackType attackType : context.attackTypes()) {
            AttackCandidate candidate = context.candidateFactory().candidate(attackType);
            if (!candidate.legal()) {
                continue;
            }
            int mapCost = candidate.mapCost();
            if (mapCost <= 0 || mapCost > context.mapsAvailable()) {
                continue;
            }
            double score = scoreCandidate(attackType, candidate);
            if (bestAttackType == null
                    || score > bestScore
                    || (score == bestScore && attackType.ordinal() < bestAttackType.ordinal())) {
                bestAttackType = attackType;
                bestScore = score;
            }
        }
        return bestScore > 0d ? bestAttackType : null;
    }

    AttackType chooseAttackType(
            AttackType[] attackTypes,
            int mapsAvailable,
            AttackEvaluator evaluator,
            MutableAttackCandidate candidate,
            BestAttackObserver bestAttackObserver
    ) {
        AttackType bestAttackType = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AttackType attackType : attackTypes) {
            evaluator.evaluate(attackType, candidate);
            if (!candidate.legal) {
                continue;
            }
            int mapCost = candidate.mapCost;
            if (mapCost <= 0 || mapCost > mapsAvailable) {
                continue;
            }
            double score = scoreCandidate(attackType, candidate);
            if (bestAttackType == null
                    || score > bestScore
                    || (score == bestScore && attackType.ordinal() < bestAttackType.ordinal())) {
                bestAttackType = attackType;
                bestScore = score;
                if (bestAttackObserver != null) {
                    bestAttackObserver.recordBestAttack();
                }
            }
        }
        return bestScore > 0d ? bestAttackType : null;
    }

    double scoreCandidate(AttackType attackType, AttackCandidate candidate) {
        double controlLeverage = AttackObjectiveComponentMapper.controlLeverage(candidate.controlDelta());
        double actionSpaceQuality = Math.max(0d, candidate.actionSpaceQuality());
        double timingWindowAdvantage = Math.max(0d, candidate.timingWindowAdvantage());
        metrics.set(
                Math.max(0d, candidate.defenderUnitDamage()),
                Math.max(0d, candidate.attackerUnitDamage()),
                AttackObjectiveComponentMapper.resourceSwingForObjective(
                        attackType,
                    candidate.resourceSwing()
                ),
                controlLeverage,
                tacticalMomentum(candidate.defenderResistanceDelta()),
                actionSpaceQuality,
                timingWindowAdvantage,
                Math.max(0d, candidate.targetPressure())
        );
        double score = objective.scoreOpening(metrics, teamId) * openingSettings.attackTypeWeight(attackType);
        if (AttackObjectiveComponentMapper.isSpecialist(attackType) && candidate.conventionalFollowThroughValue() > 0d) {
            score -= Math.min(score * 0.75d, candidate.conventionalFollowThroughValue() * 0.20d);
        }
        return score;
    }

    double scoreCandidate(AttackType attackType, MutableAttackCandidate candidate) {
        double controlLeverage = AttackObjectiveComponentMapper.controlLeverage(candidate.controlDelta);
        double actionSpaceQuality = Math.max(0d, candidate.actionSpaceQuality);
        double timingWindowAdvantage = Math.max(0d, candidate.timingWindowAdvantage);
        metrics.set(
                Math.max(0d, candidate.defenderUnitDamage),
                Math.max(0d, candidate.attackerUnitDamage),
                AttackObjectiveComponentMapper.resourceSwingForObjective(
                        attackType,
                    candidate.resourceSwing
                ),
                controlLeverage,
                tacticalMomentum(candidate.defenderResistanceDelta),
                actionSpaceQuality,
                timingWindowAdvantage,
                Math.max(0d, candidate.targetPressure)
        );
        double score = objective.scoreOpening(metrics, teamId) * openingSettings.attackTypeWeight(attackType);
        if (AttackObjectiveComponentMapper.isSpecialist(attackType) && candidate.conventionalFollowThroughValue > 0d) {
            score -= Math.min(score * 0.75d, candidate.conventionalFollowThroughValue * 0.20d);
        }
        return score;
    }

    private static double tacticalMomentum(double defenderResistanceDelta) {
        return clamp01(-defenderResistanceDelta / 100d);
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}
