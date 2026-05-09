package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Evaluates candidate (attacker, defender) pairs and populates a {@link CandidateEdgeTable}.
 *
 * <p>Pruning signal is kernel-derived: for each attack type the attacker can afford, the
 * odds model is asked for {@code P(>= MODERATE_SUCCESS)} and the max is taken as the edge's
 * viability probe. Edges whose conventional probe is below the objective's admission floor are
 * pruned unless the objective explicitly opts into a low-probe specialist lane, in which case
 * legal missile/nuke admission is driven by a shared combat-derived specialist signal instead of
 * stocked-unit fallback.
 *
 * <p>This replaces the earlier <code>ViabilityBand</code> taxonomy whose thresholds
 * (air-ratio cutoffs, minimum missile count, naval ratio) were heuristic-over-kernel and
 * would silently diverge from game rules as they change.
 *
 * <p>Invariants:
 * <ul>
 *   <li>The hot collector reuses planner-owned scratch/context for admission and rollout.</li>
 *   <li>EV fractional outputs are used only for ranking; discrete state transitions are not
 *       driven by EV outputs (enforced by {@link PlannerConflictExecutor}).</li>
 * </ul>
 */
final class OpeningEvaluator {
    private static final int DEFAULT_ACTION_BUDGET = 3;
    private static final EvaluatedEdge REJECTED_EDGE = new EvaluatedEdge(
            Float.NEGATIVE_INFINITY,
            (byte) -1,
            (byte) -1,
            0f,
            0f,
            0f,
            0f,
            0f
    );
    static final AttackType[] OPENING_ATTACK_TYPES = {
        AttackType.GROUND,
        AttackType.AIRSTRIKE_INFRA,
        AttackType.AIRSTRIKE_SOLDIER,
        AttackType.AIRSTRIKE_TANK,
        AttackType.AIRSTRIKE_MONEY,
        AttackType.AIRSTRIKE_SHIP,
        AttackType.AIRSTRIKE_AIRCRAFT,
        AttackType.NAVAL,
        AttackType.NAVAL_INFRA,
        AttackType.NAVAL_AIR,
        AttackType.NAVAL_GROUND,
        AttackType.MISSILE,
        AttackType.NUKE
    };
    static final WarType[] OPENING_WAR_TYPES = {
        WarType.ATT,
        WarType.ORD,
        WarType.RAID
    };

    static record OpeningBaseline(
            DBNationSnapshot attackerSnapshot,
            DBNationSnapshot defenderSnapshot,
            double attackerGround,
            double defenderGround,
            double attackerAir,
            double defenderAir,
            double attackerNaval,
            double defenderNaval,
            double targetPressure
    ) {
        static OpeningBaseline from(DBNationSnapshot attacker, DBNationSnapshot defender) {
            double attackerGround = OpeningMetricSummary.groundStrength(
                    attacker.unit(MilitaryUnit.SOLDIER),
                    attacker.unit(MilitaryUnit.TANK),
                    false
            );
            double defenderGround = OpeningMetricSummary.groundStrength(
                    defender.unit(MilitaryUnit.SOLDIER),
                    defender.unit(MilitaryUnit.TANK),
                    false
            );
            double attackerAir = attacker.unit(MilitaryUnit.AIRCRAFT);
            double defenderAir = defender.unit(MilitaryUnit.AIRCRAFT);
            double attackerNaval = attacker.unit(MilitaryUnit.SHIP);
            double defenderNaval = defender.unit(MilitaryUnit.SHIP);
            return new OpeningBaseline(
                    attacker,
                    defender,
                    attackerGround,
                    defenderGround,
                    attackerAir,
                    defenderAir,
                    attackerNaval,
                    defenderNaval,
                    OpeningMetricSummary.defenderControlPressure(defender)
            );
        }
    }

    private OpeningEvaluator() {
    }

