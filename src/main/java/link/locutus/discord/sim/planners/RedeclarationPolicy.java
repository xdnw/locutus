package link.locutus.discord.sim.planners;

import java.util.List;

public interface RedeclarationPolicy {
    List<RedeclarationChoice> chooseRedeclarations(RedeclarationContext context);

    record RedeclarationChoice(int edgeIndex, int attackerIndex, int defenderIndex) {
        public RedeclarationChoice {
            if (edgeIndex < 0) {
                throw new IllegalArgumentException("edgeIndex must be >= 0");
            }
            if (attackerIndex < 0) {
                throw new IllegalArgumentException("attackerIndex must be >= 0");
            }
            if (defenderIndex < 0) {
                throw new IllegalArgumentException("defenderIndex must be >= 0");
            }
        }
    }

    @FunctionalInterface
    interface CandidateFactory {
        RedeclareCandidate candidate(int edgeIndex);
    }

    record RedeclareCandidate(
            int attackerIndex,
            int defenderIndex,
            boolean legal,
            boolean activePair,
            double attackerStrength,
            double defenderStrength,
            double activityWeight,
            double baseEdgeScore,
            int blockedTurns
    ) {
        public RedeclareCandidate {
            if (attackerIndex < 0) {
                throw new IllegalArgumentException("attackerIndex must be >= 0");
            }
            if (defenderIndex < 0) {
                throw new IllegalArgumentException("defenderIndex must be >= 0");
            }
        }
    }

    record RedeclarationContext(
            int edgeCount,
            int[] remainingAttackerSlots,
            int[] remainingDefenderSlots,
            double scoreThreshold,
            int horizonRemainingTurns,
            CandidateFactory candidateFactory
    ) {
        public RedeclarationContext {
            if (edgeCount < 0) {
                throw new IllegalArgumentException("edgeCount must be >= 0");
            }
            if (remainingAttackerSlots == null) {
                throw new IllegalArgumentException("remainingAttackerSlots must not be null");
            }
            if (remainingDefenderSlots == null) {
                throw new IllegalArgumentException("remainingDefenderSlots must not be null");
            }
            if (!Double.isFinite(scoreThreshold)) {
                throw new IllegalArgumentException("scoreThreshold must be finite");
            }
            if (horizonRemainingTurns < 0) {
                throw new IllegalArgumentException("horizonRemainingTurns must be >= 0");
            }
            if (candidateFactory == null) {
                throw new IllegalArgumentException("candidateFactory must not be null");
            }
        }
    }
}