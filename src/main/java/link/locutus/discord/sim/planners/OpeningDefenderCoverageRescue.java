package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.planners.compile.OpeningEvaluationScenario;

final class OpeningDefenderCoverageRescue {
    private OpeningDefenderCoverageRescue() {
    }

    static void emit(
            OpeningEvaluationScenario scenario,
            OpeningEvaluator.TopKEdgeCollector[] defenderCoverageCollectors,
            int[] defenderCaps,
            int coverageTarget,
            CandidateEdgeTable out,
            long[] emittedPairWordsByAttacker,
            int emittedWordsPerAttacker,
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
                            scenario,
                            collector,
                            collector.sortedIndexAt(order),
                            out,
                            emittedPairWordsByAttacker,
                            emittedWordsPerAttacker,
                            defenderCoverageCounts
                    );
                }
            }
            PlannerProfiler.addCounter(PlannerProfiler.Scope.DEFENDER_COVERAGE_RESCUE, "rescuedEdges", out.edgeCount() - beforeEdgeCount);
        }
    }

    static boolean emitSelectedEdge(
            OpeningEvaluationScenario scenario,
            OpeningEvaluator.TopKEdgeCollector collector,
            int selectedIndex,
            CandidateEdgeTable out,
            long[] emittedPairWordsByAttacker,
            int emittedWordsPerAttacker,
            int[] defenderCoverageCounts
    ) {
        return emitEdge(
            scenario,
            collector.attackerIndexAt(selectedIndex),
            collector.defenderIndexAt(selectedIndex),
            collector.preferredWarTypeIdAt(selectedIndex),
            collector.bestAttackTypeIdAt(selectedIndex),
            collector.scoreAt(selectedIndex),
            collector.immediateHarmAt(selectedIndex),
            collector.selfExposureAt(selectedIndex),
            collector.resourceSwingAt(selectedIndex),
            collector.controlLeverageAt(selectedIndex),
            collector.futureWarLeverageAt(selectedIndex),
            out,
            emittedPairWordsByAttacker,
            emittedWordsPerAttacker,
            defenderCoverageCounts
        );
        }

    static boolean emitSelectedEdge(
            OpeningEvaluator.CoveragePriorityCollector collector,
            int selectedIndex,
            CandidateEdgeTable out,
            long[][] emittedPairWordsByAttacker,
            int[] defenderCoverageCounts
        ) {
        return emitEdge(
            collector.attackerIndexAt(selectedIndex),
            collector.defenderIndexAt(selectedIndex),
            collector.preferredWarTypeIdAt(selectedIndex),
            collector.bestAttackTypeIdAt(selectedIndex),
            collector.scoreAt(selectedIndex),
            collector.counterRiskAt(selectedIndex),
            collector.immediateHarmAt(selectedIndex),
            collector.selfExposureAt(selectedIndex),
            collector.resourceSwingAt(selectedIndex),
            collector.controlLeverageAt(selectedIndex),
            collector.futureWarLeverageAt(selectedIndex),
            out,
            flattenWords(emittedPairWordsByAttacker),
            emittedPairWordsByAttacker.length == 0 ? 0 : emittedPairWordsByAttacker[0].length,
            defenderCoverageCounts
        );
        }

        static boolean emitSelectedEdge(
            OpeningEvaluationScenario scenario,
            OpeningEvaluator.CoveragePriorityCollector collector,
            int selectedIndex,
            CandidateEdgeTable out,
            long[] emittedPairWordsByAttacker,
            int emittedWordsPerAttacker,
            int[] defenderCoverageCounts
    ) {
        return emitEdge(
            scenario,
                collector.attackerIndexAt(selectedIndex),
                collector.defenderIndexAt(selectedIndex),
                collector.preferredWarTypeIdAt(selectedIndex),
                collector.bestAttackTypeIdAt(selectedIndex),
                collector.scoreAt(selectedIndex),
                collector.immediateHarmAt(selectedIndex),
                collector.selfExposureAt(selectedIndex),
                collector.resourceSwingAt(selectedIndex),
                collector.controlLeverageAt(selectedIndex),
                collector.futureWarLeverageAt(selectedIndex),
                out,
                emittedPairWordsByAttacker,
                emittedWordsPerAttacker,
                defenderCoverageCounts
        );
    }

            static boolean emitSelectedEdge(
                OpeningEvaluationScenario scenario,
                OpeningEvaluator.CoveragePriorityCollector collector,
                int selectedIndex,
                CandidateEdgeTable out,
                long[][] emittedPairWordsByAttacker,
                int[] defenderCoverageCounts
            ) {
            return emitSelectedEdge(
                scenario,
                collector,
                selectedIndex,
                out,
                flattenWords(emittedPairWordsByAttacker),
                emittedPairWordsByAttacker.length == 0 ? 0 : emittedPairWordsByAttacker[0].length,
                defenderCoverageCounts
            );
            }

        static boolean emitEdge(
            OpeningEvaluationScenario scenario,
            int attackerIndex,
            int defenderIndex,
            byte preferredWarTypeId,
            byte bestAttackTypeId,
            float score,
            float immediateHarm,
            float selfExposure,
            float resourceSwing,
            float controlLeverage,
            float futureWarLeverage,
            CandidateEdgeTable out,
            long[] emittedPairWordsByAttacker,
            int emittedWordsPerAttacker,
            int[] defenderCoverageCounts
        ) {
            float counterRisk = (float) scenario.estimateAllianceCounterRisk(attackerIndex, defenderIndex);
            return emitEdge(
                attackerIndex,
                defenderIndex,
                preferredWarTypeId,
                bestAttackTypeId,
                score,
                counterRisk,
                immediateHarm,
                selfExposure,
                resourceSwing,
                controlLeverage,
                futureWarLeverage,
                out,
                emittedPairWordsByAttacker,
                emittedWordsPerAttacker,
                defenderCoverageCounts
            );
            }

            private static boolean emitEdge(
                int attackerIndex,
                int defenderIndex,
                byte preferredWarTypeId,
                byte bestAttackTypeId,
                float score,
                float counterRisk,
                float immediateHarm,
                float selfExposure,
                float resourceSwing,
                float controlLeverage,
                float futureWarLeverage,
                CandidateEdgeTable out,
                long[] emittedPairWordsByAttacker,
                int emittedWordsPerAttacker,
                int[] defenderCoverageCounts
            ) {
        if (!markEdgeEmitted(emittedPairWordsByAttacker, emittedWordsPerAttacker, attackerIndex, defenderIndex)) {
            return false;
        }
        out.add(
            attackerIndex,
            defenderIndex,
            preferredWarTypeId,
            bestAttackTypeId,
            score,
            counterRisk,
            immediateHarm,
            selfExposure,
            resourceSwing,
            controlLeverage,
            futureWarLeverage
        );
        defenderCoverageCounts[defenderIndex]++;
        return true;
    }

    private static boolean markEdgeEmitted(long[] emittedPairWordsByAttacker, int emittedWordsPerAttacker, int attackerIndex, int defenderIndex) {
        int base = attackerIndex * emittedWordsPerAttacker;
        int wordIndex = defenderIndex / Long.SIZE;
        long mask = 1L << (defenderIndex % Long.SIZE);
        int flatIndex = base + wordIndex;
        if ((emittedPairWordsByAttacker[flatIndex] & mask) != 0L) {
            return false;
        }
        emittedPairWordsByAttacker[flatIndex] |= mask;
        return true;
    }

    private static long[] flattenWords(long[][] emittedPairWordsByAttacker) {
        if (emittedPairWordsByAttacker.length == 0) {
            return new long[0];
        }
        int wordsPerAttacker = emittedPairWordsByAttacker[0].length;
        long[] flat = new long[emittedPairWordsByAttacker.length * wordsPerAttacker];
        for (int attackerIndex = 0; attackerIndex < emittedPairWordsByAttacker.length; attackerIndex++) {
            System.arraycopy(
                    emittedPairWordsByAttacker[attackerIndex],
                    0,
                    flat,
                    attackerIndex * wordsPerAttacker,
                    wordsPerAttacker
            );
        }
        return flat;
    }
}