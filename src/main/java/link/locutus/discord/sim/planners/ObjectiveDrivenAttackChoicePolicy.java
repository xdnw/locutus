package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

/**
 * Attack selector that scores each resolved attack candidate through the side objective.
 */
public final class ObjectiveDrivenAttackChoicePolicy implements AttackChoicePolicy {
    private static final int DEFAULT_TEAM_ID = 0;

    private final SideOpeningSettings openingSettings;

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
        double conventionalFollowThroughValue;

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
            this.conventionalFollowThroughValue = conventionalFollowThroughValue;
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
        this.openingSettings = openingSettings == null ? SideOpeningSettings.defaults(objective) : openingSettings;
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
        double score = OpeningEvaluator.baseScore(
                Math.max(0d, candidate.defenderUnitDamage()),
                AttackObjectiveComponentMapper.resourceSwingForObjective(attackType, candidate.resourceSwing())
        )
            * openingSettings.attackTypeWeight(attackType);
        if (AttackObjectiveComponentMapper.isSpecialist(attackType) && candidate.conventionalFollowThroughValue() > 0d) {
            score -= Math.min(score * 0.75d, candidate.conventionalFollowThroughValue() * 0.20d);
        }
        return score;
    }

    double scoreCandidate(AttackType attackType, MutableAttackCandidate candidate) {
        double score = OpeningEvaluator.baseScore(
                Math.max(0d, candidate.defenderUnitDamage),
                AttackObjectiveComponentMapper.resourceSwingForObjective(attackType, candidate.resourceSwing)
        )
            * openingSettings.attackTypeWeight(attackType);
        if (AttackObjectiveComponentMapper.isSpecialist(attackType) && candidate.conventionalFollowThroughValue > 0d) {
            score -= Math.min(score * 0.75d, candidate.conventionalFollowThroughValue * 0.20d);
        }
        return score;
    }
}
