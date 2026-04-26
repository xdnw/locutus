package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.CandidateEdgeAdmissionPolicy;
import link.locutus.discord.sim.CandidateEdgeComponentPolicy;
import link.locutus.discord.sim.OpeningMetricVector;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.ControlFlagDelta;
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
    private static final AttackType[] OPENING_ATTACK_TYPES = {
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
    private static final WarType[] OPENING_WAR_TYPES = {
        WarType.RAID,
        WarType.ORD,
        WarType.ATT
    };

    private record OpeningBaseline(
            double attackerGround,
            double defenderGround,
            double attackerAir,
            double defenderAir,
            double attackerNaval,
            double defenderNaval,
            double defenderInfra
    ) {
        private static OpeningBaseline from(DBNationSnapshot attacker, DBNationSnapshot defender) {
            return new OpeningBaseline(
                    OpeningMetricSummary.groundStrength(attacker.unit(MilitaryUnit.SOLDIER), attacker.unit(MilitaryUnit.TANK), false),
                    OpeningMetricSummary.groundStrength(defender.unit(MilitaryUnit.SOLDIER), defender.unit(MilitaryUnit.TANK), false),
                    attacker.unit(MilitaryUnit.AIRCRAFT),
                    defender.unit(MilitaryUnit.AIRCRAFT),
                    attacker.unit(MilitaryUnit.SHIP),
                    defender.unit(MilitaryUnit.SHIP),
                    totalInfra(defender)
            );
        }
    }

    private OpeningEvaluator() {
    }

    static EvaluatedEdge evaluateOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            TeamScoreObjective objective,
            CandidateEdgeComponentPolicy componentPolicy
    ) {
        RolloutEdgeEvaluator evaluator = new RolloutEdgeEvaluator(DEFAULT_ACTION_BUDGET);
        EdgeEvaluation evaluation = new EdgeEvaluation();
        ProbeResult probeResult = new ProbeResult();
        SpecialistProbeResult specialistProbeResult = new SpecialistProbeResult();
        if (!admitCandidate(
                attacker,
                defender,
                resolveAdmissionPolicy(objective),
                probeResult,
                specialistProbeResult,
                new ViabilityProbeEvaluator(),
                new SpecialistProbeEvaluator()
        )) {
            return REJECTED_EDGE;
        }
        return evaluateAdmittedOpening(
            attacker,
            defender,
            objective,
            componentPolicy,
            evaluator,
            evaluation,
            actionBudgetForProbe(probeResult.probe(), evaluator.maxActionBudget())
        );
    }

    static EvaluatedEdge evaluateOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            TeamScoreObjective objective,
            CandidateEdgeComponentPolicy componentPolicy,
            int actionBudget
    ) {
        RolloutEdgeEvaluator evaluator = new RolloutEdgeEvaluator(actionBudget);
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
        private final TeamScoreObjective objective;
        private final CandidateEdgeAdmissionPolicy admissionPolicy;
        private final CandidateEdgeComponentPolicy componentPolicy;
        private final RolloutEdgeEvaluator rolloutEdgeEvaluator;
        private final EdgeEvaluation edgeEvaluation = new EdgeEvaluation();
        private final ProbeResult probeResult = new ProbeResult();
        private final SpecialistProbeResult specialistProbeResult = new SpecialistProbeResult();
        private final ViabilityProbeEvaluator viabilityProbeEvaluator = new ViabilityProbeEvaluator();
        private final SpecialistProbeEvaluator specialistProbeEvaluator = new SpecialistProbeEvaluator();

        CandidateOpeningEvaluator(TeamScoreObjective objective, int actionBudget) {
            this.objective = Objects.requireNonNull(objective, "objective");
            this.admissionPolicy = resolveAdmissionPolicy(objective);
            this.componentPolicy = resolveComponentPolicy(objective);
            this.rolloutEdgeEvaluator = new RolloutEdgeEvaluator(actionBudget);
        }

        EvaluatedEdge evaluate(DBNationSnapshot attacker, DBNationSnapshot defender) {
            if (!admitCandidate(
                    attacker,
                    defender,
                    admissionPolicy,
                    probeResult,
                    specialistProbeResult,
                    viabilityProbeEvaluator,
                    specialistProbeEvaluator
            )) {
                return REJECTED_EDGE;
            }
            return evaluateAdmittedOpening(
                    attacker,
                    defender,
                    objective,
                    componentPolicy,
                    rolloutEdgeEvaluator,
                    edgeEvaluation,
                    actionBudgetForProbe(probeResult.probe(), rolloutEdgeEvaluator.maxActionBudget())
            );
        }
    }

    /**
     * Evaluates all legal (attacker, defender) pairs in the compiled scenario and appends them
     * to {@code out}.
     *
     * <p>For each attacker, at most {@code candidatesPerAttacker} edges are admitted after
     * kernel-EV pruning and score ranking.
     */
    static void evaluate(
            CompiledScenario scenario,
            SimTuning tuning,
            OverrideSet overrides,
            TeamScoreObjective objective,
            int[] attackerCaps,
            int[] defenderCaps,
            CandidateEdgeTable out
    ) {
        CandidateEdgeComponentPolicy componentPolicy = objective.candidateEdgeComponentPolicy();
        out.configureComponentRetention(componentPolicy);
        CandidateEdgeAdmissionPolicy admissionPolicy = objective.candidateEdgeAdmissionPolicy();
        CandidateEdgeAdmissionPolicy resolvedAdmissionPolicy = admissionPolicy == null
            ? CandidateEdgeAdmissionPolicy.defaultPolicy()
            : admissionPolicy;
        int candidatesPerAttacker = tuning.candidatesPerAttacker();
        DefenderAdmissionCollector collector = new DefenderAdmissionCollector(
                scenario,
                objective,
                resolvedAdmissionPolicy,
                componentPolicy,
                defenderCaps,
                candidatesPerAttacker,
                out
        );

        for (int ai = 0; ai < scenario.attackerCount(); ai++) {
            if (attackerCaps[ai] <= 0) {
                continue;
            }
            collector.beginAttacker(ai);
            scenario.forEachDefenderIndexInRange(ai, collector);
            collector.emit();
        }
    }

    private static final class DefenderAdmissionCollector implements IntConsumer {
        private final CompiledScenario scenario;
        private final TeamScoreObjective objective;
        private final CandidateEdgeComponentPolicy componentPolicy;
        private final CandidateEdgeAdmissionPolicy admissionPolicy;
        private final int[] defenderCaps;
        private final TopKDefenderCollector topK;
        private final CandidateEdgeTable out;
        private int attackerIndex;
        private DBNationSnapshot attacker;
        private final EdgeComponents edgeComponents;
        private final ProbeResult probeResult;
        private final SpecialistProbeResult specialistProbeResult;
        private final ViabilityProbeEvaluator viabilityProbeEvaluator;
        private final SpecialistProbeEvaluator specialistProbeEvaluator;
        private final RolloutEdgeEvaluator rolloutEdgeEvaluator;
        private final EdgeEvaluation edgeEvaluation;

        private DefenderAdmissionCollector(
                CompiledScenario scenario,
                TeamScoreObjective objective,
                CandidateEdgeAdmissionPolicy admissionPolicy,
                CandidateEdgeComponentPolicy componentPolicy,
                int[] defenderCaps,
                int candidatesPerAttacker,
                CandidateEdgeTable out
        ) {
            this.scenario = scenario;
            this.objective = objective;
            this.componentPolicy = componentPolicy == null ? CandidateEdgeComponentPolicy.none() : componentPolicy;
            this.admissionPolicy = admissionPolicy;
            this.defenderCaps = defenderCaps;
            this.topK = new TopKDefenderCollector(candidatesPerAttacker, this.componentPolicy);
            this.out = out;
            this.edgeComponents = new EdgeComponents();
            this.probeResult = new ProbeResult();
            this.specialistProbeResult = new SpecialistProbeResult();
            this.viabilityProbeEvaluator = new ViabilityProbeEvaluator();
            this.specialistProbeEvaluator = new SpecialistProbeEvaluator();
            this.rolloutEdgeEvaluator = new RolloutEdgeEvaluator(DEFAULT_ACTION_BUDGET);
            this.edgeEvaluation = new EdgeEvaluation();
        }

        private void beginAttacker(int attackerIndex) {
            this.attackerIndex = attackerIndex;
            this.attacker = scenario.attacker(attackerIndex);
            this.topK.clear();
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
            if (!admitCandidate(
                    attacker,
                    defender,
                    admissionPolicy,
                    probeResult,
                    specialistProbeResult,
                    viabilityProbeEvaluator,
                    specialistProbeEvaluator
            )) {
                return;
            }
            float probe = probeResult.probe();
            byte bestAttackTypeId = probeResult.bestAttackTypeId();
            byte preferredWarTypeId = probeResult.preferredWarTypeId();
            rolloutEdgeEvaluator.evaluate(
                    attacker,
                    defender,
                    objective,
                    componentPolicy,
                    actionBudgetForProbe(probe, rolloutEdgeEvaluator.maxActionBudget()),
                    edgeEvaluation
            );
            float score = edgeEvaluation.score();
            if (!Float.isFinite(score)) {
                return;
            }
            if (edgeEvaluation.firstAttackTypeId() >= 0) {
                bestAttackTypeId = edgeEvaluation.firstAttackTypeId();
            }
            if (edgeEvaluation.preferredWarTypeId() >= 0) {
                preferredWarTypeId = edgeEvaluation.preferredWarTypeId();
            }
            edgeComponents.set(
                    edgeEvaluation.immediateHarm(),
                    edgeEvaluation.selfExposure(),
                    edgeEvaluation.resourceSwing(),
                    edgeEvaluation.controlLeverage(),
                    edgeEvaluation.futureWarLeverage()
            );
            float counterRisk = (float) scenario.estimateAllianceCounterRisk(attackerIndex, defenderIndex);
            topK.consider(
                    defenderIndex,
                    preferredWarTypeId,
                    bestAttackTypeId,
                    score,
                    counterRisk,
                    edgeComponents.immediateHarm,
                    edgeComponents.selfExposure,
                    edgeComponents.resourceSwing,
                    edgeComponents.controlLeverage,
                    edgeComponents.futureWarLeverage
            );
        }

        private void emit() {
            if (topK.size() == 0) {
                return;
            }
            topK.sortSelectedDescending();
            for (int order = 0; order < topK.size(); order++) {
                int selected = topK.sortedIndexAt(order);
                out.add(
                        attackerIndex,
                        topK.defenderIndexAt(selected),
                        topK.preferredWarTypeIdAt(selected),
                        topK.bestAttackTypeIdAt(selected),
                        topK.scoreAt(selected),
                        topK.counterRiskAt(selected),
                        topK.immediateHarmAt(selected),
                        topK.selfExposureAt(selected),
                        topK.resourceSwingAt(selected),
                        topK.controlLeverageAt(selected),
                        topK.futureWarLeverageAt(selected)
                );
            }
        }
    }

    private static boolean admitCandidate(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            CandidateEdgeAdmissionPolicy admissionPolicy,
            ProbeResult conventionalProbeResult,
            SpecialistProbeResult specialistProbeResult,
            ViabilityProbeEvaluator viabilityProbeEvaluator,
            SpecialistProbeEvaluator specialistProbeEvaluator
    ) {
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

    private static CandidateEdgeAdmissionPolicy resolveAdmissionPolicy(TeamScoreObjective objective) {
        CandidateEdgeAdmissionPolicy candidateAdmissionPolicy = objective.candidateEdgeAdmissionPolicy();
        return candidateAdmissionPolicy == null
                ? CandidateEdgeAdmissionPolicy.defaultPolicy()
                : candidateAdmissionPolicy;
    }

    private static CandidateEdgeComponentPolicy resolveComponentPolicy(TeamScoreObjective objective) {
        CandidateEdgeComponentPolicy candidateComponentPolicy = objective.candidateEdgeComponentPolicy();
        return candidateComponentPolicy == null
                ? CandidateEdgeComponentPolicy.none()
                : candidateComponentPolicy;
    }

    private static EvaluatedEdge evaluateAdmittedOpening(
            DBNationSnapshot attacker,
            DBNationSnapshot defender,
            TeamScoreObjective objective,
            CandidateEdgeComponentPolicy componentPolicy,
            RolloutEdgeEvaluator rolloutEdgeEvaluator,
            EdgeEvaluation edgeEvaluation,
            int actionBudget
    ) {
        rolloutEdgeEvaluator.evaluate(attacker, defender, objective, componentPolicy, actionBudget, edgeEvaluation);
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

    private static final class TopKDefenderCollector {
        private final int[] defenderIndexes;
        private final byte[] preferredWarTypeIds;
        private final float[] scores;
        private final float[] counterRisks;
        private final byte[] bestAttackTypeIds;
        private final int[] sortedIndexes;
        private final CandidateEdgeComponents retainedComponents;
        private int size;
        private boolean sortedDirty;

        private TopKDefenderCollector(int k, CandidateEdgeComponentPolicy componentPolicy) {
            int capacity = Math.max(0, k);
            this.defenderIndexes = new int[capacity];
            this.preferredWarTypeIds = new byte[capacity];
            this.scores = new float[capacity];
            this.counterRisks = new float[capacity];
            this.bestAttackTypeIds = new byte[capacity];
            this.sortedIndexes = new int[capacity];
            this.retainedComponents = new CandidateEdgeComponents(capacity, componentPolicy);
            this.size = 0;
            this.sortedDirty = true;
        }

        private int size() {
            return size;
        }

        private void clear() {
            size = 0;
            sortedDirty = true;
        }

        private int sortedIndexAt(int order) {
            return sortedIndexes[order];
        }

        private int defenderIndexAt(int index) {
            return defenderIndexes[index];
        }

        private byte preferredWarTypeIdAt(int index) {
            return preferredWarTypeIds[index];
        }

        private float scoreAt(int index) {
            return scores[index];
        }

        private float counterRiskAt(int index) {
            return counterRisks[index];
        }

        private float immediateHarmAt(int index) {
            return retainedComponents.retainsImmediateHarm() ? retainedComponents.immediateHarm(index) : 0f;
        }

        private float selfExposureAt(int index) {
            return retainedComponents.retainsSelfExposure() ? retainedComponents.selfExposure(index) : 0f;
        }

        private float resourceSwingAt(int index) {
            return retainedComponents.retainsResourceSwing() ? retainedComponents.resourceSwing(index) : 0f;
        }

        private float controlLeverageAt(int index) {
            return retainedComponents.retainsControlLeverage() ? retainedComponents.controlLeverage(index) : 0f;
        }

        private float futureWarLeverageAt(int index) {
            return retainedComponents.retainsFutureWarLeverage() ? retainedComponents.futureWarLeverage(index) : 0f;
        }

        private byte bestAttackTypeIdAt(int index) {
            return bestAttackTypeIds[index];
        }

        private void consider(
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
            if (defenderIndexes.length == 0) {
                return;
            }
            if (size < defenderIndexes.length) {
                defenderIndexes[size] = defenderIndex;
                preferredWarTypeIds[size] = preferredWarTypeId;
                bestAttackTypeIds[size] = bestAttackTypeId;
                scores[size] = score;
                counterRisks[size] = counterRisk;
                retainedComponents.set(size, immediateHarm, selfExposure, resourceSwing, controlLeverage, futureWarLeverage);
                siftUp(size);
                size++;
                sortedDirty = true;
                return;
            }
            if (!isBetter(score, defenderIndex, scores[0], defenderIndexes[0])) {
                return;
            }
            defenderIndexes[0] = defenderIndex;
            preferredWarTypeIds[0] = preferredWarTypeId;
            bestAttackTypeIds[0] = bestAttackTypeId;
            scores[0] = score;
            counterRisks[0] = counterRisk;
            retainedComponents.set(0, immediateHarm, selfExposure, resourceSwing, controlLeverage, futureWarLeverage);
            siftDown(0);
            sortedDirty = true;
        }

        private void sortSelectedDescending() {
            if (!sortedDirty) {
                return;
            }
            for (int i = 0; i < size; i++) {
                sortedIndexes[i] = i;
            }
            for (int i = 1; i < size; i++) {
                int cursor = i;
                while (cursor > 0 && isBetter(
                    scores[sortedIndexes[cursor]],
                    defenderIndexes[sortedIndexes[cursor]],
                    scores[sortedIndexes[cursor - 1]],
                    defenderIndexes[sortedIndexes[cursor - 1]]
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
            return isBetter(scores[rhsIndex], defenderIndexes[rhsIndex], scores[lhsIndex], defenderIndexes[lhsIndex]);
        }

        private static boolean isBetter(float lhsScore, int lhsDefenderIndex, float rhsScore, int rhsDefenderIndex) {
            if (lhsScore > rhsScore) {
                return true;
            }
            if (lhsScore < rhsScore) {
                return false;
            }
            return lhsDefenderIndex < rhsDefenderIndex;
        }

        private void swap(int lhs, int rhs) {
            int defenderSwap = defenderIndexes[lhs];
            defenderIndexes[lhs] = defenderIndexes[rhs];
            defenderIndexes[rhs] = defenderSwap;

            byte warTypeSwap = preferredWarTypeIds[lhs];
            preferredWarTypeIds[lhs] = preferredWarTypeIds[rhs];
            preferredWarTypeIds[rhs] = warTypeSwap;

            float scoreSwap = scores[lhs];
            scores[lhs] = scores[rhs];
            scores[rhs] = scoreSwap;

            float riskSwap = counterRisks[lhs];
            counterRisks[lhs] = counterRisks[rhs];
            counterRisks[rhs] = riskSwap;

            byte bestAttackSwap = bestAttackTypeIds[lhs];
            bestAttackTypeIds[lhs] = bestAttackTypeIds[rhs];
            bestAttackTypeIds[rhs] = bestAttackSwap;
            retainedComponents.swap(lhs, rhs);
        }
    }

    private static final class EdgeComponents {
        private float immediateHarm;
        private float selfExposure;
        private float resourceSwing;
        private float controlLeverage;
        private float futureWarLeverage;

        private void set(
                float immediateHarm,
                float selfExposure,
                float resourceSwing,
                float controlLeverage,
                float futureWarLeverage
        ) {
            this.immediateHarm = immediateHarm;
            this.selfExposure = selfExposure;
            this.resourceSwing = resourceSwing;
            this.controlLeverage = controlLeverage;
            this.futureWarLeverage = futureWarLeverage;
        }

        private void clear() {
            immediateHarm = 0f;
            selfExposure = 0f;
            resourceSwing = 0f;
            controlLeverage = 0f;
            futureWarLeverage = 0f;
        }
    }

    private static final class EdgeEvaluation {
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

    private static final class SpecialistProbeResult {
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

    private static final class ViabilityProbeEvaluator {
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

    private static final class SpecialistProbeEvaluator {
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

    private static final class PairAttackContext implements CombatKernel.AttackContext {
        private final MutableNationState attacker = new MutableNationState();
        private final MutableNationState defender = new MutableNationState();
        private WarType warType = WarType.ORD;
        private int attackerMaps = SimWar.INITIAL_MAPS;
        private int defenderMaps = SimWar.INITIAL_MAPS;
        private int attackerResistance = SimWar.INITIAL_RESISTANCE;
        private int defenderResistance = SimWar.INITIAL_RESISTANCE;
        private boolean attackerHasAirControl;
        private boolean defenderHasAirControl;
        private boolean attackerHasGroundControl;
        private boolean defenderHasGroundControl;
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
            attackerHasGroundControl = false;
            defenderHasGroundControl = false;
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
        public boolean attackerHasGroundControl() {
            return attackerHasGroundControl;
        }

        @Override
        public boolean defenderHasGroundControl() {
            return defenderHasGroundControl;
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

        private void applyControlDelta(ControlFlagDelta controlDelta) {
            if (controlDelta == null) {
                return;
            }
            if (controlDelta.clearGroundControl()) {
                defenderHasGroundControl = false;
            }
            if (controlDelta.groundControl() > 0) {
                attackerHasGroundControl = true;
                defenderHasGroundControl = false;
            } else if (controlDelta.groundControl() < 0) {
                attackerHasGroundControl = false;
                defenderHasGroundControl = true;
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

    private static final class RolloutEdgeEvaluator {
        private final int maxActionBudget;
        private final PairAttackContext context = new PairAttackContext();
        private final AttackScratch scratch = new AttackScratch();
        private final MutableAttackResult result = new MutableAttackResult();
        private final OpeningMetricVector.Mutable currentMetrics = new OpeningMetricVector.Mutable();
        private final OpeningMetricVector.Mutable bestMetrics = new OpeningMetricVector.Mutable();
        private final OpeningMetricVector.Mutable projectedMetrics = new OpeningMetricVector.Mutable();

        private RolloutEdgeEvaluator(int actionBudget) {
            this.maxActionBudget = Math.max(1, actionBudget);
        }

        private int maxActionBudget() {
            return maxActionBudget;
        }

        void evaluate(
                DBNationSnapshot attacker,
                DBNationSnapshot defender,
                TeamScoreObjective objective,
                CandidateEdgeComponentPolicy componentPolicy,
                int actionBudget,
                EdgeEvaluation out
        ) {
            out.clear();
            CandidateEdgeComponentPolicy policy = componentPolicy == null
                    ? CandidateEdgeComponentPolicy.none()
                    : componentPolicy;
            OpeningBaseline baseline = OpeningBaseline.from(attacker, defender);
            int effectiveActionBudget = Math.max(1, Math.min(maxActionBudget, actionBudget));

            for (WarType warType : OPENING_WAR_TYPES) {
                evaluateWarType(attacker, defender, baseline, warType, objective, policy, effectiveActionBudget, out);
            }
        }

        private void evaluateWarType(
                DBNationSnapshot attacker,
                DBNationSnapshot defender,
                OpeningBaseline baseline,
                WarType warType,
                TeamScoreObjective objective,
                CandidateEdgeComponentPolicy policy,
                int actionBudget,
                EdgeEvaluation out
        ) {
            context.bind(attacker, defender, warType);

            byte firstAttackTypeId = (byte) -1;
            currentMetrics.clear();
            float currentScore = scoreObjective(objective, attacker.teamId(), currentMetrics);

            for (int action = 0; action < actionBudget; action++) {
                float bestNextScore = currentScore;
                AttackType bestType = null;
                bestMetrics.copyFrom(currentMetrics);

                for (AttackType type : OPENING_ATTACK_TYPES) {
                    if (!isLegalOpeningAttack(context.attacker(), context.attackerMaps(), type)) {
                        continue;
                    }
                    CombatKernel.resolveInto(context, type, ResolutionMode.DETERMINISTIC_EV, scratch, result);
                    projectMetrics(baseline, currentMetrics, result, projectedMetrics);
                    float projectedScore = scoreObjective(objective, attacker.teamId(), projectedMetrics);
                    if (projectedScore > bestNextScore) {
                        bestNextScore = projectedScore;
                        bestType = type;
                        bestMetrics.copyFrom(projectedMetrics);
                    }
                }

                if (bestType == null) {
                    break;
                }

                CombatKernel.resolveInto(context, bestType, ResolutionMode.DETERMINISTIC_EV, scratch, result);
                context.applyExpectedResult(bestType, result);
                currentMetrics.copyFrom(bestMetrics);
                currentScore = bestNextScore;
                if (firstAttackTypeId < 0) {
                    firstAttackTypeId = (byte) bestType.ordinal();
                }
            }

            if (firstAttackTypeId < 0 || !Float.isFinite(currentScore)) {
                return;
            }

            if (currentScore > out.score()) {
                out.set(
                        currentScore,
                        (byte) warType.ordinal(),
                        firstAttackTypeId,
                        policy.retainImmediateHarm() ? (float) currentMetrics.immediateHarm() : 0f,
                        policy.retainSelfExposure() ? (float) currentMetrics.selfExposure() : 0f,
                        policy.retainResourceSwing() ? (float) currentMetrics.resourceSwing() : 0f,
                        policy.retainControlLeverage() ? (float) currentMetrics.controlLeverage() : 0f,
                        policy.retainFutureWarLeverage() ? (float) currentMetrics.futureWarLeverage() : 0f
                );
            }
        }

        private void projectMetrics(
                OpeningBaseline baseline,
                OpeningMetricVector currentMetrics,
                MutableAttackResult result,
                OpeningMetricVector.Mutable out
        ) {
            boolean attackerHasGroundControl = projectedAttackerHasGroundControl(result.controlDelta());
            boolean attackerHasAirControl = projectedAttackerHasAirControl(result.controlDelta());
            boolean attackerHasBlockade = projectedAttackerHasBlockade(result.controlDelta());
            boolean defenderHasAirControl = projectedDefenderHasAirControl(result.controlDelta());
            double immediateHarm = currentMetrics.immediateHarm() + OpeningMetricSummary.immediateHarm(result);
            double selfExposure = currentMetrics.selfExposure() + OpeningMetricSummary.selfExposure(result);
            double resourceSwing = currentMetrics.resourceSwing() + result.loot();
            double controlLeverage = OpeningMetricSummary.controlLeverage(
                    attackerHasGroundControl,
                    attackerHasAirControl,
                    attackerHasBlockade
            );
            double futureWarLeverage = OpeningMetricSummary.futureWarLeverage(
                    baseline.attackerGround(),
                    OpeningMetricSummary.groundStrength(
                            remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.SOLDIER),
                            remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.TANK),
                            defenderHasAirControl
                    ),
                    baseline.defenderGround(),
                    OpeningMetricSummary.groundStrength(
                            remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.SOLDIER),
                            remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.TANK),
                            attackerHasAirControl
                    ),
                    baseline.attackerAir(),
                    remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.AIRCRAFT),
                    baseline.defenderAir(),
                    remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.AIRCRAFT),
                    baseline.attackerNaval(),
                    remainingUnits(context.attacker(), result.attackerLossesEv(), MilitaryUnit.SHIP),
                    baseline.defenderNaval(),
                    remainingUnits(context.defender(), result.defenderLossesEv(), MilitaryUnit.SHIP),
                    baseline.defenderInfra(),
                    Math.max(0d, context.defender().totalInfra() - result.infraDestroyed()),
                    projectedDefenderResistance(result)
            );
            out.set(
                    immediateHarm,
                    selfExposure,
                    resourceSwing,
                    controlLeverage,
                    futureWarLeverage
            );
        }

        private float scoreObjective(
                TeamScoreObjective objective,
                int attackerTeamId,
                OpeningMetricVector metrics
        ) {
            return (float) objective.scoreOpening(metrics, attackerTeamId);
        }

        private boolean projectedAttackerHasGroundControl(ControlFlagDelta controlDelta) {
            if (controlDelta == null) {
                return context.attackerHasGroundControl();
            }
            if (controlDelta.groundControl() == 0) {
                return context.attackerHasGroundControl();
            }
            return controlDelta.groundControl() > 0;
        }

        private boolean projectedAttackerHasAirControl(ControlFlagDelta controlDelta) {
            if (controlDelta == null) {
                return context.attackerHasAirControl();
            }
            if (controlDelta.airSuperiority() == 0) {
                return context.attackerHasAirControl();
            }
            return controlDelta.airSuperiority() > 0;
        }

        private boolean projectedDefenderHasAirControl(ControlFlagDelta controlDelta) {
            if (controlDelta == null) {
                return context.defenderHasAirControl();
            }
            if (controlDelta.airSuperiority() == 0) {
                return controlDelta.clearAirSuperiority() ? false : context.defenderHasAirControl();
            }
            return controlDelta.airSuperiority() < 0;
        }

        private boolean projectedAttackerHasBlockade(ControlFlagDelta controlDelta) {
            if (controlDelta == null) {
                return context.attackerHasBlockade();
            }
            if (controlDelta.blockade() == 0) {
                return controlDelta.clearBlockade() ? false : context.attackerHasBlockade();
            }
            return controlDelta.blockade() > 0;
        }

        private int projectedDefenderResistance(MutableAttackResult result) {
            return PairAttackContext.clampResistance(context.defenderResistance() + result.defenderResistanceDelta());
        }

        private double remainingUnits(CombatKernel.NationState nation, double[] losses, MilitaryUnit unit) {
            return Math.max(0d, nation.getUnits(unit) - losses[unit.ordinal()]);
        }
    }

    private static boolean isLegalOpeningAttack(CombatKernel.NationState attacker, int attackerMaps, AttackType type) {
        if (type.getMapUsed() > attackerMaps) {
            return false;
        }
        return CombatKernel.canUseAttackType(attacker, type);
    }

    private static boolean isSpecialist(AttackType type) {
        return type == AttackType.MISSILE || type == AttackType.NUKE;
    }

    private static double totalInfra(DBNationSnapshot snapshot) {
        return snapshot.totalInfraRaw();
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
            double remaining = damage;
            while (remaining > 0d) {
                int highestIndex = highestInfraCityIndex();
                if (highestIndex < 0) {
                    return;
                }
                double removed = Math.min(remaining, cityInfra[highestIndex]);
                if (removed <= 0d) {
                    return;
                }
                cityInfra[highestIndex] -= removed;
                totalInfra -= removed;
                remaining -= removed;
            }
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
