package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.StrategicTimingValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class HeuristicRedeclarationPolicy implements RedeclarationPolicy {
    public static final HeuristicRedeclarationPolicy INSTANCE = new HeuristicRedeclarationPolicy();

    @FunctionalInterface
    interface RedeclareCandidateEvaluator {
        void evaluate(int edgeIndex, MutableRedeclareCandidate out);
    }

    @FunctionalInterface
    interface RedeclarationSink {
        void accept(int edgeIndex, int attackerIndex, int defenderIndex);
    }

    static final class MutableRedeclareCandidate {
        int attackerIndex;
        int defenderIndex;
        boolean legal;
        boolean activePair;
        double attackerStrength;
        double defenderStrength;
        double activityWeight;
        double baseEdgeScore;
        int blockedTurns;

        void set(
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
            this.attackerIndex = attackerIndex;
            this.defenderIndex = defenderIndex;
            this.legal = legal;
            this.activePair = activePair;
            this.attackerStrength = attackerStrength;
            this.defenderStrength = defenderStrength;
            this.activityWeight = activityWeight;
            this.baseEdgeScore = baseEdgeScore;
            this.blockedTurns = blockedTurns;
        }
    }

    private static final double MAX_STRENGTH_RATIO = 2.0d;

    private HeuristicRedeclarationPolicy() {
    }

    @Override
    public List<RedeclarationChoice> chooseRedeclarations(RedeclarationContext context) {
        int[] remainingAttackerSlots = Arrays.copyOf(
                context.remainingAttackerSlots(),
                context.remainingAttackerSlots().length
        );
        int[] remainingDefenderSlots = Arrays.copyOf(
                context.remainingDefenderSlots(),
                context.remainingDefenderSlots().length
        );
        boolean[] edgeUsed = new boolean[Math.max(1, context.edgeCount())];
        List<RedeclarationChoice> choices = new ArrayList<>();
        double[] deferredBestByAttacker = new double[Math.max(1, remainingAttackerSlots.length)];
        while (true) {
            int bestEdge = -1;
            RedeclareCandidate bestCandidate = null;
            double bestScore = context.scoreThreshold();
            Arrays.fill(deferredBestByAttacker, 0d);
            for (int edgeIndex = 0; edgeIndex < context.edgeCount(); edgeIndex++) {
                if (edgeUsed[edgeIndex]) {
                    continue;
                }
                RedeclareCandidate candidate = context.candidateFactory().candidate(edgeIndex);
                if (remainingAttackerSlots[candidate.attackerIndex()] <= 0) {
                    continue;
                }
                if (candidate.activePair()) {
                    continue;
                }
                if (!candidate.legal() && candidate.blockedTurns() > 0
                        && remainingDefenderSlots[candidate.defenderIndex()] > 0) {
                    double deferredScore = projectedDeferredRedeclareScore(
                            candidate,
                            remainingAttackerSlots[candidate.attackerIndex()],
                            context.horizonRemainingTurns()
                    );
                    if (deferredScore > deferredBestByAttacker[candidate.attackerIndex()]) {
                        deferredBestByAttacker[candidate.attackerIndex()] = deferredScore;
                    }
                }
            }
            for (int edgeIndex = 0; edgeIndex < context.edgeCount(); edgeIndex++) {
                if (edgeUsed[edgeIndex]) {
                    continue;
                }
                RedeclareCandidate candidate = context.candidateFactory().candidate(edgeIndex);
                if (remainingAttackerSlots[candidate.attackerIndex()] <= 0) {
                    continue;
                }
                if (candidate.activePair() || !candidate.legal()) {
                    continue;
                }
                if (remainingDefenderSlots[candidate.defenderIndex()] <= 0) {
                    continue;
                }
                double score = projectedRedeclareScore(candidate, remainingAttackerSlots[candidate.attackerIndex()]);
                if (score <= deferredBestByAttacker[candidate.attackerIndex()]) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestEdge = edgeIndex;
                    bestCandidate = candidate;
                }
            }
            if (bestEdge < 0 || bestCandidate == null) {
                return List.copyOf(choices);
            }
            edgeUsed[bestEdge] = true;
            remainingAttackerSlots[bestCandidate.attackerIndex()]--;
            remainingDefenderSlots[bestCandidate.defenderIndex()]--;
            choices.add(new RedeclarationChoice(bestEdge, bestCandidate.attackerIndex(), bestCandidate.defenderIndex()));
        }
    }

    void chooseRedeclarations(
            int edgeCount,
            int[] remainingAttackerSlots,
            int[] remainingDefenderSlots,
            double scoreThreshold,
            int horizonRemainingTurns,
            RedeclareCandidateEvaluator evaluator,
            RedeclarationSink sink
    ) {
        int[] attackerSlots = Arrays.copyOf(remainingAttackerSlots, remainingAttackerSlots.length);
        int[] defenderSlots = Arrays.copyOf(remainingDefenderSlots, remainingDefenderSlots.length);
        boolean[] edgeUsed = new boolean[Math.max(1, edgeCount)];
        MutableRedeclareCandidate candidate = new MutableRedeclareCandidate();
        double[] deferredBestByAttacker = new double[Math.max(1, attackerSlots.length)];
        while (true) {
            int bestEdge = -1;
            int bestAttackerIndex = -1;
            int bestDefenderIndex = -1;
            double bestScore = scoreThreshold;
            Arrays.fill(deferredBestByAttacker, 0d);
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                if (edgeUsed[edgeIndex]) {
                    continue;
                }
                evaluator.evaluate(edgeIndex, candidate);
                if (attackerSlots[candidate.attackerIndex] <= 0) {
                    continue;
                }
                if (candidate.activePair) {
                    continue;
                }
                if (!candidate.legal && candidate.blockedTurns > 0
                        && defenderSlots[candidate.defenderIndex] > 0) {
                    double deferredScore = projectedDeferredRedeclareScore(
                            candidate,
                            attackerSlots[candidate.attackerIndex],
                            horizonRemainingTurns
                    );
                    if (deferredScore > deferredBestByAttacker[candidate.attackerIndex]) {
                        deferredBestByAttacker[candidate.attackerIndex] = deferredScore;
                    }
                }
            }
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                if (edgeUsed[edgeIndex]) {
                    continue;
                }
                evaluator.evaluate(edgeIndex, candidate);
                if (attackerSlots[candidate.attackerIndex] <= 0) {
                    continue;
                }
                if (candidate.activePair || !candidate.legal) {
                    continue;
                }
                if (defenderSlots[candidate.defenderIndex] <= 0) {
                    continue;
                }
                double score = projectedRedeclareScore(candidate, attackerSlots[candidate.attackerIndex]);
                if (score <= deferredBestByAttacker[candidate.attackerIndex]) {
                    continue;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestEdge = edgeIndex;
                    bestAttackerIndex = candidate.attackerIndex;
                    bestDefenderIndex = candidate.defenderIndex;
                }
            }
            if (bestEdge < 0) {
                return;
            }
            edgeUsed[bestEdge] = true;
            attackerSlots[bestAttackerIndex]--;
            defenderSlots[bestDefenderIndex]--;
            sink.accept(bestEdge, bestAttackerIndex, bestDefenderIndex);
        }
    }

    private static double projectedRedeclareScore(RedeclareCandidate candidate, int remainingAttackerSlots) {
        if (!candidate.legal()) {
            return 0d;
        }
        return projectedRedeclareScore(
                candidate.attackerStrength(),
                candidate.defenderStrength(),
                candidate.activityWeight(),
                candidate.baseEdgeScore(),
                remainingAttackerSlots
        );
    }

    static double projectedDeferredRedeclareScore(
            RedeclareCandidate candidate,
            int remainingAttackerSlots,
            int horizonRemainingTurns
    ) {
        if (candidate.blockedTurns() <= 0) {
            return 0d;
        }
        return projectedRedeclareScore(
                candidate.attackerStrength(),
                candidate.defenderStrength(),
                candidate.activityWeight(),
                candidate.baseEdgeScore(),
                remainingAttackerSlots
        ) * StrategicTimingValue.redeclareWaitDiscount(candidate.blockedTurns(), horizonRemainingTurns);
    }

    private static double projectedRedeclareScore(MutableRedeclareCandidate candidate, int remainingAttackerSlots) {
        if (!candidate.legal) {
            return 0d;
        }
        return projectedRedeclareScore(
                candidate.attackerStrength,
                candidate.defenderStrength,
                candidate.activityWeight,
                candidate.baseEdgeScore,
                remainingAttackerSlots
        );
    }

    private static double projectedDeferredRedeclareScore(
            MutableRedeclareCandidate candidate,
            int remainingAttackerSlots,
            int horizonRemainingTurns
    ) {
        if (candidate.blockedTurns <= 0) {
            return 0d;
        }
        return projectedRedeclareScore(
                candidate.attackerStrength,
                candidate.defenderStrength,
                candidate.activityWeight,
                candidate.baseEdgeScore,
                remainingAttackerSlots
        ) * StrategicTimingValue.redeclareWaitDiscount(candidate.blockedTurns, horizonRemainingTurns);
    }

    static double projectedRedeclareScore(
            double attackerStrength,
            double defenderStrength,
            double activityWeight,
            double baseEdgeScore,
            int remainingAttackerSlots
    ) {
        if (!(attackerStrength > 0d) || !(defenderStrength > 0d)) {
            return 0d;
        }
        double activity = Math.max(0d, Math.min(1d, activityWeight));
        double strengthRatio = attackerStrength / Math.max(1d, defenderStrength);
        double slotPressure = 1d / Math.max(1, remainingAttackerSlots);
        return Math.max(0d, baseEdgeScore)
                * activity
                * Math.min(MAX_STRENGTH_RATIO, strengthRatio)
                * slotPressure;
    }
}