package link.locutus.discord.sim.planners;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HeuristicCounterDeclarationPolicy implements CounterDeclarationPolicy {
    public static final HeuristicCounterDeclarationPolicy INSTANCE = new HeuristicCounterDeclarationPolicy();

    @FunctionalInterface
    interface CounterCandidateEvaluator {
        void evaluate(int defenderIndex, int attackerIndex, MutableCounterCandidate out);
    }

    @FunctionalInterface
    interface CounterSelectionSink {
        void accept(int defenderIndex, int attackerIndex);
    }

    static final class MutableCounterCandidate {
        boolean legal;
        boolean activePair;
        double activityWeight;
        double counterStrength;
        double targetStrength;
        double targetMarginalActionSpaceValue;

        void set(
                boolean legal,
                boolean activePair,
                double activityWeight,
                double counterStrength,
                double targetStrength,
                double targetMarginalActionSpaceValue
        ) {
            this.legal = legal;
            this.activePair = activePair;
            this.activityWeight = activityWeight;
            this.counterStrength = counterStrength;
            this.targetStrength = targetStrength;
            this.targetMarginalActionSpaceValue = targetMarginalActionSpaceValue;
        }
    }

    private static final double MIN_TARGET_VALUE = 50d;
    private static final double TARGET_VALUE_MULTIPLIER = 0.10d;
    private static final double MAX_STRENGTH_RATIO = 2.0d;

    private HeuristicCounterDeclarationPolicy() {
    }

    @Override
    public List<CounterSelection> chooseDeclarations(CounterDeclarationContext context) {
        int attackerCount = context.attackerCount();
        int defenderCount = context.defenderCount();
        int[] remainingCounterOffensiveSlots = Arrays.copyOf(
                context.remainingCounterOffensiveSlots(),
                context.remainingCounterOffensiveSlots().length
        );
        int[] remainingTargetDefensiveSlots = Arrays.copyOf(
                context.remainingTargetDefensiveSlots(),
                context.remainingTargetDefensiveSlots().length
        );
        boolean[] declaredPairs = new boolean[Math.max(1, attackerCount) * Math.max(1, defenderCount)];
        List<CounterSelection> selections = new ArrayList<>();
        while (selections.size() < context.maxDeclarations()) {
            int bestDefenderIndex = -1;
            int bestAttackerIndex = -1;
            double bestScore = context.scoreThreshold();
            for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
                if (remainingCounterOffensiveSlots[defenderIndex] <= 0) {
                    continue;
                }
                for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
                    if (remainingTargetDefensiveSlots[attackerIndex] <= 0) {
                        continue;
                    }
                    int pairIndex = defenderIndex * attackerCount + attackerIndex;
                    if (declaredPairs[pairIndex]) {
                        continue;
                    }
                    CounterCandidate candidate = context.candidateFactory().candidate(defenderIndex, attackerIndex);
                    if (candidate.activePair()) {
                        continue;
                    }
                    double score = projectedCounterScore(candidate, remainingTargetDefensiveSlots[attackerIndex]);
                    if (score > bestScore) {
                        bestScore = score;
                        bestDefenderIndex = defenderIndex;
                        bestAttackerIndex = attackerIndex;
                    }
                }
            }
            if (bestDefenderIndex < 0) {
                return List.copyOf(selections);
            }
            declaredPairs[bestDefenderIndex * attackerCount + bestAttackerIndex] = true;
            remainingCounterOffensiveSlots[bestDefenderIndex]--;
            remainingTargetDefensiveSlots[bestAttackerIndex]--;
            selections.add(new CounterSelection(bestDefenderIndex, bestAttackerIndex));
        }
        return List.copyOf(selections);
    }

    void chooseDeclarations(
            int defenderCount,
            int attackerCount,
            int[] remainingCounterOffensiveSlots,
            int[] remainingTargetDefensiveSlots,
            int maxDeclarations,
            double scoreThreshold,
            CounterCandidateEvaluator evaluator,
            CounterSelectionSink sink
    ) {
        int[] counterSlots = Arrays.copyOf(remainingCounterOffensiveSlots, remainingCounterOffensiveSlots.length);
        int[] targetSlots = Arrays.copyOf(remainingTargetDefensiveSlots, remainingTargetDefensiveSlots.length);
        boolean[] declaredPairs = new boolean[Math.max(1, attackerCount) * Math.max(1, defenderCount)];
        MutableCounterCandidate candidate = new MutableCounterCandidate();
        int selections = 0;
        while (selections < maxDeclarations) {
            int bestDefenderIndex = -1;
            int bestAttackerIndex = -1;
            double bestScore = scoreThreshold;
            for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
                if (counterSlots[defenderIndex] <= 0) {
                    continue;
                }
                for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
                    if (targetSlots[attackerIndex] <= 0) {
                        continue;
                    }
                    int pairIndex = defenderIndex * attackerCount + attackerIndex;
                    if (declaredPairs[pairIndex]) {
                        continue;
                    }
                    evaluator.evaluate(defenderIndex, attackerIndex, candidate);
                    if (candidate.activePair) {
                        continue;
                    }
                    double score = projectedCounterScore(candidate, targetSlots[attackerIndex]);
                    if (score > bestScore) {
                        bestScore = score;
                        bestDefenderIndex = defenderIndex;
                        bestAttackerIndex = attackerIndex;
                    }
                }
            }
            if (bestDefenderIndex < 0) {
                return;
            }
            declaredPairs[bestDefenderIndex * attackerCount + bestAttackerIndex] = true;
            counterSlots[bestDefenderIndex]--;
            targetSlots[bestAttackerIndex]--;
            sink.accept(bestDefenderIndex, bestAttackerIndex);
            selections++;
        }
    }

    private static double projectedCounterScore(CounterCandidate candidate, int remainingTargetDefensiveSlots) {
        return projectedCounterScore(
                candidate.legal(),
                candidate.activityWeight(),
                candidate.counterStrength(),
                candidate.targetStrength(),
                candidate.targetMarginalActionSpaceValue(),
                remainingTargetDefensiveSlots
        );
    }

    static double projectedCounterScore(
            boolean legal,
            double activityWeight,
            double counterStrength,
            double targetStrength,
            double targetMarginalActionSpaceValue,
            int remainingTargetDefensiveSlots
    ) {
        if (!legal) {
            return 0d;
        }
        if (!(counterStrength > 0d) || !(targetStrength > 0d)) {
            return 0d;
        }
        double activity = Math.max(0d, Math.min(1d, activityWeight));
        double strengthRatio = counterStrength / Math.max(1d, targetStrength);
        double targetValue = Math.max(
                MIN_TARGET_VALUE,
                targetMarginalActionSpaceValue * TARGET_VALUE_MULTIPLIER
        );
        return activity
                * targetValue
                * Math.min(MAX_STRENGTH_RATIO, strengthRatio)
                / Math.max(1, remainingTargetDefensiveSlots);
    }

    private static double projectedCounterScore(MutableCounterCandidate candidate, int remainingTargetDefensiveSlots) {
        return projectedCounterScore(
                candidate.legal,
                candidate.activityWeight,
                candidate.counterStrength,
                candidate.targetStrength,
                candidate.targetMarginalActionSpaceValue,
                remainingTargetDefensiveSlots
        );
    }
}