    static EvaluatedEdge evaluateOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            CandidateEdgeComponentPolicy componentPolicy
    ) {
        OpeningRolloutSearch evaluator = new OpeningRolloutSearch(DEFAULT_ACTION_BUDGET);
        EdgeEvaluation evaluation = new EdgeEvaluation();
        OpeningCandidateAdmission candidateAdmission = new OpeningCandidateAdmission(resolveAdmissionPolicy(objective));
        if (!candidateAdmission.admit(attacker, defender)) {
            if (candidateAdmission.admitPositiveOpeningBaseline()
                    && evaluatePositiveBaselineOpening(attacker, defender, objective, null, evaluation)) {
                OpeningEdgeEvaluationWriter.retainComponents(evaluation, componentPolicy);
                return new EvaluatedEdge(
                        evaluation.score(),
                        evaluation.preferredWarTypeId(),
                        evaluation.firstAttackTypeId(),
                        evaluation.immediateHarm(),
                        evaluation.selfExposure(),
                        evaluation.resourceSwing(),
                        evaluation.controlLeverage(),
                        evaluation.futureWarLeverage()
                );
            }
            return REJECTED_EDGE;
        }
        return evaluateAdmittedOpening(
            attacker,
            defender,
            objective,
            componentPolicy,
            evaluator,
            evaluation,
            actionBudgetForProbe(candidateAdmission.probe(), evaluator.maxActionBudget())
        );
    }

    static EvaluatedEdge evaluateOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            CandidateEdgeComponentPolicy componentPolicy,
            int actionBudget
    ) {
        OpeningRolloutSearch evaluator = new OpeningRolloutSearch(actionBudget);
        EdgeEvaluation evaluation = new EdgeEvaluation();
        return evaluateAdmittedOpening(
            attacker,
            defender,
            objective,
            componentPolicy,
            evaluator,
            evaluation,
            evaluator.maxActionBudget()
        );
    }

    record EvaluatedEdge(
            float score,
            byte preferredWarTypeId,
            byte firstAttackTypeId,
            float immediateHarm,
            float selfExposure,
            float resourceSwing,
            float controlLeverage,
            float futureWarLeverage
    ) {
    }

    private enum EvaluationEffortTier {
        SINGLE_STEP(1),
        SHORT_ROLLOUT(2),
        DEEP_OPENING(3);

        private final int actionBudget;

        EvaluationEffortTier(int actionBudget) {
            this.actionBudget = actionBudget;
        }

        private int clampedBudget(int maxActionBudget) {
            return Math.max(1, Math.min(actionBudget, maxActionBudget));
        }

        private static EvaluationEffortTier fromProbe(float probe) {
            if (probe >= (2f / 3f)) {
                return DEEP_OPENING;
            }
            if (probe >= (1f / 3f)) {
                return SHORT_ROLLOUT;
            }
            return SINGLE_STEP;
        }
    }

    static int actionBudgetForHorizon(int horizonTurns) {
        return Math.max(1, Math.min(DEFAULT_ACTION_BUDGET, horizonTurns));
    }

    static int actionBudgetForProbe(float probe) {
        return actionBudgetForProbe(probe, DEFAULT_ACTION_BUDGET);
    }

    static int actionBudgetForProbe(float probe, int maxActionBudget) {
        return EvaluationEffortTier.fromProbe(probe).clampedBudget(maxActionBudget);
    }

    static final class CandidateOpeningEvaluator {
        private final StrategicObjective objective;
        private final CandidateEdgeComponentPolicy componentPolicy;
        private final OpeningCandidateAdmission candidateAdmission;
        private final OpeningRolloutSearch rolloutEdgeEvaluator;
        private final EdgeEvaluation edgeEvaluation = new EdgeEvaluation();

        CandidateOpeningEvaluator(StrategicObjective objective, int actionBudget) {
            this.objective = Objects.requireNonNull(objective, "objective");
            this.componentPolicy = resolveComponentPolicy(objective);
            this.candidateAdmission = new OpeningCandidateAdmission(resolveAdmissionPolicy(objective));
            this.rolloutEdgeEvaluator = new OpeningRolloutSearch(actionBudget);
        }

        EvaluatedEdge evaluate(DBNationSnapshot attacker, DBNationSnapshot defender) {
            if (!candidateAdmission.admit(attacker, defender)) {
                return REJECTED_EDGE;
            }
            return evaluateAdmittedOpening(
                    attacker,
                    defender,
                    objective,
                    componentPolicy,
                    rolloutEdgeEvaluator,
                    edgeEvaluation,
                    actionBudgetForProbe(candidateAdmission.probe(), rolloutEdgeEvaluator.maxActionBudget())
            );
        }
    }

    /**
     * Evaluates all legal (attacker, defender) pairs in the compiled scenario and appends them
     * to {@code out}.
     *
         * <p>For each attacker, the top {@code candidatesPerAttacker} edges are retained after
         * kernel-EV pruning and score ranking. A bounded defender-coverage pass then rescues the
         * best admitted edges for defenders that would otherwise be orphaned by that per-attacker
         * truncation.
     */
    static void evaluate(
            CompiledScenario scenario,
            SimTuning tuning,
            OverrideSet overrides,
            StrategicObjective objective,
            int[] attackerCaps,
            int[] defenderCaps,
            CandidateEdgeTable out
    ) {
        evaluate(scenario, tuning, overrides, objective, null, attackerCaps, defenderCaps, out);
        }

        static void evaluate(
            CompiledScenario scenario,
            SimTuning tuning,
            OverrideSet overrides,
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            int[] attackerCaps,
            int[] defenderCaps,
            CandidateEdgeTable out
        ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.OPENING_EVALUATE)) {
            PlannerProfiler.addCounter(PlannerProfiler.Scope.OPENING_EVALUATE, "attackers", scenario.attackerCount());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.OPENING_EVALUATE, "defenders", scenario.defenderCount());
            CandidateEdgeComponentPolicy componentPolicy = objective.candidateEdgeComponentPolicy();
            out.configureComponentRetention(componentPolicy);
            CandidateEdgeAdmissionPolicy resolvedAdmissionPolicy = resolveAdmissionPolicy(objective, openingSettings);
            int candidatesPerAttacker = tuning.candidatesPerAttacker();
            int maxCandidatesPerAttacker = maxCandidatesPerAttacker(attackerCaps, candidatesPerAttacker);
            int defenderCoverageTarget = defenderCoverageCapacity(maxCandidatesPerAttacker);
            float[] defenderCoveragePriorities = new float[scenario.defenderCount()];
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                defenderCoveragePriorities[defenderIndex] = (float) OpeningMetricSummary.defenderControlPressure(scenario.defender(defenderIndex));
            }
            int[] defenderCoverageCounts = new int[scenario.defenderCount()];
            long[][] emittedPairWordsByAttacker = new long[scenario.attackerCount()][(scenario.defenderCount() + Long.SIZE - 1) / Long.SIZE];
            TopKEdgeCollector[] defenderCoverageCollectors = new TopKEdgeCollector[scenario.defenderCount()];
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                if (defenderCaps[defenderIndex] > 0) {
                    defenderCoverageCollectors[defenderIndex] = new TopKEdgeCollector(defenderCoverageTarget, componentPolicy);
                }
            }
            DefenderAdmissionCollector collector = new DefenderAdmissionCollector(
                    scenario,
                    objective,
                    openingSettings,
                    resolvedAdmissionPolicy,
                    componentPolicy,
                    attackerCaps,
                    defenderCaps,
                    candidatesPerAttacker,
                    maxCandidatesPerAttacker,
                    defenderCoverageTarget,
                        defenderCoveragePriorities,
                    defenderCoverageCollectors,
                    out,
                    emittedPairWordsByAttacker,
                    defenderCoverageCounts
            );

            for (int ai = 0; ai < scenario.attackerCount(); ai++) {
                if (attackerCaps[ai] <= 0) {
                    continue;
                }
                collector.beginAttacker(ai);
                scenario.forEachDefenderIndexInRange(ai, collector);
                collector.emit();
            }

            OpeningDefenderCoverageRescue.emit(
                    defenderCoverageCollectors,
                    defenderCaps,
                    defenderCoverageTarget,
                    out,
                    emittedPairWordsByAttacker,
                    defenderCoverageCounts
            );
            PlannerProfiler.addCounter(PlannerProfiler.Scope.OPENING_EVALUATE, "candidateEdges", out.edgeCount());
        }
    }

    private static final class DefenderAdmissionCollector implements IntConsumer {
        private final CompiledScenario scenario;
        private final StrategicObjective objective;
        private final SideOpeningSettings openingSettings;
        private final CandidateEdgeComponentPolicy componentPolicy;
        private final CandidateEdgeAdmissionPolicy admissionPolicy;
        private final int[] defenderCaps;
        private final float[] defenderCoveragePriorities;
        private final TopKEdgeCollector topK;
        private final CoveragePriorityCollector coverageSpillovers;
        private final TopKEdgeCollector[] defenderCoverageCollectors;
        private final CandidateEdgeTable out;
        private final long[][] emittedPairWordsByAttacker;
        private final int[] defenderCoverageCounts;
        private int attackerIndex;
        private DBNationSnapshot attacker;
        private final OpeningCandidateAdmission candidateAdmission;
        private final OpeningRolloutSearch rolloutEdgeEvaluator;
        private final EdgeEvaluation edgeEvaluation;

        private DefenderAdmissionCollector(
                CompiledScenario scenario,
                StrategicObjective objective,
            SideOpeningSettings openingSettings,
                CandidateEdgeAdmissionPolicy admissionPolicy,
                CandidateEdgeComponentPolicy componentPolicy,
                int[] attackerCaps,
                int[] defenderCaps,
                int candidatesPerAttacker,
                int maxCandidatesPerAttacker,
                int defenderCoverageTarget,
                float[] defenderCoveragePriorities,
                TopKEdgeCollector[] defenderCoverageCollectors,
                CandidateEdgeTable out,
                long[][] emittedPairWordsByAttacker,
                int[] defenderCoverageCounts
        ) {
            this.scenario = scenario;
            this.objective = objective;
            this.openingSettings = openingSettings;
            this.componentPolicy = componentPolicy == null ? CandidateEdgeComponentPolicy.none() : componentPolicy;
            this.admissionPolicy = admissionPolicy;
            this.defenderCaps = defenderCaps;
            this.defenderCoverageTarget = defenderCoverageTarget;
            this.defenderCoveragePriorities = defenderCoveragePriorities;
            this.topK = new TopKEdgeCollector(maxCandidatesPerAttacker, this.componentPolicy);
            this.coverageSpillovers = new CoveragePriorityCollector(coverageSpilloverCapacity(maxCandidatesPerAttacker), this.componentPolicy);
            this.defenderCoverageCollectors = defenderCoverageCollectors;
            this.out = out;
            this.emittedPairWordsByAttacker = emittedPairWordsByAttacker;
            this.defenderCoverageCounts = defenderCoverageCounts;
            this.candidateAdmission = new OpeningCandidateAdmission(admissionPolicy);
            this.rolloutEdgeEvaluator = new OpeningRolloutSearch(DEFAULT_ACTION_BUDGET);
            this.edgeEvaluation = new EdgeEvaluation();
            this.attackerCaps = attackerCaps;
            this.candidatesPerAttacker = candidatesPerAttacker;
        }

        private final int[] attackerCaps;
        private final int candidatesPerAttacker;
        private final int defenderCoverageTarget;

        private void beginAttacker(int attackerIndex) {
            this.attackerIndex = attackerIndex;
            this.attacker = scenario.attacker(attackerIndex);
            this.topK.clear();
            this.coverageSpillovers.clear();
            this.topK.setActiveLimit(retentionCapacity(attackerCaps[attackerIndex], candidatesPerAttacker));
        }

        @Override
        public void accept(int defenderIndex) {
            if (defenderCaps[defenderIndex] <= 0) {
                return;
            }
            if (scenario.isTreated(attackerIndex, defenderIndex)) {
                return;
            }
            if (scenario.hasActivePairConflict(attackerIndex, defenderIndex)) {
                return;
            }

            DBNationSnapshot defender = scenario.defender(defenderIndex);
            if (!candidateAdmission.admit(attacker, defender)) {
                if (!candidateAdmission.admitPositiveOpeningBaseline()
                        || !evaluatePositiveBaselineOpening(attacker, defender, objective, openingSettings, edgeEvaluation)) {
                    return;
                }
                OpeningEdgeEvaluationWriter.retainComponents(edgeEvaluation, componentPolicy);
                retainEvaluatedEdge(defenderIndex, edgeEvaluation);
                return;
            }
            float probe = candidateAdmission.probe();
            rolloutEdgeEvaluator.evaluate(
                    attacker,
                    defender,
                    objective,
                    openingSettings,
                    actionBudgetForProbe(probe, rolloutEdgeEvaluator.maxActionBudget()),
                    edgeEvaluation
            );
            OpeningEdgeEvaluationWriter.retainComponents(edgeEvaluation, componentPolicy);
            finalizeEdgeSelection(edgeEvaluation, candidateAdmission.preferredWarTypeId(), candidateAdmission.bestAttackTypeId());
            float score = edgeEvaluation.score();
            if (!Float.isFinite(score)) {
                return;
            }
            retainEvaluatedEdge(defenderIndex, edgeEvaluation);
        }

        private void retainEvaluatedEdge(int defenderIndex, EdgeEvaluation edgeEvaluation) {
            float immediateHarm = edgeEvaluation.immediateHarm();
            float selfExposure = edgeEvaluation.selfExposure();
            float resourceSwing = edgeEvaluation.resourceSwing();
            float controlLeverage = edgeEvaluation.controlLeverage();
            float futureWarLeverage = edgeEvaluation.futureWarLeverage();
            float counterRisk = (float) scenario.estimateAllianceCounterRisk(attackerIndex, defenderIndex);
            topK.consider(
                    attackerIndex,
                    defenderIndex,
                    edgeEvaluation.preferredWarTypeId(),
                    edgeEvaluation.firstAttackTypeId(),
                    edgeEvaluation.score(),
                    counterRisk,
                    immediateHarm,
                    selfExposure,
                    resourceSwing,
                    controlLeverage,
                    futureWarLeverage
            );
                    TopKEdgeCollector defenderCoverageCollector = defenderCoverageCollectors[defenderIndex];
                    if (defenderCoverageCollector != null) {
                    defenderCoverageCollector.consider(
                        attackerIndex,
                        defenderIndex,
                        edgeEvaluation.preferredWarTypeId(),
                        edgeEvaluation.firstAttackTypeId(),
                        edgeEvaluation.score(),
                        counterRisk,
                        immediateHarm,
                        selfExposure,
                        resourceSwing,
                        controlLeverage,
                        futureWarLeverage
                    );
                    }
                    coverageSpillovers.consider(
                        defenderCoveragePriorities[defenderIndex],
                        attackerIndex,
                        defenderIndex,
                        edgeEvaluation.preferredWarTypeId(),
                        edgeEvaluation.firstAttackTypeId(),
                        edgeEvaluation.score(),
                        counterRisk,
                        immediateHarm,
                        selfExposure,
                        resourceSwing,
                        controlLeverage,
                        futureWarLeverage
                    );
        }

        private void emit() {
            if (topK.size() == 0) {
                return;
            }
            topK.sortSelectedDescending();
            int emitCount = Math.min(topK.size(), Math.max(1, candidatesPerAttacker));
            for (int order = 0; order < emitCount; order++) {
                OpeningDefenderCoverageRescue.emitSelectedEdge(
                        topK,
                        topK.sortedIndexAt(order),
                        out,
                        emittedPairWordsByAttacker,
                        defenderCoverageCounts
                );
            }
            emitCoverageSpillover();
        }

        private void emitCoverageSpillover() {
            if (coverageSpillovers.size() == 0) {
                return;
            }
            coverageSpillovers.sortSelectedDescending();
            for (int order = 0; order < coverageSpillovers.size(); order++) {
                int candidateIndex = coverageSpillovers.sortedIndexAt(order);
                int defenderIndex = coverageSpillovers.defenderIndexAt(candidateIndex);
                if (defenderCoverageCounts[defenderIndex] >= defenderCoverageTarget) {
                    continue;
                }
                if (OpeningDefenderCoverageRescue.emitSelectedEdge(
                        coverageSpillovers,
                        candidateIndex,
                        out,
                        emittedPairWordsByAttacker,
                        defenderCoverageCounts
                )) {
                    return;
                }
            }
        }
    }

    private static int maxCandidatesPerAttacker(int[] attackerCaps, int baseCandidatesPerAttacker) {
        int max = Math.max(1, baseCandidatesPerAttacker);
        for (int attackerCap : attackerCaps) {
            max = Math.max(max, retentionCapacity(attackerCap, baseCandidatesPerAttacker));
        }
        return max;
    }

    private static int retentionCapacity(int attackerCap, int baseCandidatesPerAttacker) {
        return Math.max(baseCandidatesPerAttacker, Math.max(1, (2 * Math.max(0, attackerCap)) - 2));
    }

    private static int defenderCoverageCapacity(int maxCandidatesPerAttacker) {
        return Math.max(maxCandidatesPerAttacker, maxCandidatesPerAttacker * 2);
    }

    private static int coverageSpilloverCapacity(int maxCandidatesPerAttacker) {
        return Math.max(1, Math.min(4, maxCandidatesPerAttacker));
    }

    private static boolean evaluatePositiveBaselineOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            SideOpeningSettings openingSettings,
            EdgeEvaluation out
    ) {
        out.clear();
        if (attacker.vmTurns() > 0 || defender.vmTurns() > 0 || defender.beigeTurns() > 0) {
            return false;
        }
        OpeningBaseline baseline = OpeningBaseline.from(attacker, defender);
        OpeningMetricVector.Mutable metrics = new OpeningMetricVector.Mutable();
        metrics.set(0d, 0d, 0d, 0d, 0d, baseline.targetPressure());
        byte firstAttackTypeId = firstLegalOpeningAttack(attacker);
        if (firstAttackTypeId < 0) {
            return false;
        }
        float score = (float) objective.scoreOpening(metrics, attacker.teamId());
        if (openingSettings != null) {
            score *= (float) (openingSettings.warTypeWeight(WarType.ORD)
                    * openingSettings.attackTypeWeight(AttackType.values[firstAttackTypeId]));
        }
        if (!Float.isFinite(score) || score <= 0f) {
            return false;
        }
        out.set(score, (byte) WarType.ORD.ordinal(), firstAttackTypeId, 0f, 0f, 0f, 0f, 0f);
        return true;
    }

    private static byte firstLegalOpeningAttack(DBNationSnapshot attacker) {
        MutableNationState attackerState = new MutableNationState();
        attackerState.bind(attacker);
        for (AttackType type : OPENING_ATTACK_TYPES) {
            if (isLegalOpeningAttack(attackerState, SimWar.INITIAL_MAPS, type)) {
                return (byte) type.ordinal();
            }
        }
        return (byte) -1;
    }

    static boolean admitCandidate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            CandidateEdgeAdmissionPolicy admissionPolicy,
            ProbeResult conventionalProbeResult,
            SpecialistProbeResult specialistProbeResult,
            ViabilityProbeEvaluator viabilityProbeEvaluator,
            SpecialistProbeEvaluator specialistProbeEvaluator
    ) {
        if (attacker.vmTurns() > 0 || defender.vmTurns() > 0 || defender.beigeTurns() > 0) {
            return false;
        }
        double minimumViabilityProbe = admissionPolicy.minimumViabilityProbe();
        viabilityProbeEvaluator.evaluate(attacker, defender, conventionalProbeResult);
        if (conventionalProbeResult.probe() >= minimumViabilityProbe) {
            return true;
        }
        if (!admissionPolicy.allowLegalSpecialistFallback()) {
            return false;
        }
        specialistProbeEvaluator.evaluate(attacker, defender, specialistProbeResult);
        if (specialistProbeResult.probe() < minimumViabilityProbe) {
            return false;
        }
        conventionalProbeResult.set(
                specialistProbeResult.probe(),
                specialistProbeResult.preferredWarTypeId(),
                specialistProbeResult.bestAttackTypeId()
        );
        return true;
    }

    private static CandidateEdgeAdmissionPolicy resolveAdmissionPolicy(StrategicObjective objective) {
        return resolveAdmissionPolicy(objective, null);
    }

    private static CandidateEdgeAdmissionPolicy resolveAdmissionPolicy(StrategicObjective objective, SideOpeningSettings openingSettings) {
        if (openingSettings != null && openingSettings.admissionPolicy() != null) {
            return openingSettings.admissionPolicy();
        }
        CandidateEdgeAdmissionPolicy candidateAdmissionPolicy = objective.candidateEdgeAdmissionPolicy();
        return candidateAdmissionPolicy == null
                ? CandidateEdgeAdmissionPolicy.defaultPolicy()
                : candidateAdmissionPolicy;
    }

    private static void finalizeEdgeSelection(EdgeEvaluation edgeEvaluation, byte fallbackWarTypeId, byte fallbackAttackTypeId) {
        byte resolvedWarTypeId = edgeEvaluation.preferredWarTypeId() >= 0
                ? edgeEvaluation.preferredWarTypeId()
                : fallbackWarTypeId;
        byte resolvedAttackTypeId = edgeEvaluation.firstAttackTypeId() >= 0
                ? edgeEvaluation.firstAttackTypeId()
                : fallbackAttackTypeId;
        if (resolvedWarTypeId == edgeEvaluation.preferredWarTypeId()
                && resolvedAttackTypeId == edgeEvaluation.firstAttackTypeId()) {
            return;
        }
        edgeEvaluation.set(
                edgeEvaluation.score(),
                resolvedWarTypeId,
                resolvedAttackTypeId,
                edgeEvaluation.immediateHarm(),
                edgeEvaluation.selfExposure(),
                edgeEvaluation.resourceSwing(),
                edgeEvaluation.controlLeverage(),
                edgeEvaluation.futureWarLeverage()
        );
    }

    private static CandidateEdgeComponentPolicy resolveComponentPolicy(StrategicObjective objective) {
        CandidateEdgeComponentPolicy candidateComponentPolicy = objective.candidateEdgeComponentPolicy();
        return candidateComponentPolicy == null
                ? CandidateEdgeComponentPolicy.none()
                : candidateComponentPolicy;
    }

    private static EvaluatedEdge evaluateAdmittedOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            StrategicObjective objective,
            CandidateEdgeComponentPolicy componentPolicy,
            OpeningRolloutSearch rolloutEdgeEvaluator,
            EdgeEvaluation edgeEvaluation,
            int actionBudget
    ) {
        rolloutEdgeEvaluator.evaluate(attacker, defender, objective, actionBudget, edgeEvaluation);
        OpeningEdgeEvaluationWriter.retainComponents(edgeEvaluation, componentPolicy);
        return toEvaluatedEdge(edgeEvaluation);
    }

    private static EvaluatedEdge toEvaluatedEdge(EdgeEvaluation edgeEvaluation) {
        if (!Float.isFinite(edgeEvaluation.score())) {
            return REJECTED_EDGE;
        }
        return new EvaluatedEdge(
                edgeEvaluation.score(),
                edgeEvaluation.preferredWarTypeId(),
                edgeEvaluation.firstAttackTypeId(),
                edgeEvaluation.immediateHarm(),
                edgeEvaluation.selfExposure(),
                edgeEvaluation.resourceSwing(),
                edgeEvaluation.controlLeverage(),
                edgeEvaluation.futureWarLeverage()
        );
    }

    static final class TopKEdgeCollector {
        private final CandidateEdgeStorage edges;
        private final int[] sortedIndexes;
        private int activeLimit;
        private int size;
        private boolean sortedDirty;

        private TopKEdgeCollector(int k, CandidateEdgeComponentPolicy componentPolicy) {
            int capacity = Math.max(0, k);
            this.edges = new CandidateEdgeStorage(capacity, componentPolicy);
            this.sortedIndexes = new int[capacity];
            this.activeLimit = capacity;
            this.size = 0;
            this.sortedDirty = true;
        }

        int size() {
            return size;
        }

        private void clear() {
            size = 0;
            sortedDirty = true;
        }

        private void setActiveLimit(int value) {
            this.activeLimit = Math.max(0, Math.min(edges.capacity(), value));
            if (size > activeLimit) {
                size = activeLimit;
            }
            sortedDirty = true;
        }

        int sortedIndexAt(int order) {
            return sortedIndexes[order];
        }

        int attackerIndexAt(int index) {
            return edges.attackerIndexAt(index);
        }

        int defenderIndexAt(int index) {
            return edges.defenderIndexAt(index);
        }

        byte preferredWarTypeIdAt(int index) {
            return edges.preferredWarTypeIdAt(index);
        }

        float scoreAt(int index) {
            return edges.scoreAt(index);
        }

        float counterRiskAt(int index) {
            return edges.counterRiskAt(index);
        }

        float immediateHarmAt(int index) {
            return edges.retainsImmediateHarm() ? edges.immediateHarmAt(index) : 0f;
        }

        float selfExposureAt(int index) {
            return edges.retainsSelfExposure() ? edges.selfExposureAt(index) : 0f;
        }

        float resourceSwingAt(int index) {
            return edges.retainsResourceSwing() ? edges.resourceSwingAt(index) : 0f;
        }

        float controlLeverageAt(int index) {
            return edges.retainsControlLeverage() ? edges.controlLeverageAt(index) : 0f;
        }

        float futureWarLeverageAt(int index) {
            return edges.retainsFutureWarLeverage() ? edges.futureWarLeverageAt(index) : 0f;
        }

        byte bestAttackTypeIdAt(int index) {
            return edges.bestAttackTypeIdAt(index);
        }

        private void consider(
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
                float futureWarLeverage
        ) {
            if (activeLimit == 0) {
                return;
            }
            if (size < activeLimit) {
                edges.write(
                        size,
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
                siftUp(size);
                size++;
                sortedDirty = true;
                return;
            }
            if (!isBetter(
                    score,
                    attackerIndex,
                    defenderIndex,
                    edges.scoreAt(0),
                    edges.attackerIndexAt(0),
                    edges.defenderIndexAt(0)
            )) {
                return;
            }
            edges.write(
                    0,
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
            siftDown(0);
            sortedDirty = true;
        }

        void sortSelectedDescending() {
            if (!sortedDirty) {
                return;
            }
            for (int i = 0; i < size; i++) {
                sortedIndexes[i] = i;
            }
            for (int i = 1; i < size; i++) {
                int cursor = i;
                while (cursor > 0 && isBetter(
                    edges.scoreAt(sortedIndexes[cursor]),
                    edges.attackerIndexAt(sortedIndexes[cursor]),
                    edges.defenderIndexAt(sortedIndexes[cursor]),
                    edges.scoreAt(sortedIndexes[cursor - 1]),
                    edges.attackerIndexAt(sortedIndexes[cursor - 1]),
                    edges.defenderIndexAt(sortedIndexes[cursor - 1])
                )) {
                    int swap = sortedIndexes[cursor - 1];
                    sortedIndexes[cursor - 1] = sortedIndexes[cursor];
                    sortedIndexes[cursor] = swap;
                    cursor--;
                }
            }
            sortedDirty = false;
        }

        private void siftUp(int index) {
            int child = index;
            while (child > 0) {
                int parent = (child - 1) / 2;
                if (!isWorse(child, parent)) {
                    break;
                }
                swap(child, parent);
                child = parent;
            }
        }

        private void siftDown(int index) {
            int parent = index;
            while (true) {
                int left = parent * 2 + 1;
                if (left >= size) {
                    return;
                }
                int smallest = left;
                int right = left + 1;
                if (right < size && isWorse(right, left)) {
                    smallest = right;
                }
                if (!isWorse(smallest, parent)) {
                    return;
                }
                swap(parent, smallest);
                parent = smallest;
            }
        }

        private boolean isWorse(int lhsIndex, int rhsIndex) {
            return isBetter(
                edges.scoreAt(rhsIndex),
                    edges.attackerIndexAt(rhsIndex),
                    edges.defenderIndexAt(rhsIndex),
                edges.scoreAt(lhsIndex),
                    edges.attackerIndexAt(lhsIndex),
                    edges.defenderIndexAt(lhsIndex)
            );
        }

        private static boolean isBetter(
                float lhsScore,
                int lhsAttackerIndex,
                int lhsDefenderIndex,
                float rhsScore,
                int rhsAttackerIndex,
                int rhsDefenderIndex
        ) {
            if (lhsScore > rhsScore) {
                return true;
            }
            if (lhsScore < rhsScore) {
                return false;
            }
            if (lhsAttackerIndex < rhsAttackerIndex) {
                return true;
            }
            if (lhsAttackerIndex > rhsAttackerIndex) {
                return false;
            }
            return lhsDefenderIndex < rhsDefenderIndex;
        }

        private void swap(int lhs, int rhs) {
            edges.swap(lhs, rhs);
        }
    }

    static final class CoveragePriorityCollector {
        private final CandidateEdgeStorage edges;
        private final float[] priorities;
        private final int[] sortedIndexes;
        private int size;
        private boolean sortedDirty;

        CoveragePriorityCollector(int k, CandidateEdgeComponentPolicy componentPolicy) {
            int capacity = Math.max(0, k);
            this.edges = new CandidateEdgeStorage(capacity, componentPolicy);
            this.priorities = new float[capacity];
            this.sortedIndexes = new int[capacity];
            this.size = 0;
            this.sortedDirty = true;
        }

        void clear() {
            size = 0;
            sortedDirty = true;
        }

        int size() {
            return size;
        }

        int sortedIndexAt(int order) {
            return sortedIndexes[order];
        }

        int attackerIndexAt(int index) {
            return edges.attackerIndexAt(index);
        }

        int defenderIndexAt(int index) {
            return edges.defenderIndexAt(index);
        }

        byte preferredWarTypeIdAt(int index) {
            return edges.preferredWarTypeIdAt(index);
        }

        byte bestAttackTypeIdAt(int index) {
            return edges.bestAttackTypeIdAt(index);
        }

        float scoreAt(int index) {
            return edges.scoreAt(index);
        }

        float counterRiskAt(int index) {
            return edges.counterRiskAt(index);
        }

        float immediateHarmAt(int index) {
            return edges.retainsImmediateHarm() ? edges.immediateHarmAt(index) : 0f;
        }

        float selfExposureAt(int index) {
            return edges.retainsSelfExposure() ? edges.selfExposureAt(index) : 0f;
        }

        float resourceSwingAt(int index) {
            return edges.retainsResourceSwing() ? edges.resourceSwingAt(index) : 0f;
        }

        float controlLeverageAt(int index) {
            return edges.retainsControlLeverage() ? edges.controlLeverageAt(index) : 0f;
        }

        float futureWarLeverageAt(int index) {
            return edges.retainsFutureWarLeverage() ? edges.futureWarLeverageAt(index) : 0f;
        }

        float priorityAt(int index) {
            return priorities[index];
        }

        void consider(
                float priority,
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
                float futureWarLeverage
        ) {
            if (edges.capacity() == 0 || !Float.isFinite(priority)) {
                return;
            }
            if (size < edges.capacity()) {
                write(
                        size,
                        priority,
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
                siftUp(size);
                size++;
                sortedDirty = true;
                return;
            }
            if (!isHigherPriority(
                    priority,
                    score,
                    defenderIndex,
                    priorities[0],
                    edges.scoreAt(0),
                    edges.defenderIndexAt(0)
            )) {
                return;
            }
            write(
                    0,
                    priority,
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
            siftDown(0);
            sortedDirty = true;
        }

        void sortSelectedDescending() {
            if (!sortedDirty) {
                return;
            }
            for (int i = 0; i < size; i++) {
                sortedIndexes[i] = i;
            }
            for (int i = 1; i < size; i++) {
                int cursor = i;
                while (cursor > 0 && isHigherPriority(
                        priorities[sortedIndexes[cursor]],
                        edges.scoreAt(sortedIndexes[cursor]),
                        edges.defenderIndexAt(sortedIndexes[cursor]),
                        priorities[sortedIndexes[cursor - 1]],
                        edges.scoreAt(sortedIndexes[cursor - 1]),
                        edges.defenderIndexAt(sortedIndexes[cursor - 1])
                )) {
                    int swap = sortedIndexes[cursor - 1];
                    sortedIndexes[cursor - 1] = sortedIndexes[cursor];
                    sortedIndexes[cursor] = swap;
                    cursor--;
                }
            }
            sortedDirty = false;
        }

        static boolean isHigherPriority(
                float lhsPriority,
                float lhsScore,
                int lhsDefenderIndex,
                float rhsPriority,
                float rhsScore,
                int rhsDefenderIndex
        ) {
            if (lhsPriority > rhsPriority) {
                return true;
            }
            if (lhsPriority < rhsPriority) {
                return false;
            }
            if (lhsScore > rhsScore) {
                return true;
            }
            if (lhsScore < rhsScore) {
                return false;
            }
            return lhsDefenderIndex < rhsDefenderIndex;
        }

        private void write(
                int index,
                float priority,
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
                float futureWarLeverage
        ) {
            priorities[index] = priority;
            edges.write(
                    index,
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
        }

        private void siftUp(int index) {
            int child = index;
            while (child > 0) {
                int parent = (child - 1) / 2;
                if (!isLowerPriority(child, parent)) {
                    break;
                }
                swap(child, parent);
                child = parent;
            }
        }

        private void siftDown(int index) {
            int parent = index;
            while (true) {
                int left = parent * 2 + 1;
                if (left >= size) {
                    return;
                }
                int smallest = left;
                int right = left + 1;
                if (right < size && isLowerPriority(right, left)) {
                    smallest = right;
                }
                if (!isLowerPriority(smallest, parent)) {
                    return;
                }
                swap(parent, smallest);
                parent = smallest;
            }
        }

        private boolean isLowerPriority(int lhsIndex, int rhsIndex) {
            return isHigherPriority(
                    priorities[rhsIndex],
                    edges.scoreAt(rhsIndex),
                    edges.defenderIndexAt(rhsIndex),
                    priorities[lhsIndex],
                    edges.scoreAt(lhsIndex),
                    edges.defenderIndexAt(lhsIndex)
            );
        }

        private void swap(int lhs, int rhs) {
            float prioritySwap = priorities[lhs];
            priorities[lhs] = priorities[rhs];
            priorities[rhs] = prioritySwap;
            edges.swap(lhs, rhs);
        }
    }

    static final class EdgeEvaluation {
        private float score;
        private byte preferredWarTypeId;
        private byte firstAttackTypeId;
        private float immediateHarm;
        private float selfExposure;
        private float resourceSwing;
        private float controlLeverage;
        private float futureWarLeverage;

        float score() {
            return score;
        }

        byte preferredWarTypeId() {
            return preferredWarTypeId;
        }

        byte firstAttackTypeId() {
            return firstAttackTypeId;
        }

        float immediateHarm() {
            return immediateHarm;
        }

        float selfExposure() {
            return selfExposure;
        }

        float resourceSwing() {
            return resourceSwing;
        }

        float controlLeverage() {
            return controlLeverage;
        }

        float futureWarLeverage() {
            return futureWarLeverage;
        }

        void set(
                float score,
                byte preferredWarTypeId,
                byte firstAttackTypeId,
                float immediateHarm,
                float selfExposure,
                float resourceSwing,
                float controlLeverage,
                float futureWarLeverage
        ) {
            this.score = score;
            this.preferredWarTypeId = preferredWarTypeId;
            this.firstAttackTypeId = firstAttackTypeId;
            this.immediateHarm = immediateHarm;
            this.selfExposure = selfExposure;
            this.resourceSwing = resourceSwing;
            this.controlLeverage = controlLeverage;
            this.futureWarLeverage = futureWarLeverage;
        }

        void clear() {
            set(Float.NEGATIVE_INFINITY, (byte) -1, (byte) -1, 0f, 0f, 0f, 0f, 0f);
        }
    }

    static final class ProbeResult {
        private float probe;
        private byte preferredWarTypeId;
        private byte bestAttackTypeId;

        float probe() {
            return probe;
        }

        byte preferredWarTypeId() {
            return preferredWarTypeId;
        }

        byte bestAttackTypeId() {
            return bestAttackTypeId;
        }

        void set(float probe, byte preferredWarTypeId, byte bestAttackTypeId) {
            this.probe = probe;
            this.preferredWarTypeId = preferredWarTypeId;
            this.bestAttackTypeId = bestAttackTypeId;
        }
    }

    static final class SpecialistProbeResult {
        private float probe;
        private byte preferredWarTypeId;
        private byte bestAttackTypeId;

        float probe() {
            return probe;
        }

        byte preferredWarTypeId() {
            return preferredWarTypeId;
        }

        byte bestAttackTypeId() {
            return bestAttackTypeId;
        }

        void set(float probe, byte preferredWarTypeId, byte bestAttackTypeId) {
            this.probe = probe;
            this.preferredWarTypeId = preferredWarTypeId;
            this.bestAttackTypeId = bestAttackTypeId;
        }
    }

    static final class ViabilityProbeEvaluator {
        private final PairAttackContext context = new PairAttackContext();
        private final AttackScratch scratch = new AttackScratch();

        void evaluate(DBNationSnapshot attacker, DBNationSnapshot defender, ProbeResult out) {
            double best = 0d;
            byte bestWarTypeId = (byte) -1;
            byte bestAttackTypeId = (byte) -1;
            for (WarType warType : OPENING_WAR_TYPES) {
                context.bind(attacker, defender, warType);
                for (AttackType type : OPENING_ATTACK_TYPES) {
                    if (isSpecialist(type) || !isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                        continue;
                    }
                    double candidate = CombatKernel.admissionSignal(context, type, scratch);
                    if (candidate > best) {
                        best = candidate;
                        bestWarTypeId = (byte) warType.ordinal();
                        bestAttackTypeId = (byte) type.ordinal();
                    }
                }
            }
            out.set((float) best, bestWarTypeId, bestAttackTypeId);
        }
    }

    static final class SpecialistProbeEvaluator {
        private final PairAttackContext context = new PairAttackContext();
        private final AttackScratch scratch = new AttackScratch();
        private final MutableAttackResult result = new MutableAttackResult();

        void evaluate(DBNationSnapshot attacker, DBNationSnapshot defender, SpecialistProbeResult out) {
            double best = 0d;
            byte bestWarTypeId = (byte) -1;
            byte bestAttackTypeId = (byte) -1;
            for (WarType warType : OPENING_WAR_TYPES) {
                context.bind(attacker, defender, warType);
                for (AttackType type : OPENING_ATTACK_TYPES) {
                    if (!isSpecialist(type) || !isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                        continue;
                    }
                    double candidate = CombatKernel.specialistAdmissionSignal(context, type, scratch, result);
                    if (candidate > best) {
                        best = candidate;
                        bestWarTypeId = (byte) warType.ordinal();
                        bestAttackTypeId = (byte) type.ordinal();
                    }
                }
            }
            out.set((float) best, bestWarTypeId, bestAttackTypeId);
        }
    }

    static final class PairAttackContext implements CombatKernel.AttackContext {
        private final MutableNationState attacker = new MutableNationState();
        private final MutableNationState defender = new MutableNationState();
        private WarType warType = WarType.ORD;
        private int attackerMaps = SimWar.INITIAL_MAPS;
        private int defenderMaps = SimWar.INITIAL_MAPS;
        private int attackerResistance = SimWar.INITIAL_RESISTANCE;
        private int defenderResistance = SimWar.INITIAL_RESISTANCE;
        private boolean attackerHasAirControl;
        private boolean defenderHasAirControl;
        private boolean attackerHasGroundSuperiority;
        private boolean defenderHasGroundSuperiority;
        private boolean attackerFortified;
        private boolean defenderFortified;
        private int blockadeOwner = CombatKernel.AttackContext.BLOCKADE_NONE;

        void bind(DBNationSnapshot attackerSnapshot, DBNationSnapshot defenderSnapshot, WarType warType) {
            attacker.bind(attackerSnapshot);
            defender.bind(defenderSnapshot);
            this.warType = warType;
            attackerMaps = SimWar.INITIAL_MAPS;
            defenderMaps = SimWar.INITIAL_MAPS;
            attackerResistance = SimWar.INITIAL_RESISTANCE;
            defenderResistance = SimWar.INITIAL_RESISTANCE;
            attackerHasAirControl = false;
            defenderHasAirControl = false;
            attackerHasGroundSuperiority = false;
            defenderHasGroundSuperiority = false;
            attackerFortified = false;
            defenderFortified = false;
            blockadeOwner = CombatKernel.AttackContext.BLOCKADE_NONE;
        }

        @Override
        public MutableNationState attacker() {
            return attacker;
        }

        @Override
        public MutableNationState defender() {
            return defender;
        }

        @Override
        public WarType warType() {
            return warType;
        }

        @Override
        public boolean attackerHasAirControl() {
            return attackerHasAirControl;
        }

        @Override
        public boolean defenderHasAirControl() {
            return defenderHasAirControl;
        }

        @Override
        public boolean attackerHasGroundSuperiority() {
            return attackerHasGroundSuperiority;
        }

        @Override
        public boolean defenderHasGroundSuperiority() {
            return defenderHasGroundSuperiority;
        }

        @Override
        public boolean attackerFortified() {
            return attackerFortified;
        }

        @Override
        public boolean defenderFortified() {
            return defenderFortified;
        }

        @Override
        public int attackerMaps() {
            return attackerMaps;
        }

        @Override
        public int defenderMaps() {
            return defenderMaps;
        }

        @Override
        public int attackerResistance() {
            return attackerResistance;
        }

        @Override
        public int defenderResistance() {
            return defenderResistance;
        }

        @Override
        public int blockadeOwner() {
            return blockadeOwner;
        }

        double defenderTotalInfra() {
            return defender.totalInfra();
        }

        void applyExpectedResult(AttackType type, MutableAttackResult result) {
            attacker.applyLosses(result.attackerLossesEv());
            defender.applyLosses(result.defenderLossesEv());
            defender.applyInfraDamage(result.infraDestroyed());
            attackerMaps = Math.max(0, attackerMaps - result.mapCost());
            attackerResistance = clampResistance(attackerResistance + result.attackerResistanceDelta());
            defenderResistance = clampResistance(defenderResistance + result.defenderResistanceDelta());
            if (type != AttackType.FORTIFY) {
                attackerFortified = false;
            }
            applyControlDelta(result.controlDelta());
        }

        boolean attackerHasBlockade() {
            return blockadeOwner == CombatKernel.AttackContext.BLOCKADE_ATTACKER;
        }

        private void applyControlDelta(SuperiorityFlagDelta controlDelta) {
            if (controlDelta == null) {
                return;
            }
            if (controlDelta.clearGroundSuperiority()) {
                defenderHasGroundSuperiority = false;
            }
            if (controlDelta.groundSuperiority() > 0) {
                attackerHasGroundSuperiority = true;
                defenderHasGroundSuperiority = false;
            } else if (controlDelta.groundSuperiority() < 0) {
                attackerHasGroundSuperiority = false;
                defenderHasGroundSuperiority = true;
            }
            if (controlDelta.clearAirSuperiority()) {
                defenderHasAirControl = false;
            }
            if (controlDelta.airSuperiority() > 0) {
                attackerHasAirControl = true;
                defenderHasAirControl = false;
            } else if (controlDelta.airSuperiority() < 0) {
                attackerHasAirControl = false;
                defenderHasAirControl = true;
            }
            if (controlDelta.clearBlockade() && blockadeOwner == CombatKernel.AttackContext.BLOCKADE_DEFENDER) {
                blockadeOwner = CombatKernel.AttackContext.BLOCKADE_NONE;
            }
            if (controlDelta.blockade() > 0) {
                blockadeOwner = CombatKernel.AttackContext.BLOCKADE_ATTACKER;
            } else if (controlDelta.blockade() < 0) {
                blockadeOwner = CombatKernel.AttackContext.BLOCKADE_DEFENDER;
            }
        }

        private static int clampResistance(double value) {
            return Math.max(0, Math.min(SimWar.INITIAL_RESISTANCE, (int) Math.round(value)));
        }
    }

    static boolean isLegalOpeningAttack(CombatKernel.NationState attacker, int attackerMaps, AttackType type) {
        if (type.getMapUsed() > attackerMaps) {
            return false;
        }
        return CombatKernel.canUseAttackType(attacker, type);
    }

    private static boolean isSpecialist(AttackType type) {
        return type == AttackType.MISSILE || type == AttackType.NUKE;
    }

    private static final class MutableNationState implements CombatKernel.PrimitiveCityAccess {
        private DBNationSnapshot snapshot;
        private final double[] unitCounts = new double[MilitaryUnit.values.length];
        private double[] cityInfra = new double[0];
        private link.locutus.discord.sim.combat.SpecialistCityProfile[] cityProfiles = new link.locutus.discord.sim.combat.SpecialistCityProfile[0];
        private double totalInfra;

        void bind(DBNationSnapshot snapshot) {
            this.snapshot = snapshot;
            for (MilitaryUnit unit : MilitaryUnit.values) {
                unitCounts[unit.ordinal()] = snapshot.unit(unit);
            }
            double[] snapshotCityInfra = snapshot.cityInfraRaw();
            int cityCount = snapshotCityInfra.length;
            if (cityInfra.length < cityCount) {
                cityInfra = new double[cityCount];
            }
            System.arraycopy(snapshotCityInfra, 0, cityInfra, 0, cityCount);
            cityProfiles = snapshot.citySpecialistProfilesRaw();
            totalInfra = snapshot.totalInfraRaw();
        }

        void applyLosses(double[] losses) {
            for (MilitaryUnit unit : MilitaryUnit.values) {
                int index = unit.ordinal();
                if (losses[index] <= 0d) {
                    continue;
                }
                unitCounts[index] = Math.max(0d, unitCounts[index] - losses[index]);
            }
        }

        void applyInfraDamage(double damage) {
            int highestIndex = highestInfraCityIndex();
            if (highestIndex < 0) {
                return;
            }
            double removed = Math.min(damage, cityInfra[highestIndex]);
            if (removed <= 0d) {
                return;
            }
            cityInfra[highestIndex] -= removed;
            totalInfra -= removed;
        }

        double unitCount(MilitaryUnit unit) {
            return unitCounts[unit.ordinal()];
        }

        double totalInfra() {
            return Math.max(0d, totalInfra);
        }

        private int highestInfraCityIndex() {
            int bestIndex = -1;
            double bestInfra = 0d;
            for (int i = 0; i < snapshot.cities(); i++) {
                if (cityInfra[i] > bestInfra) {
                    bestInfra = cityInfra[i];
                    bestIndex = i;
                }
            }
            return bestIndex;
        }

        @Override
        public int nationId() {
            return snapshot.nationId();
        }

        @Override
        public int cities() {
            return snapshot.cities();
        }

        @Override
        public int researchBits() {
            return snapshot.researchBits();
        }

        @Override
        public double cityInfra(int cityIndex) {
            return cityInfra[cityIndex];
        }

        @Override
        public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].missileDamage(infra, this::hasProject);
        }

        @Override
        public int cityMissileDamageMin(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].missileDamageMin(infra, this::hasProject);
        }

        @Override
        public int cityMissileDamageMax(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].missileDamageMax(infra, this::hasProject);
        }

        @Override
        public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].nukeDamage(infra, this::hasProject);
        }

        @Override
        public int cityNukeDamageMin(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].nukeDamageMin(infra, this::hasProject);
        }

        @Override
        public int cityNukeDamageMax(int cityIndex) {
            double infra = cityInfra[cityIndex];
            return cityProfiles[cityIndex].nukeDamageMax(infra, this::hasProject);
        }

        @Override
        public double infraAttackModifier(AttackType type) {
            return snapshot.infraAttackModifier(type);
        }

        @Override
        public double infraDefendModifier(AttackType type) {
            return snapshot.infraDefendModifier(type);
        }

        @Override
        public double looterModifier(boolean ground) {
            return snapshot.looterModifier(ground);
        }

        @Override
        public double lootModifier() {
            return snapshot.lootModifier();
        }

        @Override
        public boolean isBlitzkrieg() {
            return snapshot.blitzkriegActive();
        }

        @Override
        public boolean hasProject(link.locutus.discord.apiv1.enums.city.project.Project project) {
            return snapshot.hasProject(project);
        }

        @Override
        public int getUnits(MilitaryUnit unit) {
            return Math.max(0, (int) Math.round(unitCounts[unit.ordinal()]));
        }
    }

    /**
     * Cheap kernel-EV probe: {@code max over applicable attack types of P(>= MODERATE_SUCCESS)}.
     *
     * <p>This is a single odds-model call per attack type — no horizon stepping, no state
     * mutation, no per-defender allocation. When game rules or odds formulas change, the
      * probe moves with them; there are no standalone ratio thresholds to recalibrate.
      * Missile/nuke low-probe admission is evaluated separately through a shared
      * specialist signal that uses the same combat owner path as live/planner execution.
     */
    static ProbeResult viabilityProbe(CompiledScenario scenario, int ai, int di) {
        ProbeResult result = new ProbeResult();
        viabilityProbe(scenario, ai, di, result);
        return result;
    }

    static void viabilityProbe(CompiledScenario scenario, int ai, int di, ProbeResult out) {
        viabilityProbe(scenario.attacker(ai), scenario.defender(di), out);
    }

    static void viabilityProbe(DBNationSnapshot attacker, DBNationSnapshot defender, ProbeResult out) {
        new ViabilityProbeEvaluator().evaluate(attacker, defender, out);
    }

}
