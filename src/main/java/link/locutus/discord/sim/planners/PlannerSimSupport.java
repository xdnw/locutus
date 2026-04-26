package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;

final class PlannerSimSupport {
    private static final long SAMPLE_SEED_GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private PlannerSimSupport() {
    }

    static ScoreSummary summarizeScores(SimTuning tuning, IntToDoubleFunction scorer) {
        if (tuning.stateResolutionMode() != link.locutus.discord.sim.combat.ResolutionMode.STOCHASTIC) {
            return ScoreSummary.identical(scorer.applyAsDouble(0));
        }
        List<Double> samples = new ArrayList<>(tuning.stochasticSampleCount());
        for (int sampleIndex = 0; sampleIndex < tuning.stochasticSampleCount(); sampleIndex++) {
            samples.add(scorer.applyAsDouble(sampleIndex));
        }
        return ScoreSummary.fromSamples(samples);
    }

    static SimTuning sampleTuning(SimTuning tuning, int sampleIndex) {
        return tuning.withStochasticSeed(tuning.stochasticSeed() + SAMPLE_SEED_GOLDEN_GAMMA * sampleIndex);
    }

    static double scoreAssignment(
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            Map<Integer, List<Integer>> assignment,
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
    ) {
        if (assignment.isEmpty()) {
            return 0.0;
        }
        return PlannerConflictExecutor.scoreAssignment(tuning, overrides, objective, assignment, attackers, defenders);
    }

    static Map<Integer, Float> compileActivityWeights(
            SnapshotActivityProvider activityProvider,
            int currentTurn,
            OverrideSet overrides,
            Collection<DBNationSnapshot> snapshots
    ) {
        Map<Integer, Float> activityWeights = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            float weight = switch (overrides.activeOverride(snapshot.nationId())) {
                case TRUE -> 1.0f;
                case FALSE -> 0.0f;
                case AUTO -> {
                    double base = activityProvider.activityForSnapshot(snapshot, currentTurn);
                    // Enforce planner compile contract: activity weights are always [0, 1].
                    yield (float) Math.max(0.0, Math.min(1.0, base));
                }
            };
            activityWeights.put(snapshot.nationId(), weight);
        }
        return activityWeights;
    }

    static void collectResetDiagnostics(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders,
            List<PlannerDiagnostic> diagnostics
    ) {
        for (DBNationSnapshot snap : attackers) {
            if (snap.resetHourUtcFallback()) {
                diagnostics.add(PlannerDiagnostic.resetHourFallback(
                        PlannerDiagnostic.NationRole.ATTACKER,
                        snap.nationId()
                ));
            }
        }
        for (DBNationSnapshot snap : defenders) {
            if (snap.resetHourUtcFallback()) {
                diagnostics.add(PlannerDiagnostic.resetHourFallback(
                        PlannerDiagnostic.NationRole.DEFENDER,
                        snap.nationId()
                ));
            }
        }
    }
}