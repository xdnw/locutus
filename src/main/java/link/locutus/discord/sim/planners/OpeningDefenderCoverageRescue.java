package link.locutus.discord.sim.planners;

final class OpeningDefenderCoverageRescue {
    private OpeningDefenderCoverageRescue() {
    }

    static void emit(
            OpeningEvaluator.TopKEdgeCollector[] defenderCoverageCollectors,
            int[] defenderCaps,
            int coverageTarget,
            CandidateEdgeTable out,
            long[][] emittedPairWordsByAttacker,
            int[] defenderCoverageCounts
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.DEFENDER_COVERAGE_RESCUE)) {
            if (coverageTarget <= 0) {
                return;
            }
            int beforeEdgeCount = out.edgeCount();
            for (int defenderIndex = 0; defenderIndex < defenderCoverageCollectors.length; defenderIndex++) {
                if (defenderCaps[defenderIndex] <= 0 || defenderCoverageCounts[defenderIndex] >= coverageTarget) {
                    continue;
                }
                OpeningEvaluator.TopKEdgeCollector collector = defenderCoverageCollectors[defenderIndex];
                if (collector == null || collector.size() == 0) {
                    continue;
                }
                collector.sortSelectedDescending();
                for (int order = 0; order < collector.size() && defenderCoverageCounts[defenderIndex] < coverageTarget; order++) {
                    emitSelectedEdge(
                            collector,
                            collector.sortedIndexAt(order),
                            out,
                            emittedPairWordsByAttacker,
                            defenderCoverageCounts
                    );
                }
            }
            PlannerProfiler.addCounter(PlannerProfiler.Scope.DEFENDER_COVERAGE_RESCUE, "rescuedEdges", out.edgeCount() - beforeEdgeCount);
        }
    }

    static void emitSelectedEdge(
            OpeningEvaluator.TopKEdgeCollector collector,
            int selectedIndex,
            CandidateEdgeTable out,
            long[][] emittedPairWordsByAttacker,
            int[] defenderCoverageCounts
    ) {
        int attackerIndex = collector.attackerIndexAt(selectedIndex);
        int defenderIndex = collector.defenderIndexAt(selectedIndex);
        if (!markEdgeEmitted(emittedPairWordsByAttacker, attackerIndex, defenderIndex)) {
            return;
        }
        out.add(
                attackerIndex,
                defenderIndex,
                collector.preferredWarTypeIdAt(selectedIndex),
                collector.bestAttackTypeIdAt(selectedIndex),
                collector.scoreAt(selectedIndex),
                collector.counterRiskAt(selectedIndex),
                collector.immediateHarmAt(selectedIndex),
                collector.selfExposureAt(selectedIndex),
                collector.resourceSwingAt(selectedIndex),
                collector.controlLeverageAt(selectedIndex),
                collector.futureWarLeverageAt(selectedIndex)
        );
        defenderCoverageCounts[defenderIndex]++;
    }

    private static boolean markEdgeEmitted(long[][] emittedPairWordsByAttacker, int attackerIndex, int defenderIndex) {
        long[] words = emittedPairWordsByAttacker[attackerIndex];
        int wordIndex = defenderIndex / Long.SIZE;
        long mask = 1L << (defenderIndex % Long.SIZE);
        if ((words[wordIndex] & mask) != 0L) {
            return false;
        }
        words[wordIndex] |= mask;
        return true;
    }
}