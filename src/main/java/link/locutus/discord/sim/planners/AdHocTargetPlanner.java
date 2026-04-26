package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.ScenarioActionPolicy;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Single-attacker target ranking over a short deterministic horizon.
 */
public final class AdHocTargetPlanner {
    private static final ScenarioCompiler SCENARIO_COMPILER = new ScenarioCompiler();

    private final SimTuning tuning;
    private final TreatyProvider treatyProvider;
    private final OverrideSet overrides;
    private final TeamScoreObjective objective;
    private final SnapshotActivityProvider snapshotActivityProvider;

    public AdHocTargetPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, TeamScoreObjective objective) {
        this(tuning, treatyProvider, overrides, objective, SnapshotActivityProvider.BASELINE);
    }

    public AdHocTargetPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, TeamScoreObjective objective, SnapshotActivityProvider activityProvider) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.treatyProvider = Objects.requireNonNull(treatyProvider, "treatyProvider");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.objective = Objects.requireNonNull(objective, "objective");
        this.snapshotActivityProvider = Objects.requireNonNull(activityProvider, "activityProvider");
    }

    public AdHocTargetPlanner(SimTuning tuning) {
        this(tuning, TreatyProvider.NONE, OverrideSet.EMPTY, new DamageObjective(), SnapshotActivityProvider.BASELINE);
    }

    public AdHocPlan rankTargets(
            DBNationSnapshot attacker,
            Collection<DBNationSnapshot> defenders,
            int horizonTurns,
            int limit
    ) {
        return rankTargets(attacker, defenders, horizonTurns, limit, 0, AdHocSimulationOptions.DEFAULT);
    }

    public AdHocPlan rankTargets(
            DBNationSnapshot attacker,
            Collection<DBNationSnapshot> defenders,
            int horizonTurns,
            int limit,
            AdHocSimulationOptions simulationOptions
    ) {
        return rankTargets(attacker, defenders, horizonTurns, limit, 0, simulationOptions);
    }

    public AdHocPlan rankTargets(
            DBNationSnapshot attacker,
            Collection<DBNationSnapshot> defenders,
            int horizonTurns,
            int limit,
            int currentTurn,
            AdHocSimulationOptions simulationOptions
    ) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(defenders, "defenders");
        AdHocSimulationOptions options = simulationOptions == null ? AdHocSimulationOptions.DEFAULT : simulationOptions;
        ScenarioActionPolicy scenarioActionPolicy = options.scenarioActionPolicy();
        PlannerExactValidatorScripts validatorScripts = options.effectiveValidatorScripts();
        if (horizonTurns <= 0) {
            throw new IllegalArgumentException("horizonTurns must be > 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        List<DBNationSnapshot> defenderList = List.copyOf(defenders);
        Map<Integer, DBNationSnapshot> snapshotById = snapshotById(attacker, defenderList);
        boolean runtimePreviewRequired = !PlannerExactValidatorScripts.DEFAULT.equals(validatorScripts)
            || hasPolicyRestrictions(scenarioActionPolicy, attacker, defenderList);
        Map<Integer, Float> activityWeights = PlannerSimSupport.compileActivityWeights(
                snapshotActivityProvider.withWartimeUplift(tuning.wartimeActivityUplift()),
                currentTurn,
                overrides,
                combinedSnapshots(attacker, defenderList)
        );
        CompiledScenario compiledScenario = SCENARIO_COMPILER.compile(
                List.of(attacker),
                defenderList,
                overrides,
                treatyProvider,
                activityWeights
        );
        List<AdHocTargetRecommendation> recommendations = new ArrayList<>();
        int attackerIndex = 0;
        DBNationSnapshot compiledAttacker = compiledScenario.attacker(attackerIndex);
        float attackerActivityWeight = compiledScenario.attackerActivityWeight(attackerIndex);
        OpeningEvaluator.CandidateOpeningEvaluator openingEvaluator = new OpeningEvaluator.CandidateOpeningEvaluator(
            objective,
            OpeningEvaluator.actionBudgetForHorizon(horizonTurns)
        );

        List<Integer> defenderIndexes = new ArrayList<>();
        compiledScenario.forEachDefenderIndexInRange(attackerIndex, defenderIndexes::add);
        for (int defenderIndex : defenderIndexes) {
            DBNationSnapshot defender = compiledScenario.defender(defenderIndex);
            if (compiledScenario.isTreated(attackerIndex, defenderIndex)) {
                continue;
            }
            if (compiledScenario.hasActivePairConflict(attackerIndex, defenderIndex)) {
                continue;
            }
            if (compiledScenario.defenderFreeDefSlots(defenderIndex) <= 0) {
                continue;
            }
            OpeningEvaluator.EvaluatedEdge evaluatedEdge = openingEvaluator.evaluate(compiledAttacker, defender);
            if (!Float.isFinite(evaluatedEdge.score())) {
                continue;
            }
            double weightedScore = evaluatedEdge.score() * attackerActivityWeight;
            ScoreSummary scoreSummary = ScoreSummary.identical(weightedScore);
            double score = scoreSummary.mean();
            if (!Double.isFinite(score)) {
                continue;
            }
            recommendations.add(new AdHocTargetRecommendation(
                    attacker.nationId(),
                    defender.nationId(),
                    score,
                    compiledScenario.estimateAllianceCounterRisk(attackerIndex, defenderIndex),
                    horizonTurns,
                    scoreSummary
            ));
        }

        recommendations.sort(Comparator.comparingDouble(AdHocTargetRecommendation::objectiveScore).reversed());
        recommendations = retainTopRecommendations(recommendations, limit);

        List<PlannerDiagnostic> diagnostics = new ArrayList<>();
        boolean exactValidationDefault = options.usesDefaultExactValidation();
        boolean runtimePreviewApplied = false;
        if (runtimePreviewRequired && !recommendations.isEmpty()) {
            runtimePreviewApplied = true;
            recommendations = runtimePreviewRecommendations(
                    attacker,
                    horizonTurns,
                    validatorScripts,
                    scenarioActionPolicy,
                    recommendations,
                    attackerActivityWeight,
                    snapshotById
            );
        }
        PlannerSimSupport.collectResetDiagnostics(List.of(attacker), defenderList, diagnostics);
        return new AdHocPlan(
                attacker.nationId(),
                horizonTurns,
                recommendations,
                diagnostics,
                new AdHocPlanMetadata(exactValidationDefault, runtimePreviewApplied)
        );
    }

    private static List<AdHocTargetRecommendation> retainTopRecommendations(
            List<AdHocTargetRecommendation> recommendations,
            int limit
    ) {
        if (recommendations.size() <= limit) {
            return recommendations;
        }
        return new ArrayList<>(recommendations.subList(0, limit));
    }

    private List<AdHocTargetRecommendation> runtimePreviewRecommendations(
            DBNationSnapshot attacker,
            int horizonTurns,
            PlannerExactValidatorScripts validatorScripts,
            ScenarioActionPolicy scenarioActionPolicy,
            List<AdHocTargetRecommendation> retainedRecommendations,
            float attackerActivityWeight,
            Map<Integer, DBNationSnapshot> snapshotById
    ) {
        ScenarioActionPolicy.NationActionPolicy attackerPolicy = resolvePolicy(
            scenarioActionPolicy,
            attacker
        );
        List<AdHocTargetRecommendation> previewed = new ArrayList<>(retainedRecommendations.size());
        for (AdHocTargetRecommendation recommendation : retainedRecommendations) {
            DBNationSnapshot defender = snapshotById.get(recommendation.defenderId());
            if (defender == null) {
            continue;
            }
            ScenarioActionPolicy.NationActionPolicy defenderPolicy = resolvePolicy(
                scenarioActionPolicy,
                defender
            );
            PlannerExactValidatorScripts scripts = validatorScripts.constrainedByPolicies(
                attackerPolicy,
                defenderPolicy
            );
            ScoreSummary previewSummary = PlannerSimSupport.summarizeScores(tuning, sampleIndex -> {
                SimTuning scoringTuning = tuning.stateResolutionMode() == ResolutionMode.STOCHASTIC
                        ? PlannerSimSupport.sampleTuning(tuning, sampleIndex)
                        : tuning;
                double rawScore = PlannerConflictExecutor.evaluateDeclaredWar(
                    scoringTuning,
                    overrides,
                    objective,
                    attacker,
                    defender,
                    horizonTurns,
                    scripts
                );
                return rawScore * attackerActivityWeight;
            });
            double previewScore = previewSummary.mean();
            if (!Double.isFinite(previewScore)) {
                continue;
            }
            previewed.add(new AdHocTargetRecommendation(
                    recommendation.attackerId(),
                    recommendation.defenderId(),
                    previewScore,
                    recommendation.counterRisk(),
                    recommendation.horizonTurns(),
                    previewSummary
            ));
        }
        previewed.sort(Comparator.comparingDouble(AdHocTargetRecommendation::objectiveScore).reversed());
        return previewed;
    }

    private static ScenarioActionPolicy.NationActionPolicy resolvePolicy(
            ScenarioActionPolicy scenarioActionPolicy,
            DBNationSnapshot nation
    ) {
        if (scenarioActionPolicy == ScenarioActionPolicy.ALLOW_ALL) {
            return ScenarioActionPolicy.NationActionPolicy.allowAll();
        }
        return ScenarioActionPolicy.resolveSnapshot(
                scenarioActionPolicy,
                nation.nationId(),
                nation.teamId()
        );
    }

    private static boolean hasPolicyRestrictions(
            ScenarioActionPolicy scenarioActionPolicy,
            DBNationSnapshot attacker,
            List<DBNationSnapshot> defenders
    ) {
        if (scenarioActionPolicy == ScenarioActionPolicy.ALLOW_ALL) {
            return false;
        }
        ScenarioActionPolicy.NationActionPolicy allowAll = ScenarioActionPolicy.NationActionPolicy.allowAll();
        if (!allowAll.equals(resolvePolicy(scenarioActionPolicy, attacker))) {
            return true;
        }
        for (DBNationSnapshot defender : defenders) {
            if (!allowAll.equals(resolvePolicy(scenarioActionPolicy, defender))) {
                return true;
            }
        }
        return false;
    }

    private static List<DBNationSnapshot> combinedSnapshots(DBNationSnapshot attacker, List<DBNationSnapshot> defenders) {
        List<DBNationSnapshot> combined = new ArrayList<>(defenders.size() + 1);
        combined.add(attacker);
        combined.addAll(defenders);
        return combined;
    }

    private static Map<Integer, DBNationSnapshot> snapshotById(DBNationSnapshot attacker, List<DBNationSnapshot> defenders) {
        Map<Integer, DBNationSnapshot> byId = new LinkedHashMap<>(defenders.size() + 1);
        byId.put(attacker.nationId(), attacker);
        for (DBNationSnapshot defender : defenders) {
            byId.put(defender.nationId(), defender);
        }
        return byId;
    }
}