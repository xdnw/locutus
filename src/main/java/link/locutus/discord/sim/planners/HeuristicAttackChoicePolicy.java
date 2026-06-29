package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

public final class HeuristicAttackChoicePolicy implements AttackChoicePolicy {
    public static final HeuristicAttackChoicePolicy INSTANCE = new HeuristicAttackChoicePolicy();

    private static final double DEFENDER_RESISTANCE_WEIGHT = 5d;
    private static final double GROUND_SUPERIORITY_WEIGHT = 18d;
    private static final double AIR_SUPERIORITY_WEIGHT = 20d;
    private static final double BLOCKADE_WEIGHT = 14d;
    private static final double CLEAR_GROUND_WEIGHT = 8d;
    private static final double CLEAR_AIR_WEIGHT = 8d;
    private static final double CLEAR_BLOCKADE_WEIGHT = 6d;
    private static final double ATTACKER_UNIT_DAMAGE_WEIGHT = 0.35d;

    private HeuristicAttackChoicePolicy() {
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
            double score = attackResultScore(candidate);
            if (bestAttackType == null
                    || score > bestScore
                    || (score == bestScore && attackType.ordinal() < bestAttackType.ordinal())) {
                bestAttackType = attackType;
                bestScore = score;
            }
        }
        return bestScore > 0d ? bestAttackType : null;
    }

    private static double attackResultScore(AttackCandidate candidate) {
        double controlScore = controlScore(candidate.controlDelta());
        return candidate.defenderUnitDamage()
                + (-candidate.defenderResistanceDelta() * DEFENDER_RESISTANCE_WEIGHT)
                + controlScore
                - candidate.attackerUnitDamage() * ATTACKER_UNIT_DAMAGE_WEIGHT;
    }

    private static double controlScore(SuperiorityFlagDelta controlDelta) {
        double controlScore = 0d;
        controlScore += Math.max(0, controlDelta.groundSuperiority()) * GROUND_SUPERIORITY_WEIGHT;
        controlScore += Math.max(0, controlDelta.airSuperiority()) * AIR_SUPERIORITY_WEIGHT;
        controlScore += Math.max(0, controlDelta.blockade()) * BLOCKADE_WEIGHT;
        controlScore += controlDelta.clearGroundSuperiority() ? CLEAR_GROUND_WEIGHT : 0d;
        controlScore += controlDelta.clearAirSuperiority() ? CLEAR_AIR_WEIGHT : 0d;
        controlScore += controlDelta.clearBlockade() ? CLEAR_BLOCKADE_WEIGHT : 0d;
        return controlScore;
    }
}
