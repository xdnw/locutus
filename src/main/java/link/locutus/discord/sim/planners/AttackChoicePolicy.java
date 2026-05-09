package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;

public interface AttackChoicePolicy {
    AttackType chooseAttackType(AttackChoiceContext context);

    @FunctionalInterface
    interface CandidateFactory {
        AttackCandidate candidate(AttackType attackType);
    }

    record AttackCandidate(
            boolean legal,
            int mapCost,
            double defenderUnitDamage,
            double attackerUnitDamage,
            double defenderResistanceDelta,
            SuperiorityFlagDelta controlDelta
    ) {
        public AttackCandidate {
            if (controlDelta == null) {
                throw new IllegalArgumentException("controlDelta must not be null");
            }
        }
    }

    record AttackChoiceContext(
            AttackType[] attackTypes,
            int mapsAvailable,
            CandidateFactory candidateFactory
    ) {
        public AttackChoiceContext {
            if (attackTypes == null) {
                throw new IllegalArgumentException("attackTypes must not be null");
            }
            if (mapsAvailable < 0) {
                throw new IllegalArgumentException("mapsAvailable must be >= 0");
            }
            if (candidateFactory == null) {
                throw new IllegalArgumentException("candidateFactory must not be null");
            }
        }
    }
}