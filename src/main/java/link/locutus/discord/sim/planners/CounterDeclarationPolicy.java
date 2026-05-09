package link.locutus.discord.sim.planners;

import java.util.List;

public interface CounterDeclarationPolicy {
    List<CounterSelection> chooseDeclarations(CounterDeclarationContext context);

    record CounterSelection(int defenderIndex, int attackerIndex) {
        public CounterSelection {
            if (defenderIndex < 0) {
                throw new IllegalArgumentException("defenderIndex must be >= 0");
            }
            if (attackerIndex < 0) {
                throw new IllegalArgumentException("attackerIndex must be >= 0");
            }
        }
    }

    @FunctionalInterface
    interface CandidateFactory {
        CounterCandidate candidate(int defenderIndex, int attackerIndex);
    }

    record CounterCandidate(
            boolean legal,
            boolean activePair,
            double activityWeight,
            double counterStrength,
            double targetStrength,
            double targetMarginalActionSpaceValue
    ) {
    }

    record CounterDeclarationContext(
            int defenderCount,
            int attackerCount,
            int[] remainingCounterOffensiveSlots,
            int[] remainingTargetDefensiveSlots,
            int maxDeclarations,
            double scoreThreshold,
            CandidateFactory candidateFactory
    ) {
        public CounterDeclarationContext {
            if (defenderCount < 0) {
                throw new IllegalArgumentException("defenderCount must be >= 0");
            }
            if (attackerCount < 0) {
                throw new IllegalArgumentException("attackerCount must be >= 0");
            }
            if (remainingCounterOffensiveSlots == null || remainingCounterOffensiveSlots.length != defenderCount) {
                throw new IllegalArgumentException("remainingCounterOffensiveSlots must match defenderCount");
            }
            if (remainingTargetDefensiveSlots == null || remainingTargetDefensiveSlots.length != attackerCount) {
                throw new IllegalArgumentException("remainingTargetDefensiveSlots must match attackerCount");
            }
            if (maxDeclarations <= 0) {
                throw new IllegalArgumentException("maxDeclarations must be > 0");
            }
            if (!Double.isFinite(scoreThreshold)) {
                throw new IllegalArgumentException("scoreThreshold must be finite");
            }
            if (candidateFactory == null) {
                throw new IllegalArgumentException("candidateFactory must not be null");
            }
        }
    }
}