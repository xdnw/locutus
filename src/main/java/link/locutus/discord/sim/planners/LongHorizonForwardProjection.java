package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.StrategicObjective;
import link.locutus.discord.sim.StrategicTimingValue;
import link.locutus.discord.sim.TeamWarControlView;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.combat.WarControlRules;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.planners.compile.CompiledActiveWar;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import link.locutus.discord.util.PW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primitive forward-projection surface for long-horizon blitz assignment scoring.
 *
 * <p>This is intentionally not a replay engine. It owns dense planner-local arrays that can price
 * terminal score, active-war control metrics, unit rebuy capacity, and expected counter exposure
 * without constructing local conflict worlds for each assignment candidate.</p>
 */
final class LongHorizonForwardProjection {
    private static final ScenarioCompiler PROJECTED_DECLARATION_SCENARIO_COMPILER = new ScenarioCompiler();
    private static final int INITIAL_WAR_MAPS = 6;
    private static final int INITIAL_RESISTANCE = 100;
    private static final int MAP_CAP = 12;
    private static final int WAR_EXPIRATION_TURN = 60;
    private static final int PROJECTED_COUNTER_START_TURN = 1;
    private static final double WIPE_RISK_COMBAT_STRENGTH_RATIO = 0.25d;
    private static final int DAY_TURNS = 12;
    private static final MilitaryUnit[] PROJECTED_BUY_UNITS = {
        MilitaryUnit.AIRCRAFT,
        MilitaryUnit.TANK,
        MilitaryUnit.SHIP,
        MilitaryUnit.SOLDIER
    };
    private static final AttackType[] ADAPTIVE_ATTACK_TYPES = {
        AttackType.NUKE,
        AttackType.MISSILE,
        AttackType.GROUND,
        AttackType.AIRSTRIKE_AIRCRAFT,
        AttackType.AIRSTRIKE_TANK,
        AttackType.AIRSTRIKE_SOLDIER,
        AttackType.NAVAL_AIR,
        AttackType.NAVAL_GROUND,
        AttackType.NAVAL,
        AttackType.AIRSTRIKE_INFRA,
        AttackType.NAVAL_INFRA
    };

    private final CandidateEdgeTable edges;
    private final CompiledScenario scenario;
    private final int horizonTurns;
    private final double horizonFactor;
    private final double[] attackerInitialScores;
    private final double[] defenderInitialScores;
    private final double[] attackerProjectedBuyScore;
    private final double[] defenderProjectedBuyScore;
    private final double[] attackerCombatStrengths;
    private final double[] defenderCombatStrengths;
    private final LongHorizonCounterOpportunityModel counterOpportunityModel;
    private final int[] attackerCaps;
    private final StrategicObjective projectionObjective;
    private final SideOpeningSettings attackerOpeningSettings;
    private final SideOpeningSettings defenderOpeningSettings;
    private final SidePlannerSettings attackerPlannerSettings;
    private final SidePlannerSettings defenderPlannerSettings;
    private final SideProjectionPolicies attackerProjectionPolicies;
    private final SideProjectionPolicies defenderProjectionPolicies;
    private ProjectionState projectionState;
    private DenseWarState warState;
    private final Map<ActiveWarProfileKey, ProjectionStateCheckpoint> preparedStateCheckpoints;
    private DenseWarStateCheckpoint preparedWarTemplateCheckpoint;
    private final AttackScratch projectionScratch;
    private final MutableAttackResult projectionResult;
    private final boolean[] scratchActiveWarsByNation;
    private final int[] scratchActiveOffWarsByNation;
    private final int[] scratchActiveDefWarsByNation;
    private final int[] scratchCounterOffSlots;
    private final int[] scratchCounterDefSlots;
    private final double[] scratchCounterDeclarerStrengths;
    private final double[] scratchCounterDeclarerActivityWeights;
    private final double[] scratchCounterTargetStrengths;
    private final double[] scratchCounterTargetActionValues;
    private final int[] scratchRedeclareAttSlots;
    private final int[] scratchRedeclareDefSlots;
    private final double[] scratchRedeclareAttackerStrengths;
    private final double[] scratchRedeclareDefenderStrengths;
    private final ProjectionAttackEvaluator projectionAttackEvaluator;
    private final HeuristicAttackChoicePolicy.MutableAttackCandidate heuristicAttackCandidate;
    private final ProjectionCounterEvaluator projectionCounterEvaluator;
    private final ProjectionRedeclareEvaluator projectionRedeclareEvaluator;
    private long profiledProjectionTurns;
    private long profiledCounterTurns;
    private long profiledCounterTurnsNoSlots;
    private long profiledCounterCandidateEvaluations;
    private long profiledCounterDeclarations;
    private long profiledRedeclareTurns;
    private long profiledRedeclareTurnsNoSlots;
    private long profiledRedeclareCandidateEvaluations;
    private long profiledRedeclarations;
    private long profiledWarIterations;
    private long profiledAttackChoiceCalls;
    private long profiledAttackTypeEvaluations;
    private long profiledResolvedAttacks;
    private long profiledPreparedStateProfiles;
    private long profiledPreparedStateRestores;
    private long profiledPreparedWarTemplateBuilds;
    private long profiledPreparedWarRestores;

    private LongHorizonForwardProjection(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int horizonTurns,
            double horizonFactor,
            double[] attackerInitialScores,
            double[] defenderInitialScores,
            double[] attackerProjectedBuyScore,
            double[] defenderProjectedBuyScore,
            double[] attackerCombatStrengths,
            double[] defenderCombatStrengths,
            LongHorizonCounterOpportunityModel counterOpportunityModel,
            int[] attackerCaps,
            StrategicObjective projectionObjective,
            SideOpeningSettings attackerOpeningSettings,
            SideOpeningSettings defenderOpeningSettings,
            SidePlannerSettings attackerPlannerSettings,
            SidePlannerSettings defenderPlannerSettings,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        this.edges = edges;
        this.scenario = scenario;
        this.horizonTurns = horizonTurns;
        this.horizonFactor = horizonFactor;
        this.attackerInitialScores = attackerInitialScores;
        this.defenderInitialScores = defenderInitialScores;
        this.attackerProjectedBuyScore = attackerProjectedBuyScore;
        this.defenderProjectedBuyScore = defenderProjectedBuyScore;
        this.attackerCombatStrengths = attackerCombatStrengths;
        this.defenderCombatStrengths = defenderCombatStrengths;
        this.counterOpportunityModel = counterOpportunityModel;
        this.attackerCaps = attackerCaps;
        this.projectionObjective = projectionObjective;
        this.attackerOpeningSettings = attackerOpeningSettings;
        this.defenderOpeningSettings = defenderOpeningSettings;
        this.attackerPlannerSettings = attackerPlannerSettings;
        this.defenderPlannerSettings = defenderPlannerSettings;
        this.attackerProjectionPolicies = attackerProjectionPolicies;
        this.defenderProjectionPolicies = defenderProjectionPolicies;
        this.preparedStateCheckpoints = new HashMap<>();
        this.projectionScratch = new AttackScratch();
        this.projectionResult = new MutableAttackResult();
        int nationCount = scenario.attackerCount() + scenario.defenderCount();
        int attackerCountVal = scenario.attackerCount();
        int defenderCountVal = scenario.defenderCount();
        this.scratchActiveWarsByNation = new boolean[nationCount];
        this.scratchActiveOffWarsByNation = new int[nationCount];
        this.scratchActiveDefWarsByNation = new int[nationCount];
        this.scratchCounterOffSlots = new int[defenderCountVal];
        this.scratchCounterDefSlots = new int[attackerCountVal];
        this.scratchCounterDeclarerStrengths = new double[defenderCountVal];
        this.scratchCounterDeclarerActivityWeights = new double[defenderCountVal];
        this.scratchCounterTargetStrengths = new double[attackerCountVal];
        this.scratchCounterTargetActionValues = new double[attackerCountVal];
        this.scratchRedeclareAttSlots = new int[attackerCountVal];
        this.scratchRedeclareDefSlots = new int[defenderCountVal];
        this.scratchRedeclareAttackerStrengths = new double[attackerCountVal];
        this.scratchRedeclareDefenderStrengths = new double[defenderCountVal];
        this.projectionAttackEvaluator = new ProjectionAttackEvaluator();
        this.heuristicAttackCandidate = new HeuristicAttackChoicePolicy.MutableAttackCandidate();
        this.projectionCounterEvaluator = new ProjectionCounterEvaluator();
        this.projectionRedeclareEvaluator = new ProjectionRedeclareEvaluator();
    }

    static LongHorizonForwardProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int horizonTurns,
            double horizonFactor
    ) {
        return create(
                edges,
                scenario,
                attackerCaps,
                horizonTurns,
                horizonFactor,
            null,
            null,
            null,
                SidePlannerSettings.legacy(),
                SidePlannerSettings.legacy(),
                SideProjectionPolicies.heuristic(),
                SideProjectionPolicies.heuristic()
        );
    }

    static LongHorizonForwardProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int horizonTurns,
            double horizonFactor,
            StrategicObjective projectionObjective,
            SideOpeningSettings attackerOpeningSettings,
            SideOpeningSettings defenderOpeningSettings,
            SidePlannerSettings attackerPlannerSettings,
            SidePlannerSettings defenderPlannerSettings,
            SideProjectionPolicies attackerProjectionPolicies,
            SideProjectionPolicies defenderProjectionPolicies
    ) {
        double[] attackerInitialScores = new double[scenario.attackerCount()];
        double[] defenderInitialScores = new double[scenario.defenderCount()];
        double[] attackerProjectedBuyScore = new double[scenario.attackerCount()];
        double[] defenderProjectedBuyScore = new double[scenario.defenderCount()];
        double[] attackerCombatStrengths = new double[scenario.attackerCount()];
        double[] defenderCombatStrengths = new double[scenario.defenderCount()];

        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            DBNationSnapshot attacker = scenario.attacker(attackerIndex);
            attackerInitialScores[attackerIndex] = strategicAssetValue(attacker, scenario, true);
            attackerProjectedBuyScore[attackerIndex] = projectedBuyValue(attacker, horizonTurns);
            attackerCombatStrengths[attackerIndex] = combatStrength(attacker);
        }
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            DBNationSnapshot defender = scenario.defender(defenderIndex);
            defenderInitialScores[defenderIndex] = strategicAssetValue(defender, scenario, false);
            defenderProjectedBuyScore[defenderIndex] = projectedBuyValue(defender, horizonTurns);
            defenderCombatStrengths[defenderIndex] = combatStrength(defender);
        }

        LongHorizonCounterOpportunityModel counterOpportunityModel = LongHorizonCounterOpportunityModel.create(
                scenario,
                attackerInitialScores,
                attackerProjectedBuyScore,
                attackerCombatStrengths,
                defenderCombatStrengths,
                horizonFactor
        );
        return new LongHorizonForwardProjection(
                edges,
                scenario,
                Math.max(1, horizonTurns),
                horizonFactor,
                attackerInitialScores,
                defenderInitialScores,
                attackerProjectedBuyScore,
                defenderProjectedBuyScore,
                attackerCombatStrengths,
                defenderCombatStrengths,
                counterOpportunityModel,
                Arrays.copyOf(attackerCaps, attackerCaps.length),
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies
        );
    }

    static LongHorizonCounterOpportunityModel counterOpportunityModel(
            CompiledScenario scenario,
            int horizonTurns,
            double horizonFactor
    ) {
        double[] attackerInitialScores = new double[scenario.attackerCount()];
        double[] attackerProjectedBuyScore = new double[scenario.attackerCount()];
        double[] attackerCombatStrengths = new double[scenario.attackerCount()];
        double[] defenderCombatStrengths = new double[scenario.defenderCount()];
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            DBNationSnapshot attacker = scenario.attacker(attackerIndex);
            attackerInitialScores[attackerIndex] = strategicAssetValue(attacker, scenario, true);
            attackerProjectedBuyScore[attackerIndex] = projectedBuyValue(attacker, horizonTurns);
            attackerCombatStrengths[attackerIndex] = combatStrength(attacker);
        }
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            defenderCombatStrengths[defenderIndex] = combatStrength(scenario.defender(defenderIndex));
        }
        return LongHorizonCounterOpportunityModel.create(
                scenario,
                attackerInitialScores,
                attackerProjectedBuyScore,
                attackerCombatStrengths,
                defenderCombatStrengths,
                horizonFactor
        );
    }

    double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore) {
        return counterOpportunityModel.attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore, attackerCaps);
    }

    double counterOpportunityScore(int[] attackerCounts) {
        return counterOpportunityModel.counterOpportunityScore(attackerCounts, attackerCaps);
    }

    LongHorizonCounterOpportunityModel counterOpportunityModel() {
        return counterOpportunityModel;
    }

    double projectedObjectiveScore(
            StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        return objective.scoreTerminal(project(edgeAssigned, attackerCounts, defenderCounts), teamId);
    }

    ProjectionView project(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        return project(edgeAssigned, attackerCounts, defenderCounts, null);
    }

    ProjectedEvaluation projectedEvaluation(
            StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        int[] counterIncidence = new int[scenario.attackerCount()];
        ProjectionView view = project(edgeAssigned, attackerCounts, defenderCounts, counterIncidence);
        return new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence);
    }

    ProjectedFeedbackEvaluation projectedFeedbackEvaluation(
            StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int midHorizonTurns
    ) {
        int turns = Math.max(1, Math.min(horizonTurns, midHorizonTurns));
        int[] counterIncidence = new int[scenario.attackerCount()];
        resetProjectedEvaluationProfile();
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        warState.fillActiveWarsByNation(scratchActiveWarsByNation);
        MidHorizonBaseline baseline = captureMidHorizonBaseline(state);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, 0, turns);
        MidHorizonSnapshot midHorizonSnapshot = captureMidHorizonSnapshot(state, baseline, counterIncidence);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, turns, horizonTurns);
        flushProjectedEvaluationProfile();
        ProjectionView view = new ProjectionView(state, warState, edgeAssigned);
        return new ProjectedFeedbackEvaluation(
            new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence),
            midHorizonSnapshot
        );
        }

    ProjectionDiagnostics projectionDiagnostics(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        ProjectionView view = project(edgeAssigned, attackerCounts, defenderCounts, null, true);
        return view.diagnostics();
    }

    private ProjectionView project(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut
    ) {
        return project(edgeAssigned, attackerCounts, defenderCounts, counterIncidenceOut, false);
    }

    private ProjectionView project(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut,
            boolean collectDiagnostics
    ) {
        resetProjectedEvaluationProfile();
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, collectDiagnostics);
        DenseWarState warState = prepareWarState(edgeAssigned);
        simulateProjectedWars(state, warState, attackerCounts, defenderCounts, counterIncidenceOut);
        flushProjectedEvaluationProfile();
        return new ProjectionView(
                state,
                warState,
                edgeAssigned
        );
    }

    /**
     * Runs forward projection and returns the per-opening-attacker count of projected counter
     * declarations against each attacker. Allocates only the output array, the projection state,
     * and the war state used to advance combat.
     */
    int[] realizedCounterIncidence(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        int[] counterIncidence = new int[scenario.attackerCount()];
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        simulateProjectedWars(state, warState, attackerCounts, defenderCounts, counterIncidence);
        return counterIncidence;
    }

    /**
     * Captures dense per-attacker / per-defender combat state after running the forward projection
     * forward by {@code midHorizonTurns} turns. Used by the optimizer to rebuild candidate edge
     * components (immediate harm, control leverage, future-war leverage) from real projected
     * mid-horizon nation state rather than from a fixed scalar penalty.
     *
     * <p>The midHorizonTurns budget is clamped to [1, horizonTurns]. The returned snapshot retains
     * baseline (pre-simulation) and post-projection combat strengths and scores per nation slot, plus
     * the realized counter incidence count for each opening-attacker.
     */
    MidHorizonSnapshot snapshotMidHorizonState(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int midHorizonTurns
    ) {
        int turns = Math.max(1, Math.min(horizonTurns, midHorizonTurns));
        int[] counterIncidence = new int[scenario.attackerCount()];
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        warState.fillActiveWarsByNation(scratchActiveWarsByNation);
        MidHorizonBaseline baseline = captureMidHorizonBaseline(state);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, 0, turns);
        return captureMidHorizonSnapshot(state, baseline, counterIncidence);
    }

    /**
     * Default mid-horizon turn target used when the caller does not specify one. Picks the lesser
     * of half the horizon and {@link #WAR_EXPIRATION_TURN}, which captures consequences of the first
     * counter wave before opening wars expire and re-declarations begin.
     */
    int defaultMidHorizonTurns() {
        return Math.max(1, Math.min(horizonTurns, Math.min(WAR_EXPIRATION_TURN, Math.max(1, horizonTurns / 2))));
    }

    private void simulateProjectedWars(
            ProjectionState state,
            DenseWarState warState,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut
    ) {
        simulateProjectedWarsForTurns(state, warState, attackerCounts, defenderCounts, counterIncidenceOut, horizonTurns);
    }

    private ProjectionState ensureProjectionState(boolean collectDiagnostics) {
        if (projectionState == null) {
            projectionState = ProjectionState.from(scenario);
            projectionState.collectDiagnostics(collectDiagnostics);
        } else {
            projectionState.collectDiagnostics(collectDiagnostics);
        }
        return projectionState;
    }

    private DenseWarState ensureWarState() {
        if (warState == null) {
            warState = DenseWarState.create(edges, projectionState,
                    edges.edgeCount() + scenario.activeWars().size() + Math.max(maxProjectedExtraDeclareCapacity(), 1));
        }
        return warState;
    }

    private ProjectionState prepareProjectionState(int[] attackerCounts, int[] defenderCounts, boolean collectDiagnostics) {
        ProjectionState state = ensureProjectionState(collectDiagnostics);
        ActiveWarProfileKey key = activeWarProfileKey(attackerCounts, defenderCounts);
        ProjectionStateCheckpoint checkpoint = preparedStateCheckpoints.get(key);
        if (checkpoint == null) {
            state.resetMutableState();
            fillActiveWarsByNationProfile(attackerCounts, defenderCounts, scratchActiveWarsByNation);
            state.materializePendingBuys();
            state.applyDailyBuys(false, scratchActiveWarsByNation);
            checkpoint = state.captureCheckpoint();
            preparedStateCheckpoints.put(key, checkpoint);
            profiledPreparedStateProfiles++;
        } else {
            state.restoreCheckpoint(checkpoint);
            profiledPreparedStateRestores++;
        }
        return state;
    }

    private DenseWarState prepareWarState(boolean[] edgeAssigned) {
        DenseWarState state = ensureWarState();
        if (preparedWarTemplateCheckpoint == null) {
            state.initializeOpeningTemplate(edges, projectionState, maxProjectedExtraDeclareCapacity());
            preparedWarTemplateCheckpoint = state.captureCheckpoint();
            profiledPreparedWarTemplateBuilds++;
        } else {
            state.restoreCheckpoint(preparedWarTemplateCheckpoint);
            profiledPreparedWarRestores++;
        }
        state.applyOpeningAssignment(edgeAssigned);
        state.appendSeedWars(projectionState, scenario.activeWars());
        return state;
    }

    private void simulateProjectedWarsForTurns(
            ProjectionState state,
            DenseWarState warState,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut,
            int turnsToRun
    ) {
        warState.fillActiveWarsByNation(scratchActiveWarsByNation);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidenceOut, 0, turnsToRun);
    }

    private void runProjectionTurns(
            ProjectionState state,
            DenseWarState warState,
            boolean[] activeWarsByNation,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut,
            int startTurn,
            int endTurnExclusive
    ) {
        DenseWarContext context = new DenseWarContext(state, warState);
        int start = Math.max(0, Math.min(horizonTurns, startTurn));
        int bound = Math.max(start, Math.min(horizonTurns, endTurnExclusive));
        for (int turn = start; turn < bound; turn++) {
            profiledProjectionTurns++;
            if (turn > 0) {
                advanceTurn(state, warState, turn, activeWarsByNation);
            }
            if (shouldDeclareProjectedCounters(turn, attackerCounts, defenderCounts)) {
                declareProjectedCounters(state, warState, attackerCounts, turn, counterIncidenceOut);
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            if (shouldDeclareProjectedRedeclares(warState)) {
                declareProjectedAttackerRedeclares(state, warState, turn);
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                profiledWarIterations++;
                context.setWarIndex(warIndex);
                simulateAdaptiveAttacks(state, warState, context, projectionScratch, projectionResult);
            }
            if (state.collectDiagnostics) {
                // Strategic control per turn: measured by resistance edge across all active wars.
                // Attacker controls a war when the defender's resistance is lower (being drained faster).
                // Net leverage = sum over wars of (defenderResistance - attackerResistance);
                // positive = attacker side holds net control, negative = defender side holds net control.
                int netResistanceEdge = 0;
                for (int wi = 0; wi < warState.warCount; wi++) {
                    if (!warState.active[wi]) continue;
                    netResistanceEdge += warState.defenderResistance[wi] - warState.attackerResistance[wi];
                }
                if (netResistanceEdge > 0) {
                    state.turnsAttackerHeldNetControl++;
                } else if (netResistanceEdge < 0) {
                    state.turnsDefenderHeldNetControl++;
                } else {
                    state.turnsNoControl++;
                }
            }
        }
    }

    private void fillActiveWarsByNationProfile(int[] attackerCounts, int[] defenderCounts, boolean[] activeWarsByNation) {
        Arrays.fill(activeWarsByNation, false);
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            activeWarsByNation[attackerIndex] = attackerCounts[attackerIndex] > 0;
        }
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            activeWarsByNation[scenario.attackerCount() + defenderIndex] = defenderCounts[defenderIndex] > 0;
        }
    }

    private ActiveWarProfileKey activeWarProfileKey(int[] attackerCounts, int[] defenderCounts) {
        boolean[] attackerActive = new boolean[attackerCounts.length];
        boolean[] defenderActive = new boolean[defenderCounts.length];
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            attackerActive[attackerIndex] = attackerCounts[attackerIndex] > 0;
        }
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            defenderActive[defenderIndex] = defenderCounts[defenderIndex] > 0;
        }
        return new ActiveWarProfileKey(attackerActive, defenderActive);
    }

    private MidHorizonBaseline captureMidHorizonBaseline(ProjectionState state) {
        int attackerCount = scenario.attackerCount();
        int defenderCount = scenario.defenderCount();
        double[] attackerBaselineStrengths = new double[attackerCount];
        double[] defenderBaselineStrengths = new double[defenderCount];
        double[] attackerBaselineScores = new double[attackerCount];
        double[] defenderBaselineScores = new double[defenderCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            attackerBaselineStrengths[attackerIndex] = state.combatStrength(attackerIndex);
            attackerBaselineScores[attackerIndex] = state.score(attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            int nationIndex = state.attackerCount + defenderIndex;
            defenderBaselineStrengths[defenderIndex] = state.combatStrength(nationIndex);
            defenderBaselineScores[defenderIndex] = state.score(nationIndex);
        }
        return new MidHorizonBaseline(
                attackerBaselineStrengths,
                defenderBaselineStrengths,
                attackerBaselineScores,
                defenderBaselineScores
        );
    }

    private MidHorizonSnapshot captureMidHorizonSnapshot(
            ProjectionState state,
            MidHorizonBaseline baseline,
            int[] counterIncidence
    ) {
        int attackerCount = scenario.attackerCount();
        int defenderCount = scenario.defenderCount();
        double[] attackerStrengths = new double[attackerCount];
        double[] defenderStrengths = new double[defenderCount];
        double[] attackerScores = new double[attackerCount];
        double[] defenderScores = new double[defenderCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            attackerStrengths[attackerIndex] = state.combatStrength(attackerIndex);
            attackerScores[attackerIndex] = state.score(attackerIndex);
        }
        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            int nationIndex = state.attackerCount + defenderIndex;
            defenderStrengths[defenderIndex] = state.combatStrength(nationIndex);
            defenderScores[defenderIndex] = state.score(nationIndex);
        }
        return new MidHorizonSnapshot(
                baseline.attackerStrengthsBaseline(),
                attackerStrengths,
                baseline.defenderStrengthsBaseline(),
                defenderStrengths,
                baseline.attackerScoresBaseline(),
                attackerScores,
                baseline.defenderScoresBaseline(),
                defenderScores,
                counterIncidence.clone()
        );
    }

    private boolean shouldDeclareProjectedCounters(int turn, int[] attackerCounts, int[] defenderCounts) {
        if (horizonTurns <= PROJECTED_COUNTER_START_TURN || turn < PROJECTED_COUNTER_START_TURN) {
            return false;
        }
        int attackedAttackers = 0;
        for (int count : attackerCounts) {
            if (count > 0) {
                attackedAttackers++;
            }
        }
        if (attackedAttackers == 0) {
            return false;
        }
        for (int count : defenderCounts) {
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    private void declareProjectedCounters(
            ProjectionState state,
            DenseWarState warState,
            int[] attackerCounts,
            int turn,
            int[] counterIncidenceOut
    ) {
        profiledCounterTurns++;
        SidePlannerSettings counterPlannerSettings = defenderPlannerSettings;
        warState.fillActiveWarCounts(scratchActiveOffWarsByNation, scratchActiveDefWarsByNation);
        fillProjectedCounterOffensiveSlots(
            state,
            scratchActiveOffWarsByNation,
            scratchCounterOffSlots,
            counterPlannerSettings.activityActThreshold()
        );
        fillProjectedCounterTargetDefensiveSlots(state, scratchActiveDefWarsByNation, scratchCounterDefSlots);
        int[] remainingCounterOffensiveSlots = scratchCounterOffSlots;
        int[] remainingTargetDefensiveSlots = scratchCounterDefSlots;
        if (!hasAnyAvailable(remainingCounterOffensiveSlots) || !hasAnyAvailable(remainingTargetDefensiveSlots)) {
            profiledCounterTurnsNoSlots++;
            return;
        }
        ProjectedLaterDeclarationInputs inputs = buildProjectedCounterDeclarationInputs(
                state,
                warState,
                remainingCounterOffensiveSlots,
                remainingTargetDefensiveSlots,
                counterPlannerSettings
        );
        if (inputs == null) {
            profiledCounterTurnsNoSlots++;
            return;
        }
        profiledCounterDeclarations += applyProjectedLaterDeclarationPlan(
                warState,
                inputs,
                counterPlannerSettings,
                turn,
            counterIncidenceOut,
            counterPlannerSettings.maxCountersPerTurn()
        );
    }

    private int projectedExtraDeclareCapacity(int[] attackerCounts) {
        int counterTargetCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] > 0) {
                counterTargetCapacity += scenario.attacker(attackerIndex).rawFreeDef();
            }
        }
        int counterDeclarerCapacity = 0;
        int redeclareTargetCapacity = 0;
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            counterDeclarerCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeOff());
            redeclareTargetCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeDef());
        }
        int redeclareDeclarerCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            redeclareDeclarerCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeOff());
        }
        int counterSimultaneous = Math.min(counterTargetCapacity, counterDeclarerCapacity);
        int redeclareSimultaneous = Math.min(redeclareDeclarerCapacity, redeclareTargetCapacity);
        int slotReuseCycles = Math.max(1, 1 + (horizonTurns / WAR_EXPIRATION_TURN));
        return (counterSimultaneous + redeclareSimultaneous) * slotReuseCycles;
    }

    private int maxProjectedExtraDeclareCapacity() {
        int counterTargetCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            counterTargetCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeDef());
        }
        int counterDeclarerCapacity = 0;
        int redeclareTargetCapacity = 0;
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            counterDeclarerCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeOff());
            redeclareTargetCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeDef());
        }
        int redeclareDeclarerCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            redeclareDeclarerCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeOff());
        }
        int counterSimultaneous = Math.min(counterTargetCapacity, counterDeclarerCapacity);
        int redeclareSimultaneous = Math.min(redeclareDeclarerCapacity, redeclareTargetCapacity);
        int slotReuseCycles = Math.max(1, 1 + (horizonTurns / WAR_EXPIRATION_TURN));
        return (counterSimultaneous + redeclareSimultaneous) * slotReuseCycles;
    }

    private static boolean hasAnyAvailable(int[] slots) {
        for (int slotCount : slots) {
            if (slotCount > 0) {
                return true;
            }
        }
        return false;
    }



    private boolean shouldDeclareProjectedRedeclares(DenseWarState warState) {
        return edges.edgeCount() > 0 && warState.warCount > 0;
    }

    private void declareProjectedAttackerRedeclares(
            ProjectionState state,
            DenseWarState warState,
            int turn
    ) {
        profiledRedeclareTurns++;
        SidePlannerSettings redeclarePlannerSettings = attackerPlannerSettings;
        int attackerCount = scenario.attackerCount();
        int defenderCount = scenario.defenderCount();
        warState.fillActiveWarCounts(scratchActiveOffWarsByNation, scratchActiveDefWarsByNation);
        int[] activeOff = scratchActiveOffWarsByNation;
        int[] activeDef = scratchActiveDefWarsByNation;

        Arrays.fill(scratchRedeclareAttSlots, 0);
        int[] remainingAttackerSlots = scratchRedeclareAttSlots;
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            int rawFreeOff = scenario.attacker(attackerIndex).rawFreeOff();
            remainingAttackerSlots[attackerIndex] = Math.max(0,
                    Math.min(attackerCaps[attackerIndex], rawFreeOff) - activeOff[attackerIndex]);
        }
        Arrays.fill(scratchRedeclareDefSlots, 0);
        int[] remainingDefenderSlots = scratchRedeclareDefSlots;
        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            int defenderNationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[defenderNationIndex] > 0 || state.combatStrength(defenderNationIndex) <= 0d) {
                continue;
            }
            remainingDefenderSlots[defenderIndex] = Math.max(0,
                    scenario.defender(defenderIndex).rawFreeDef() - activeDef[defenderNationIndex]);
        }
        if (!hasAnyAvailable(remainingAttackerSlots) || !hasAnyAvailable(remainingDefenderSlots)) {
            profiledRedeclareTurnsNoSlots++;
            return;
        }
        ProjectedLaterDeclarationInputs inputs = buildProjectedRedeclareDeclarationInputs(
                state,
                warState,
                remainingAttackerSlots,
                remainingDefenderSlots,
                redeclarePlannerSettings,
                turn
        );
        if (inputs == null) {
            profiledRedeclareTurnsNoSlots++;
            return;
        }
        profiledRedeclarations += applyProjectedLaterDeclarationPlan(
            warState,
            inputs,
            redeclarePlannerSettings,
            turn,
            null,
            Integer.MAX_VALUE
        );
    }

    private ProjectedLaterDeclarationInputs buildProjectedCounterDeclarationInputs(
            ProjectionState state,
            DenseWarState warState,
            int[] remainingCounterOffensiveSlots,
            int[] remainingTargetDefensiveSlots,
            SidePlannerSettings counterPlannerSettings
    ) {
        List<DBNationSnapshot> declarerSnapshots = new ArrayList<>();
        List<DBNationSnapshot> targetSnapshots = new ArrayList<>();
        int[] declarerOverallIndexes = new int[scenario.defenderCount()];
        int[] targetOverallIndexes = new int[scenario.attackerCount()];
        Arrays.fill(declarerOverallIndexes, -1);
        Arrays.fill(targetOverallIndexes, -1);

        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            if (remainingCounterOffensiveSlots[defenderIndex] <= 0) {
                continue;
            }
            int counterNationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[counterNationIndex] > 0 || state.combatStrength(counterNationIndex) <= 0d) {
                continue;
            }
            declarerOverallIndexes[declarerSnapshots.size()] = counterNationIndex;
            declarerSnapshots.add(projectedSnapshot(state, warState, counterNationIndex));
        }
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            if (remainingTargetDefensiveSlots[attackerIndex] <= 0) {
                continue;
            }
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            targetOverallIndexes[targetSnapshots.size()] = attackerIndex;
            targetSnapshots.add(projectedSnapshot(state, warState, attackerIndex));
        }
        if (declarerSnapshots.isEmpty() || targetSnapshots.isEmpty()) {
            return null;
        }

        CompiledScenario projectedScenario = PROJECTED_DECLARATION_SCENARIO_COMPILER.compile(
                declarerSnapshots,
                targetSnapshots,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable projectedEdges = new CandidateEdgeTable();
        for (int declarerCompiledIndex = 0; declarerCompiledIndex < declarerSnapshots.size(); declarerCompiledIndex++) {
            int declarerNationIndex = declarerOverallIndexes[declarerCompiledIndex];
            int originalDefenderIndex = declarerNationIndex - state.attackerCount;
            double activityWeight = scenario.defenderActivityWeight(originalDefenderIndex);
            double counterStrength = state.combatStrength(declarerNationIndex);
            for (int targetCompiledIndex = 0; targetCompiledIndex < targetSnapshots.size(); targetCompiledIndex++) {
                int targetNationIndex = targetOverallIndexes[targetCompiledIndex];
                profiledCounterCandidateEvaluations++;
                double targetActionValue = state.marginalActionSpaceValue(targetNationIndex, warState);
                double score = HeuristicCounterDeclarationPolicy.projectedCounterScore(
                        !warState.hasActivePair(declarerNationIndex, targetNationIndex)
                                && state.beigeTurns[declarerNationIndex] <= 0
                                && state.beigeTurns[targetNationIndex] <= 0
                                && canCounterProjected(state, declarerNationIndex, targetNationIndex),
                        activityWeight,
                        counterStrength,
                        state.combatStrength(targetNationIndex),
                        targetActionValue,
                        remainingTargetDefensiveSlots[targetNationIndex]
                );
                if (score <= counterPlannerSettings.counterScoreThreshold()) {
                    continue;
                }
                projectedEdges.add(
                        declarerCompiledIndex,
                        targetCompiledIndex,
                        (byte) WarType.ORD.ordinal(),
                        (byte) 0,
                        (float) score,
                        0f
                );
            }
        }
        if (projectedEdges.edgeCount() == 0) {
            return null;
        }
        return projectedLaterDeclarationInputs(projectedScenario, projectedEdges, declarerOverallIndexes, targetOverallIndexes);
    }

    private ProjectedLaterDeclarationInputs buildProjectedRedeclareDeclarationInputs(
            ProjectionState state,
            DenseWarState warState,
            int[] remainingAttackerSlots,
            int[] remainingDefenderSlots,
            SidePlannerSettings redeclarePlannerSettings,
            int turn
    ) {
        List<DBNationSnapshot> declarerSnapshots = new ArrayList<>();
        List<DBNationSnapshot> targetSnapshots = new ArrayList<>();
        Map<Integer, Integer> declarerCompiledIndexByOverall = new HashMap<>();
        Map<Integer, Integer> targetCompiledIndexByOverall = new HashMap<>();
        int[] declarerOverallIndexes = new int[scenario.attackerCount()];
        int[] targetOverallIndexes = new int[scenario.defenderCount()];
        Arrays.fill(declarerOverallIndexes, -1);
        Arrays.fill(targetOverallIndexes, -1);

        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            if (remainingAttackerSlots[attackerIndex] <= 0) {
                continue;
            }
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            int compiledIndex = declarerSnapshots.size();
            declarerOverallIndexes[compiledIndex] = attackerIndex;
            declarerCompiledIndexByOverall.put(attackerIndex, compiledIndex);
            declarerSnapshots.add(projectedSnapshot(state, warState, attackerIndex));
        }
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            if (remainingDefenderSlots[defenderIndex] <= 0) {
                continue;
            }
            int defenderNationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[defenderNationIndex] > 0 || state.combatStrength(defenderNationIndex) <= 0d) {
                continue;
            }
            int compiledIndex = targetSnapshots.size();
            targetOverallIndexes[compiledIndex] = defenderNationIndex;
            targetCompiledIndexByOverall.put(defenderNationIndex, compiledIndex);
            targetSnapshots.add(projectedSnapshot(state, warState, defenderNationIndex));
        }
        if (declarerSnapshots.isEmpty() || targetSnapshots.isEmpty()) {
            return null;
        }

        CompiledScenario projectedScenario = PROJECTED_DECLARATION_SCENARIO_COMPILER.compile(
                declarerSnapshots,
                targetSnapshots,
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );
        CandidateEdgeTable projectedEdges = new CandidateEdgeTable();
        double[] deferredBestByAttacker = new double[declarerSnapshots.size()];
        int horizonRemainingTurns = Math.max(0, horizonTurns - turn);

        CandidateEdgeTable redeclareSourceEdges = edges;

        for (int edgeIndex = 0; edgeIndex < redeclareSourceEdges.edgeCount(); edgeIndex++) {
            int attackerNationIndex;
            Integer declarerCompiledIndex;
            int defenderNationIndex;
            Integer targetCompiledIndex;
            attackerNationIndex = edges.attackerIndex(edgeIndex);
            declarerCompiledIndex = declarerCompiledIndexByOverall.get(attackerNationIndex);
            if (declarerCompiledIndex == null) {
                continue;
            }
            defenderNationIndex = state.attackerCount + edges.defenderIndex(edgeIndex);
            targetCompiledIndex = targetCompiledIndexByOverall.get(defenderNationIndex);
            if (targetCompiledIndex == null) {
                continue;
            }
            profiledRedeclareCandidateEvaluations++;
            if (warState.hasActivePair(attackerNationIndex, defenderNationIndex)) {
                continue;
            }
            int blockedTurns = redeclareBlockedTurnsProjected(state, warState, attackerNationIndex, defenderNationIndex, turn);
            if (blockedTurns <= 0) {
                continue;
            }
            double deferredScore = HeuristicRedeclarationPolicy.projectedDeferredRedeclareScore(
                    new RedeclarationPolicy.RedeclareCandidate(
                            attackerNationIndex,
                            defenderNationIndex,
                            false,
                            false,
                            state.combatStrength(attackerNationIndex),
                            state.combatStrength(defenderNationIndex),
                            scenario.attackerActivityWeight(attackerNationIndex),
                                redeclareSourceEdges.scalarScore(edgeIndex),
                            blockedTurns
                    ),
                    remainingAttackerSlots[attackerNationIndex],
                    horizonRemainingTurns
            );
            deferredBestByAttacker[declarerCompiledIndex] = Math.max(
                    deferredBestByAttacker[declarerCompiledIndex],
                    deferredScore
            );
        }

        for (int edgeIndex = 0; edgeIndex < redeclareSourceEdges.edgeCount(); edgeIndex++) {
            int attackerNationIndex;
            Integer declarerCompiledIndex;
            int defenderNationIndex;
            Integer targetCompiledIndex;
            attackerNationIndex = edges.attackerIndex(edgeIndex);
            declarerCompiledIndex = declarerCompiledIndexByOverall.get(attackerNationIndex);
            if (declarerCompiledIndex == null) {
                continue;
            }
            defenderNationIndex = state.attackerCount + edges.defenderIndex(edgeIndex);
            targetCompiledIndex = targetCompiledIndexByOverall.get(defenderNationIndex);
            if (targetCompiledIndex == null) {
                continue;
            }
            profiledRedeclareCandidateEvaluations++;
            if (warState.hasActivePair(attackerNationIndex, defenderNationIndex)) {
                continue;
            }
            int blockedTurns = redeclareBlockedTurnsProjected(state, warState, attackerNationIndex, defenderNationIndex, turn);
            double score = HeuristicRedeclarationPolicy.projectedRedeclareScore(
                    state.combatStrength(attackerNationIndex),
                    state.combatStrength(defenderNationIndex),
                    scenario.attackerActivityWeight(attackerNationIndex),
                    redeclareSourceEdges.scalarScore(edgeIndex),
                    remainingAttackerSlots[attackerNationIndex]
            );
            if (blockedTurns > 0
                    || score <= redeclarePlannerSettings.redeclareScoreThreshold()
                    || score <= deferredBestByAttacker[declarerCompiledIndex]) {
                continue;
            }
            projectedEdges.add(
                    declarerCompiledIndex,
                    targetCompiledIndex,
                    redeclareSourceEdges.preferredWarTypeId(edgeIndex),
                    redeclareSourceEdges.bestAttackTypeId(edgeIndex),
                    (float) score,
                    redeclareSourceEdges.counterRisk(edgeIndex)
            );
        }
        if (projectedEdges.edgeCount() == 0) {
            return null;
        }
        return projectedLaterDeclarationInputs(projectedScenario, projectedEdges, declarerOverallIndexes, targetOverallIndexes);
    }


    private int applyProjectedLaterDeclarationPlan(
            DenseWarState warState,
            ProjectedLaterDeclarationInputs inputs,
            SidePlannerSettings plannerSettings,
            int turn,
            int[] counterIncidenceOut
    ) {
        PlannerAutonomousCounterPlanner.Plan plan = PlannerAutonomousCounterPlanner.planScorerOnly(
                inputs.scenario(),
                inputs.edges(),
                plannerSettings,
                Math.max(1, horizonTurns - turn)
        );
        int declarations = 0;
        for (Map.Entry<Integer, List<Integer>> entry : plan.assignment().entrySet()) {
            Integer declarerNationIndex = inputs.declarerOverallIndexesByNationId().get(entry.getKey());
            if (declarerNationIndex == null) {
                continue;
            }
            for (int targetNationId : entry.getValue()) {
                Integer targetNationIndex = inputs.targetOverallIndexesByNationId().get(targetNationId);
                if (targetNationIndex == null) {
                    continue;
                }
                warState.addWar(
                        declarerNationIndex,
                        targetNationIndex,
                        turn,
                        warTypeFromOrdinal(plan.warTypeOrdinal(entry.getKey(), targetNationId))
                );
                declarations++;
                if (counterIncidenceOut != null && targetNationIndex >= 0 && targetNationIndex < scenario.attackerCount()) {
                    counterIncidenceOut[targetNationIndex]++;
                }
            }
        }
        return declarations;
    }

    private int applyProjectedLaterDeclarationPlan(
            DenseWarState warState,
            ProjectedLaterDeclarationInputs inputs,
            SidePlannerSettings plannerSettings,
            int turn,
            int[] counterIncidenceOut,
            int maxDeclarations
    ) {
        PlannerAutonomousCounterPlanner.Plan plan = PlannerAutonomousCounterPlanner.planScorerOnly(
            inputs.scenario(),
            inputs.edges(),
            plannerSettings,
            Math.max(1, horizonTurns - turn)
        );
        List<ProjectedAssignedDeclaration> selectedDeclarations = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : plan.assignment().entrySet()) {
            Integer declarerNationIndex = inputs.declarerOverallIndexesByNationId().get(entry.getKey());
            if (declarerNationIndex == null) {
                continue;
            }
            for (int targetNationId : entry.getValue()) {
                Integer targetNationIndex = inputs.targetOverallIndexesByNationId().get(targetNationId);
                if (targetNationIndex == null) {
                    continue;
                }
                Integer edgeIndex = inputs.edgeIndexByPair().get(projectedPairKey(entry.getKey(), targetNationId));
                if (edgeIndex == null) {
                    continue;
                }
                selectedDeclarations.add(new ProjectedAssignedDeclaration(
                        entry.getKey(),
                        targetNationId,
                        declarerNationIndex,
                        targetNationIndex,
                        edgeIndex
                ));
            }
        }
        selectedDeclarations.sort((left, right) -> Float.compare(
                inputs.edges().scalarScore(right.edgeIndex()),
                inputs.edges().scalarScore(left.edgeIndex())
        ));
        int declarations = 0;
        int declarationLimit = Math.min(Math.max(0, maxDeclarations), selectedDeclarations.size());
        for (int declarationIndex = 0; declarationIndex < declarationLimit; declarationIndex++) {
            ProjectedAssignedDeclaration declaration = selectedDeclarations.get(declarationIndex);
            warState.addWar(
                    declaration.declarerNationIndex(),
                    declaration.targetNationIndex(),
                    turn,
                    warTypeFromOrdinal(plan.warTypeOrdinal(declaration.declarerNationId(), declaration.targetNationId()))
            );
            declarations++;
            if (counterIncidenceOut != null
                    && declaration.targetNationIndex() >= 0
                    && declaration.targetNationIndex() < scenario.attackerCount()) {
                counterIncidenceOut[declaration.targetNationIndex()]++;
            }
        }
        return declarations;
    }


    private long projectedPairKey(int declarerNationId, int targetNationId) {
        return ((long) declarerNationId << 32) ^ (targetNationId & 0xffffffffL);
    }

    private ProjectedLaterDeclarationInputs projectedLaterDeclarationInputs(
            CompiledScenario projectedScenario,
            CandidateEdgeTable projectedEdges,
            int[] declarerOverallIndexes,
            int[] targetOverallIndexes
    ) {
        Map<Integer, Integer> declarerOverallIndexesByNationId = new HashMap<>();
        for (int attackerIndex = 0; attackerIndex < projectedScenario.attackerCount(); attackerIndex++) {
            declarerOverallIndexesByNationId.put(
                    projectedScenario.attackerNationId(attackerIndex),
                    declarerOverallIndexes[attackerIndex]
            );
        }
        Map<Integer, Integer> targetOverallIndexesByNationId = new HashMap<>();
        for (int defenderIndex = 0; defenderIndex < projectedScenario.defenderCount(); defenderIndex++) {
            targetOverallIndexesByNationId.put(
                    projectedScenario.defenderNationId(defenderIndex),
                    targetOverallIndexes[defenderIndex]
            );
        }
        Map<Long, Integer> edgeIndexByPair = new HashMap<>();
        for (int edgeIndex = 0; edgeIndex < projectedEdges.edgeCount(); edgeIndex++) {
            edgeIndexByPair.put(
                    projectedPairKey(
                            projectedScenario.attackerNationId(projectedEdges.attackerIndex(edgeIndex)),
                            projectedScenario.defenderNationId(projectedEdges.defenderIndex(edgeIndex))
                    ),
                    edgeIndex
            );
        }
        return new ProjectedLaterDeclarationInputs(
                projectedScenario,
                projectedEdges,
                declarerOverallIndexesByNationId,
                targetOverallIndexesByNationId,
                edgeIndexByPair
        );
    }

    private DBNationSnapshot projectedSnapshot(ProjectionState state, DenseWarState warState, int nationIndex) {
        DBNationSnapshot baselineSnapshot = nationIndex < scenario.attackerCount()
                ? scenario.attacker(nationIndex)
                : scenario.defender(nationIndex - scenario.attackerCount());
        DBNationSnapshot.Builder builder = baselineSnapshot.toBuilder()
                .score(state.score(nationIndex))
                .currentOffensiveWars(effectiveOffensiveWars(warState, nationIndex, baselineSnapshot.currentOffensiveWars()))
                .currentDefensiveWars(effectiveDefensiveWars(warState, nationIndex, baselineSnapshot.currentDefensiveWars()))
                .activeOpponentNationIds(projectedActiveOpponentNationIds(warState, nationIndex, baselineSnapshot.activeOpponentNationIds()))
                .beigeTurns(Math.max(0, state.beigeTurns[nationIndex]))
                .cityInfra(projectedCityInfra(state, nationIndex));
        double[] resources = new double[ResourceType.values.length];
        System.arraycopy(state.resourcesFlat, state.resourceBaseOffsets[nationIndex], resources, 0, resources.length);
        builder.resources(resources);
        int unitBase = state.unitBaseOffsets[nationIndex];
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int unitIndex = unitBase + unit.ordinal();
            builder.unit(unit, Math.max(0, state.unitsFlat[unitIndex]));
            builder.unitBoughtToday(unit, Math.max(0, state.unitsBoughtTodayFlat[unitIndex]));
            builder.pendingBuyNextTurn(unit, Math.max(0, state.pendingBuysFlat[unitIndex]));
        }
        return builder.build();
    }

    private double[] projectedCityInfra(ProjectionState state, int nationIndex) {
        int cityCount = state.cityCounts[nationIndex];
        double[] cityInfra = new double[cityCount];
        System.arraycopy(state.cityInfraFlat, state.cityInfraBaseOffsets[nationIndex], cityInfra, 0, cityCount);
        return cityInfra;
    }

    private Set<Integer> projectedActiveOpponentNationIds(
            DenseWarState warState,
            int nationIndex,
            Set<Integer> baselineOpponents
    ) {
        Set<Integer> activeOpponents = new HashSet<>(baselineOpponents);
        for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
            if (!warState.active[warIndex]) {
                continue;
            }
            if (warState.attackerNationIndex[warIndex] == nationIndex) {
                activeOpponents.add(nationIdForIndex(warState.defenderNationIndex[warIndex]));
            } else if (warState.defenderNationIndex[warIndex] == nationIndex) {
                activeOpponents.add(nationIdForIndex(warState.attackerNationIndex[warIndex]));
            }
        }
        return activeOpponents;
    }

    private int effectiveOffensiveWars(DenseWarState warState, int nationIndex, int baselineOffensiveWars) {
        int seeded = 0;
        int projected = 0;
        for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
            if (!warState.active[warIndex] || warState.attackerNationIndex[warIndex] != nationIndex) {
                continue;
            }
            if (warState.seededCurrentWar[warIndex]) {
                seeded++;
            } else {
                projected++;
            }
        }
        return Math.max(Math.max(0, baselineOffensiveWars), seeded) + projected;
    }

    private int effectiveDefensiveWars(DenseWarState warState, int nationIndex, int baselineDefensiveWars) {
        int seeded = 0;
        int projected = 0;
        for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
            if (!warState.active[warIndex] || warState.defenderNationIndex[warIndex] != nationIndex) {
                continue;
            }
            if (warState.seededCurrentWar[warIndex]) {
                seeded++;
            } else {
                projected++;
            }
        }
        return Math.max(Math.max(0, baselineDefensiveWars), seeded) + projected;
    }

    private int nationIdForIndex(int nationIndex) {
        return nationIndex < scenario.attackerCount()
                ? scenario.attackerNationId(nationIndex)
                : scenario.defenderNationId(nationIndex - scenario.attackerCount());
    }

    private static WarType warTypeFromOrdinal(int ordinal) {
        WarType[] values = WarType.values;
        if (ordinal < 0 || ordinal >= values.length) {
            return WarType.ORD;
        }
        return values[ordinal];
    }

    private static int redeclareBlockedTurnsProjected(
            ProjectionState state,
            DenseWarState warState,
            int attackerNationIndex,
            int targetNationIndex,
            int currentTurn
    ) {
        if (state.nationIds[attackerNationIndex] == state.nationIds[targetNationIndex]) {
            return -1;
        }
        double attackerScore = state.score(attackerNationIndex);
        double targetScore = state.score(targetNationIndex);
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        if (targetScore < minScore || targetScore > maxScore) {
            return -1;
        }
        return StrategicTimingValue.redeclareBlockedTurns(
                state.beigeTurns[attackerNationIndex],
                state.beigeTurns[targetNationIndex],
                warState.lockoutTurnsRemaining(attackerNationIndex, targetNationIndex, currentTurn)
        );
    }

    private void fillProjectedCounterOffensiveSlots(
            ProjectionState state,
            int[] activeOffensiveWarsByNation,
            int[] slots,
            double activityThreshold
    ) {
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            int nationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[nationIndex] > 0
                    || state.combatStrength(nationIndex) <= 0d
                    || scenario.defenderActivityWeight(defenderIndex) < activityThreshold) {
                slots[defenderIndex] = 0;
                continue;
            }
            slots[defenderIndex] = Math.max(0,
                    scenario.defender(defenderIndex).rawFreeOff() - activeOffensiveWarsByNation[nationIndex]);
        }
    }

    private void fillProjectedCounterTargetDefensiveSlots(ProjectionState state, int[] activeDefensiveWarsByNation, int[] slots) {
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                slots[attackerIndex] = 0;
                continue;
            }
            slots[attackerIndex] = Math.max(0,
                    scenario.attacker(attackerIndex).rawFreeDef() - activeDefensiveWarsByNation[attackerIndex]);
        }
    }

    private static boolean canCounterProjected(ProjectionState state, int counterNationIndex, int targetNationIndex) {
        if (state.nationIds[counterNationIndex] == state.nationIds[targetNationIndex]) {
            return false;
        }
        double counterScore = state.score(counterNationIndex);
        double targetScore = state.score(targetNationIndex);
        double minScore = counterScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = counterScore * PW.WAR_RANGE_MAX_MODIFIER;
        return targetScore >= minScore && targetScore <= maxScore;
    }

    private void advanceTurn(ProjectionState state, DenseWarState warState, int turn, boolean[] activeWarsByNation) {
        boolean newDay = turn % DAY_TURNS == 0;
        if (newDay) {
            state.resetUnitBuysToday();
        }
        state.decrementBeigeTurns();
        for (int edgeIndex = 0; edgeIndex < warState.warCount; edgeIndex++) {
            if (!warState.active[edgeIndex]) {
                continue;
            }
            if (turn - warState.startTurn[edgeIndex] >= WAR_EXPIRATION_TURN) {
                warState.deactivateWar(edgeIndex, turn);
                continue;
            }
            warState.attackerMaps[edgeIndex] = Math.min(MAP_CAP, warState.attackerMaps[edgeIndex] + 1);
            warState.defenderMaps[edgeIndex] = Math.min(MAP_CAP, warState.defenderMaps[edgeIndex] + 1);
        }
        warState.fillActiveWarsByNation(activeWarsByNation);
        state.applyDailyBuys(newDay, activeWarsByNation);
    }

    private void simulateAdaptiveAttacks(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            AttackScratch scratch,
            MutableAttackResult result
    ) {
        while (warState.active[context.warIndex()]) {
            profiledAttackChoiceCalls++;
            AttackType attackType = chooseBestAttackType(state, warState, context, scratch, result);
            if (attackType == null) {
                return;
            }
            resolveAttack(state, warState, context, attackType, scratch, result);
        }
    }

    private AttackType chooseBestAttackType(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            AttackScratch scratch,
            MutableAttackResult result
    ) {
        int attackerNationIndex = warState.attackerNationIndex[context.warIndex()];
        int defenderNationIndex = warState.defenderNationIndex[context.warIndex()];
        CombatKernel.NationState attacker = state.nationViews[attackerNationIndex];
        int mapsAvailable = warState.attackerMaps[context.warIndex()];
        SideProjectionPolicies projectionPolicies = projectionPoliciesForAttacker(attackerNationIndex, state.attackerCount);
        if (projectionPolicies.attackChoicePolicy() == HeuristicAttackChoicePolicy.INSTANCE) {
            projectionAttackEvaluator.bind(state, warState, context, scratch, result, mapsAvailable, attackerNationIndex, defenderNationIndex, attacker);
            return HeuristicAttackChoicePolicy.INSTANCE.chooseAttackType(
                    ADAPTIVE_ATTACK_TYPES,
                    mapsAvailable,
                    projectionAttackEvaluator,
                    heuristicAttackCandidate
            );
        }
        return projectionPolicies.attackChoicePolicy().chooseAttackType(new AttackChoicePolicy.AttackChoiceContext(
                ADAPTIVE_ATTACK_TYPES,
                mapsAvailable,
                attackType -> {
                    int mapCost = attackType.getMapUsed();
                    if (mapCost <= 0 || mapCost > mapsAvailable || !CombatKernel.canUseAttackType(attacker, attackType)) {
                        return new AttackChoicePolicy.AttackCandidate(false, mapCost, 0d, 0d, 0d, SuperiorityFlagDelta.NONE);
                    }
                    CombatKernel.resolveInto(context, attackType, ResolutionMode.MOST_LIKELY, scratch, result);
                    double defenderUnitDamage = state.unitLossValue(
                            defenderNationIndex,
                            result.defenderLosses(),
                            state.activeWarContext(defenderNationIndex, warState)
                    );
                    double attackerUnitDamage = state.unitLossValue(
                            attackerNationIndex,
                            result.attackerLosses(),
                            state.activeWarContext(attackerNationIndex, warState)
                    );
                    return new AttackChoicePolicy.AttackCandidate(
                            true,
                            mapCost,
                            defenderUnitDamage,
                            attackerUnitDamage,
                            result.defenderResistanceDelta(),
                            result.controlDelta()
                    );
                }
        ));
    }

    private SideProjectionPolicies projectionPoliciesForAttacker(int attackerNationIndex, int attackerCount) {
        return attackerNationIndex < attackerCount ? attackerProjectionPolicies : defenderProjectionPolicies;
    }

    private final class ProjectionAttackEvaluator implements HeuristicAttackChoicePolicy.AttackEvaluator {
        private ProjectionState state;
        private DenseWarState warState;
        private DenseWarContext context;
        private AttackScratch scratch;
        private MutableAttackResult result;
        private int mapsAvailable;
        private int attackerNationIndex;
        private int defenderNationIndex;
        private CombatKernel.NationState attacker;
        private StrategicAssetValue.ActiveWarContext attackerActiveWarContext;
        private StrategicAssetValue.ActiveWarContext defenderActiveWarContext;

        void bind(
                ProjectionState state,
                DenseWarState warState,
                DenseWarContext context,
                AttackScratch scratch,
                MutableAttackResult result,
                int mapsAvailable,
                int attackerNationIndex,
                int defenderNationIndex,
                CombatKernel.NationState attacker
        ) {
            this.state = state;
            this.warState = warState;
            this.context = context;
            this.scratch = scratch;
            this.result = result;
            this.mapsAvailable = mapsAvailable;
            this.attackerNationIndex = attackerNationIndex;
            this.defenderNationIndex = defenderNationIndex;
            this.attacker = attacker;
            this.attackerActiveWarContext = state.activeWarContext(attackerNationIndex, warState);
            this.defenderActiveWarContext = state.activeWarContext(defenderNationIndex, warState);
        }

        @Override
        public void evaluate(AttackType attackType, HeuristicAttackChoicePolicy.MutableAttackCandidate out) {
            profiledAttackTypeEvaluations++;
            int mapCost = attackType.getMapUsed();
            if (mapCost <= 0 || mapCost > mapsAvailable || !CombatKernel.canUseAttackType(attacker, attackType)) {
                out.set(false, mapCost, 0d, 0d, 0d, SuperiorityFlagDelta.NONE);
                return;
            }
            CombatKernel.resolveInto(context, attackType, ResolutionMode.MOST_LIKELY, scratch, result);
            double defenderUnitDamage = state.unitLossValue(
                    defenderNationIndex,
                    result.defenderLosses(),
                    defenderActiveWarContext
            );
            double attackerUnitDamage = state.unitLossValue(
                    attackerNationIndex,
                    result.attackerLosses(),
                    attackerActiveWarContext
            );
            out.set(
                    true,
                    mapCost,
                    defenderUnitDamage,
                    attackerUnitDamage,
                    result.defenderResistanceDelta(),
                    result.controlDelta()
            );
        }
    }

    private final class ProjectionCounterEvaluator implements HeuristicCounterDeclarationPolicy.CounterCandidateEvaluator {
        private ProjectionState state;
        private DenseWarState warState;
        private double[] counterStrengths;
        private double[] counterActivities;
        private double[] targetStrengths;
        private double[] targetActionValues;

        void bind(
                ProjectionState state,
                DenseWarState warState,
                double[] counterStrengths,
                double[] counterActivities,
                double[] targetStrengths,
                double[] targetActionValues
        ) {
            this.state = state;
            this.warState = warState;
            this.counterStrengths = counterStrengths;
            this.counterActivities = counterActivities;
            this.targetStrengths = targetStrengths;
            this.targetActionValues = targetActionValues;
        }

        @Override
        public void evaluate(int defenderIndex, int attackerIndex, HeuristicCounterDeclarationPolicy.MutableCounterCandidate out) {
            profiledCounterCandidateEvaluations++;
            int counterNationIndex = state.attackerCount + defenderIndex;
            int targetNationIndex = attackerIndex;
            out.set(
                    state.beigeTurns[counterNationIndex] <= 0
                            && state.beigeTurns[targetNationIndex] <= 0
                            && canCounterProjected(state, counterNationIndex, targetNationIndex),
                    warState.hasActivePair(counterNationIndex, targetNationIndex),
                    counterActivities[defenderIndex],
                    counterStrengths[defenderIndex],
                    targetStrengths[attackerIndex],
                    targetActionValues[attackerIndex]
            );
        }
    }

    private final class ProjectionRedeclareEvaluator implements HeuristicRedeclarationPolicy.RedeclareCandidateEvaluator {
        private ProjectionState state;
        private DenseWarState warState;
        private double[] attackerStrengths;
        private double[] defenderStrengths;
        private int turn;

        void bind(ProjectionState state, DenseWarState warState, double[] attackerStrengths, double[] defenderStrengths, int turn) {
            this.state = state;
            this.warState = warState;
            this.attackerStrengths = attackerStrengths;
            this.defenderStrengths = defenderStrengths;
            this.turn = turn;
        }

        @Override
        public void evaluate(int edgeIndex, HeuristicRedeclarationPolicy.MutableRedeclareCandidate out) {
            profiledRedeclareCandidateEvaluations++;
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            int defenderNationIndex = state.attackerCount + defenderIndex;
            int blockedTurns = redeclareBlockedTurnsProjected(state, warState, attackerIndex, defenderNationIndex, turn);
            out.set(
                    attackerIndex,
                    defenderIndex,
                    blockedTurns == 0,
                    warState.hasActivePair(attackerIndex, defenderNationIndex),
                    attackerStrengths[attackerIndex],
                    defenderStrengths[defenderIndex],
                    scenario.attackerActivityWeight(attackerIndex),
                    edges.scalarScore(edgeIndex),
                    blockedTurns
            );
        }
    }

    private void resolveAttack(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            AttackType attackType,
            AttackScratch scratch,
            MutableAttackResult result
    ) {
        profiledResolvedAttacks++;
        int edgeIndex = context.warIndex();
        CombatKernel.resolveInto(context, attackType, ResolutionMode.MOST_LIKELY, scratch, result);
        warState.attackerMaps[edgeIndex] = Math.max(0, warState.attackerMaps[edgeIndex] - result.mapCost());
        state.applyLosses(warState.attackerNationIndex[edgeIndex], result.attackerLosses());
        state.applyLosses(warState.defenderNationIndex[edgeIndex], result.defenderLosses());
        state.applyInfraDamage(warState.defenderNationIndex[edgeIndex], result.infraDestroyed());
        if (result.attackerResistanceDelta() < 0d) {
            warState.attackerResistance[edgeIndex] = Math.max(
                    0,
                    warState.attackerResistance[edgeIndex] - (int) Math.round(-result.attackerResistanceDelta())
            );
        }
        if (result.defenderResistanceDelta() < 0d) {
            warState.defenderResistance[edgeIndex] = Math.max(
                    0,
                    warState.defenderResistance[edgeIndex] - (int) Math.round(-result.defenderResistanceDelta())
            );
        }
        if (result.loot() > 0d) {
            double transferred = state.subtractResource(warState.defenderNationIndex[edgeIndex], ResourceType.MONEY, result.loot());
            state.addResource(warState.attackerNationIndex[edgeIndex], ResourceType.MONEY, transferred);
        }
        WarControlRules.applySameWarDelta(
                context,
                state.nationIds[warState.attackerNationIndex[edgeIndex]],
                state.nationIds[warState.defenderNationIndex[edgeIndex]],
                result.controlDelta()
        );
        state.recalculateScore(warState.attackerNationIndex[edgeIndex]);
        state.recalculateScore(warState.defenderNationIndex[edgeIndex]);
        clearInvalidControls(state, warState);
        resolveDefeatIfNeeded(state, warState, edgeIndex);
    }

    private void resolveDefeatIfNeeded(ProjectionState state, DenseWarState warState, int edgeIndex) {
        if (!warState.active[edgeIndex]) {
            return;
        }
        boolean attackerLost = warState.attackerResistance[edgeIndex] <= 0;
        boolean defenderLost = warState.defenderResistance[edgeIndex] <= 0;
        if (!attackerLost && !defenderLost) {
            return;
        }
        warState.deactivateWar(edgeIndex, warState.startTurn[edgeIndex] + WAR_EXPIRATION_TURN);
        int winnerIndex = defenderLost ? warState.attackerNationIndex[edgeIndex] : warState.defenderNationIndex[edgeIndex];
        int loserIndex = defenderLost ? warState.defenderNationIndex[edgeIndex] : warState.attackerNationIndex[edgeIndex];
        warState.outcomeOwner[edgeIndex] = defenderLost ? DenseWarState.OWNER_ATTACKER : DenseWarState.OWNER_DEFENDER;
        boolean winnerIsOriginalAttacker = defenderLost;
        double infraPercent = WarOutcomeMath.victoryInfraPercent(
                state.infraAttackModifier(winnerIndex, AttackType.VICTORY),
                state.infraDefendModifier(loserIndex, AttackType.VICTORY),
                warState.warType(edgeIndex),
                winnerIsOriginalAttacker
        );
        state.applyVictoryInfraPercent(loserIndex, infraPercent);
        state.beigeTurns[loserIndex] = Math.max(state.beigeTurns[loserIndex], SimTuning.DEFAULT_BEIGE_TURNS_ON_DEFEAT);
        double transferred = WarOutcomeMath.victoryNationLootTransferAmount(
                state.resource(loserIndex, ResourceType.MONEY),
                state.looterModifier(winnerIndex, winnerIsOriginalAttacker),
                state.lootModifier(loserIndex),
                warState.warType(edgeIndex),
                winnerIsOriginalAttacker
        );
        if (transferred > 0d) {
            double debited = state.subtractResource(loserIndex, ResourceType.MONEY, transferred);
            state.addResource(winnerIndex, ResourceType.MONEY, debited);
        }
        state.recalculateScore(winnerIndex);
        state.recalculateScore(loserIndex);
    }

    private static void clearInvalidControls(ProjectionState state, DenseWarState warState) {
        for (int edgeIndex = 0; edgeIndex < warState.warCount; edgeIndex++) {
            if (!warState.active[edgeIndex]) {
                continue;
            }
            clearControlIfUnable(state, warState, edgeIndex, warState.groundSuperiorityOwner, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
            clearControlIfUnable(state, warState, edgeIndex, warState.airSuperiorityOwner, MilitaryUnit.AIRCRAFT, null);
            clearControlIfUnable(state, warState, edgeIndex, warState.blockadeOwner, MilitaryUnit.SHIP, null);
        }
    }

    private void resetProjectedEvaluationProfile() {
        profiledProjectionTurns = 0L;
        profiledCounterTurns = 0L;
        profiledCounterTurnsNoSlots = 0L;
        profiledCounterCandidateEvaluations = 0L;
        profiledCounterDeclarations = 0L;
        profiledRedeclareTurns = 0L;
        profiledRedeclareTurnsNoSlots = 0L;
        profiledRedeclareCandidateEvaluations = 0L;
        profiledRedeclarations = 0L;
        profiledWarIterations = 0L;
        profiledAttackChoiceCalls = 0L;
        profiledAttackTypeEvaluations = 0L;
        profiledResolvedAttacks = 0L;
        profiledPreparedStateProfiles = 0L;
        profiledPreparedStateRestores = 0L;
        profiledPreparedWarTemplateBuilds = 0L;
        profiledPreparedWarRestores = 0L;
    }

    private void flushProjectedEvaluationProfile() {
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "preparedStateProfiles", profiledPreparedStateProfiles);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "preparedStateRestores", profiledPreparedStateRestores);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "preparedWarTemplateBuilds", profiledPreparedWarTemplateBuilds);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "preparedWarRestores", profiledPreparedWarRestores);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "projectionTurns", profiledProjectionTurns);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "counterTurns", profiledCounterTurns);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "counterTurnsNoSlots", profiledCounterTurnsNoSlots);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "counterCandidateEvaluations", profiledCounterCandidateEvaluations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "counterDeclarations", profiledCounterDeclarations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "redeclareTurns", profiledRedeclareTurns);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "redeclareTurnsNoSlots", profiledRedeclareTurnsNoSlots);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "redeclareCandidateEvaluations", profiledRedeclareCandidateEvaluations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "redeclareDeclarations", profiledRedeclarations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "warIterations", profiledWarIterations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "attackChoiceCalls", profiledAttackChoiceCalls);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "attackTypeEvaluations", profiledAttackTypeEvaluations);
        PlannerProfiler.addCounter(PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION, "resolvedAttacks", profiledResolvedAttacks);
    }

    private static void clearControlIfUnable(
            ProjectionState state,
            DenseWarState warState,
            int edgeIndex,
            int[] ownerByWar,
            MilitaryUnit primaryUnit,
            MilitaryUnit secondaryUnit
    ) {
        int owner = ownerByWar[edgeIndex];
        if (owner == DenseWarState.OWNER_NONE) {
            return;
        }
        int nationIndex = owner == DenseWarState.OWNER_ATTACKER
                ? warState.attackerNationIndex[edgeIndex]
                : warState.defenderNationIndex[edgeIndex];
        if (state.unit(nationIndex, primaryUnit) > 0 || (secondaryUnit != null && state.unit(nationIndex, secondaryUnit) > 0)) {
            return;
        }
        ownerByWar[edgeIndex] = DenseWarState.OWNER_NONE;
    }

    private double positiveControlLeverage(int edgeIndex) {
        return edges.retainsControlLeverage() ? Math.max(0d, edges.controlLeverage(edgeIndex)) : 0d;
    }

    private double positiveFutureWarLeverage(int edgeIndex) {
        return edges.retainsFutureWarLeverage() ? Math.max(0d, edges.futureWarLeverage(edgeIndex)) : 0d;
    }

    private static double projectedBuyValue(DBNationSnapshot snapshot, int horizonTurns) {
        int dayCount = Math.max(1, (horizonTurns + 11) / 12);
        double score = 0d;
        for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
            if (unit == MilitaryUnit.SPIES || unit == MilitaryUnit.MISSILE || unit == MilitaryUnit.NUKE) {
                continue;
            }
            int dailyCap = Math.max(0, snapshot.dailyBuyCap(unit));
            int remainingToday = Math.max(0, dailyCap - snapshot.unitsBoughtToday(unit));
            int projectedBuys = remainingToday + Math.max(0, dayCount - 1) * dailyCap;
            if (projectedBuys > 0) {
                score += StrategicAssetValue.projectedRecoveryValue(unit, projectedBuys, snapshot.researchBits());
            }
        }
        return score;
    }

    private static double strategicAssetValue(DBNationSnapshot snapshot, CompiledScenario scenario, boolean attackerSide) {
        return PlannerStrategicValue.strategicValue(snapshot, opposingSnapshots(scenario, attackerSide));
    }

    private static double strategicAssetValue(DBNationSnapshot snapshot) {
        return PlannerStrategicValue.localStrategicValue(snapshot);
    }

    private static java.util.List<DBNationSnapshot> opposingSnapshots(CompiledScenario scenario, boolean attackerSide) {
        int count = attackerSide ? scenario.defenderCount() : scenario.attackerCount();
        java.util.ArrayList<DBNationSnapshot> snapshots = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            snapshots.add(attackerSide ? scenario.defender(index) : scenario.attacker(index));
        }
        return snapshots;
    }

    private static double combatStrength(DBNationSnapshot snapshot) {
        double groundStrength = UnitEconomy.groundStrengthRaw(
                snapshot.unit(MilitaryUnit.SOLDIER),
                snapshot.unit(MilitaryUnit.TANK),
                false,
                false
        );
        return groundStrength
                + (3d * snapshot.unit(MilitaryUnit.AIRCRAFT))
                + (2d * snapshot.unit(MilitaryUnit.SHIP));
    }

    private static WarType warTypeFromEdge(CandidateEdgeTable edges, int edgeIndex) {
        byte ordinal = edges.preferredWarTypeId(edgeIndex);
        return ordinal >= 0 && ordinal < WarType.values.length ? WarType.values[ordinal] : WarType.ORD;
    }

    private static final class ProjectionState implements CombatKernel.PrimitiveNationBuffer {
        private final int attackerCount;
        private final int defenderCount;
        private final int[] nationIds;
        private final int[] teamIds;
        private final int[] cityCounts;
        private final int[] unitBaseOffsets;
        private final int[] resourceBaseOffsets;
        private final int[] cityInfraBaseOffsets;
        private final int[] unitsFlat;
        private final int[] unitsBoughtTodayFlat;
        private final int[] pendingBuysFlat;
        private final int[] cumulativeUnitLossesFlat;
        private final double[] resourcesFlat;
        private final double[] cityInfraFlat;
        private final double[] scores;
        private final double[] nonInfraScoreBase;
        private final int[] researchBits;
        private final long[] projectBits;
        private final double[] infraAttackModifiersFlat;
        private final double[] infraDefendModifiersFlat;
        private final double[] groundLooterModifiers;
        private final double[] nonGroundLooterModifiers;
        private final double[] lootModifiers;
        private final SpecialistCityProfile[] citySpecialistProfilesFlat;
        private final ProjectionNation[] nationViews;
        private final boolean[] resourceBudgetKnown;
        private final boolean[] baseHasActiveWars;
        private int[] initialUnitsFlat;
        private int[] initialUnitsBoughtTodayFlat;
        private int[] initialPendingBuysFlat;
        private double[] initialResourcesFlat;
        private double[] initialCityInfraFlat;
        private double[] initialScores;
        private int[] initialBeigeTurns;
        private boolean collectDiagnostics;
        int turnsAttackerHeldNetControl;
        int turnsDefenderHeldNetControl;
        int turnsNoControl;
        private final int[] baselineSoldiers;
        private final int[] baselineTanks;
        private final int[] baselineAircraft;
        private final int[] baselineShips;
        private final double[] baselineScores;
        private final double[] baselineInfra;
        private final int[] beigeTurns;
        private final double[] costBuffer = new double[ResourceType.values.length];

        private ProjectionState(
                int attackerCount,
                int defenderCount,
                int[] nationIds,
                int[] teamIds,
                int[] cityCounts,
                int[] unitBaseOffsets,
                int[] resourceBaseOffsets,
                int[] cityInfraBaseOffsets,
                int[] unitsFlat,
                int[] unitsBoughtTodayFlat,
                int[] pendingBuysFlat,
                int[] cumulativeUnitLossesFlat,
                double[] resourcesFlat,
                double[] cityInfraFlat,
                double[] scores,
                double[] nonInfraScoreBase,
                int[] researchBits,
                long[] projectBits,
                double[] infraAttackModifiersFlat,
                double[] infraDefendModifiersFlat,
                double[] groundLooterModifiers,
                double[] nonGroundLooterModifiers,
                double[] lootModifiers,
                SpecialistCityProfile[] citySpecialistProfilesFlat,
                ProjectionNation[] nationViews,
                boolean[] resourceBudgetKnown,
                boolean[] baseHasActiveWars,
                int[] baselineSoldiers,
                int[] baselineTanks,
                int[] baselineAircraft,
                int[] baselineShips,
                double[] baselineScores,
                double[] baselineInfra,
                int[] beigeTurns
        ) {
            this.attackerCount = attackerCount;
            this.defenderCount = defenderCount;
            this.nationIds = nationIds;
            this.teamIds = teamIds;
            this.cityCounts = cityCounts;
            this.unitBaseOffsets = unitBaseOffsets;
            this.resourceBaseOffsets = resourceBaseOffsets;
            this.cityInfraBaseOffsets = cityInfraBaseOffsets;
            this.unitsFlat = unitsFlat;
            this.unitsBoughtTodayFlat = unitsBoughtTodayFlat;
            this.pendingBuysFlat = pendingBuysFlat;
            this.cumulativeUnitLossesFlat = cumulativeUnitLossesFlat;
            this.resourcesFlat = resourcesFlat;
            this.cityInfraFlat = cityInfraFlat;
            this.scores = scores;
            this.nonInfraScoreBase = nonInfraScoreBase;
            this.researchBits = researchBits;
            this.projectBits = projectBits;
            this.infraAttackModifiersFlat = infraAttackModifiersFlat;
            this.infraDefendModifiersFlat = infraDefendModifiersFlat;
            this.groundLooterModifiers = groundLooterModifiers;
            this.nonGroundLooterModifiers = nonGroundLooterModifiers;
            this.lootModifiers = lootModifiers;
            this.citySpecialistProfilesFlat = citySpecialistProfilesFlat;
            this.nationViews = nationViews;
            this.resourceBudgetKnown = resourceBudgetKnown;
            this.baseHasActiveWars = baseHasActiveWars;
            this.baselineSoldiers = baselineSoldiers;
            this.baselineTanks = baselineTanks;
            this.baselineAircraft = baselineAircraft;
            this.baselineShips = baselineShips;
            this.baselineScores = baselineScores;
            this.baselineInfra = baselineInfra;
            this.beigeTurns = beigeTurns;
        }

        static ProjectionState from(CompiledScenario scenario) {
            int attackerCount = scenario.attackerCount();
            int defenderCount = scenario.defenderCount();
            int nationCount = attackerCount + defenderCount;
            int unitStride = MilitaryUnit.values.length;
            int resourceStride = ResourceType.values.length;
            int attackStride = AttackType.values.length;
            int[] nationIds = new int[nationCount];
            int[] teamIds = new int[nationCount];
            int[] cityCounts = new int[nationCount];
            int[] unitBaseOffsets = new int[nationCount];
            int[] resourceBaseOffsets = new int[nationCount];
            int[] cityInfraBaseOffsets = new int[nationCount];
            double[][] cityInfraByNation = new double[nationCount][];
            SpecialistCityProfile[][] profilesByNation = new SpecialistCityProfile[nationCount][];
            int totalCities = 0;
            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                DBNationSnapshot snapshot = snapshotAt(scenario, attackerCount, nationIndex);
                unitBaseOffsets[nationIndex] = nationIndex * unitStride;
                resourceBaseOffsets[nationIndex] = nationIndex * resourceStride;
                cityInfraBaseOffsets[nationIndex] = totalCities;
                cityInfraByNation[nationIndex] = snapshot.cityInfraRaw().clone();
                profilesByNation[nationIndex] = snapshot.citySpecialistProfilesRaw().clone();
                cityCounts[nationIndex] = cityInfraByNation[nationIndex].length;
                totalCities += cityCounts[nationIndex];
            }

            int[] unitsFlat = new int[nationCount * unitStride];
            int[] unitsBoughtTodayFlat = new int[nationCount * unitStride];
            int[] pendingBuysFlat = new int[nationCount * unitStride];
            int[] cumulativeUnitLossesFlat = new int[nationCount * unitStride];
            double[] resourcesFlat = new double[nationCount * resourceStride];
            double[] cityInfraFlat = new double[totalCities];
            double[] scores = new double[nationCount];
            double[] nonInfraScoreBase = new double[nationCount];
            int[] researchBits = new int[nationCount];
            long[] projectBits = new long[nationCount];
            double[] infraAttackModifiersFlat = new double[nationCount * attackStride];
            double[] infraDefendModifiersFlat = new double[nationCount * attackStride];
            double[] groundLooterModifiers = new double[nationCount];
            double[] nonGroundLooterModifiers = new double[nationCount];
            double[] lootModifiers = new double[nationCount];
            SpecialistCityProfile[] citySpecialistProfilesFlat = new SpecialistCityProfile[totalCities];
            ProjectionNation[] nationViews = new ProjectionNation[nationCount];
            boolean[] resourceBudgetKnown = new boolean[nationCount];
            boolean[] baseHasActiveWars = new boolean[nationCount];
            int[] baselineSoldiers = new int[nationCount];
            int[] baselineTanks = new int[nationCount];
            int[] baselineAircraft = new int[nationCount];
            int[] baselineShips = new int[nationCount];
            double[] baselineScores = new double[nationCount];
            double[] baselineInfra = new double[nationCount];
            int[] beigeTurns = new int[nationCount];

            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                DBNationSnapshot snapshot = snapshotAt(scenario, attackerCount, nationIndex);
                nationIds[nationIndex] = snapshot.nationId();
                teamIds[nationIndex] = snapshot.teamId();
                nonInfraScoreBase[nationIndex] = snapshot.nonInfraScoreBase();
                researchBits[nationIndex] = snapshot.researchBits();
                projectBits[nationIndex] = snapshot.projectBits();
                groundLooterModifiers[nationIndex] = snapshot.looterModifier(true);
                nonGroundLooterModifiers[nationIndex] = snapshot.looterModifier(false);
                lootModifiers[nationIndex] = snapshot.lootModifier();
                beigeTurns[nationIndex] = snapshot.beigeTurns();
                int unitBase = unitBaseOffsets[nationIndex];
                for (MilitaryUnit unit : MilitaryUnit.values) {
                    int unitIndex = unitBase + unit.ordinal();
                    unitsFlat[unitIndex] = Math.max(0, snapshot.unit(unit));
                    unitsBoughtTodayFlat[unitIndex] = Math.max(0, snapshot.unitsBoughtToday(unit));
                    pendingBuysFlat[unitIndex] = Math.max(0, snapshot.pendingBuysNextTurn(unit));
                }
                snapshot.copyResourcesInto(resourcesFlat, resourceBaseOffsets[nationIndex]);
                resourceBudgetKnown[nationIndex] = hasAnyResource(resourcesFlat, resourceBaseOffsets[nationIndex], resourceStride);
                baseHasActiveWars[nationIndex] = snapshot.hasActiveWars();
                int cityBase = cityInfraBaseOffsets[nationIndex];
                System.arraycopy(cityInfraByNation[nationIndex], 0, cityInfraFlat, cityBase, cityCounts[nationIndex]);
                System.arraycopy(profilesByNation[nationIndex], 0, citySpecialistProfilesFlat, cityBase, cityCounts[nationIndex]);
                for (AttackType type : AttackType.values) {
                    int modifierIndex = nationIndex * attackStride + type.ordinal();
                    infraAttackModifiersFlat[modifierIndex] = snapshot.infraAttackModifier(type);
                    infraDefendModifiersFlat[modifierIndex] = snapshot.infraDefendModifier(type);
                }
                baselineSoldiers[nationIndex] = unitsFlat[unitBase + MilitaryUnit.SOLDIER.ordinal()];
                baselineTanks[nationIndex] = unitsFlat[unitBase + MilitaryUnit.TANK.ordinal()];
                baselineAircraft[nationIndex] = unitsFlat[unitBase + MilitaryUnit.AIRCRAFT.ordinal()];
                baselineShips[nationIndex] = unitsFlat[unitBase + MilitaryUnit.SHIP.ordinal()];
            }

            ProjectionState state = new ProjectionState(
                    attackerCount,
                    defenderCount,
                    nationIds,
                    teamIds,
                    cityCounts,
                    unitBaseOffsets,
                    resourceBaseOffsets,
                    cityInfraBaseOffsets,
                    unitsFlat,
                    unitsBoughtTodayFlat,
                    pendingBuysFlat,
                    cumulativeUnitLossesFlat,
                    resourcesFlat,
                    cityInfraFlat,
                    scores,
                    nonInfraScoreBase,
                    researchBits,
                    projectBits,
                    infraAttackModifiersFlat,
                    infraDefendModifiersFlat,
                    groundLooterModifiers,
                    nonGroundLooterModifiers,
                    lootModifiers,
                    citySpecialistProfilesFlat,
                    nationViews,
                    resourceBudgetKnown,
                    baseHasActiveWars,
                    baselineSoldiers,
                    baselineTanks,
                    baselineAircraft,
                    baselineShips,
                    baselineScores,
                    baselineInfra,
                    beigeTurns
            );
            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                nationViews[nationIndex] = new ProjectionNation(state, nationIndex);
                baselineInfra[nationIndex] = state.totalInfra(nationIndex);
                state.recalculateScore(nationIndex);
                baselineScores[nationIndex] = state.scores[nationIndex];
            }
            state.captureInitialMutableState();
            return state;
        }

        private static DBNationSnapshot snapshotAt(CompiledScenario scenario, int attackerCount, int nationIndex) {
            return nationIndex < attackerCount
                    ? scenario.attacker(nationIndex)
                    : scenario.defender(nationIndex - attackerCount);
        }

        private static boolean hasAnyResource(double[] resourcesFlat, int resourceBase, int resourceStride) {
            for (int i = 0; i < resourceStride; i++) {
                if (resourcesFlat[resourceBase + i] > 0d) {
                    return true;
                }
            }
            return false;
        }

        int nationIndexById(int nationId) {
            for (int nationIndex = 0; nationIndex < nationIds.length; nationIndex++) {
                if (nationIds[nationIndex] == nationId) {
                    return nationIndex;
                }
            }
            return -1;
        }

        private void captureInitialMutableState() {
            initialUnitsFlat = unitsFlat.clone();
            initialUnitsBoughtTodayFlat = unitsBoughtTodayFlat.clone();
            initialPendingBuysFlat = pendingBuysFlat.clone();
            initialResourcesFlat = resourcesFlat.clone();
            initialCityInfraFlat = cityInfraFlat.clone();
            initialScores = scores.clone();
            initialBeigeTurns = beigeTurns.clone();
        }

        void collectDiagnostics(boolean collectDiagnostics) {
            this.collectDiagnostics = collectDiagnostics;
        }

        ProjectionStateCheckpoint captureCheckpoint() {
            return new ProjectionStateCheckpoint(
                    unitsFlat.clone(),
                    unitsBoughtTodayFlat.clone(),
                    pendingBuysFlat.clone(),
                    resourcesFlat.clone(),
                    cityInfraFlat.clone(),
                    scores.clone(),
                    beigeTurns.clone()
            );
        }

        void resetMutableState() {
            System.arraycopy(initialUnitsFlat, 0, unitsFlat, 0, unitsFlat.length);
            System.arraycopy(initialUnitsBoughtTodayFlat, 0, unitsBoughtTodayFlat, 0, unitsBoughtTodayFlat.length);
            System.arraycopy(initialPendingBuysFlat, 0, pendingBuysFlat, 0, pendingBuysFlat.length);
            if (collectDiagnostics) {
                Arrays.fill(cumulativeUnitLossesFlat, 0);
                turnsAttackerHeldNetControl = 0;
                turnsDefenderHeldNetControl = 0;
                turnsNoControl = 0;
            }
            System.arraycopy(initialResourcesFlat, 0, resourcesFlat, 0, resourcesFlat.length);
            System.arraycopy(initialCityInfraFlat, 0, cityInfraFlat, 0, cityInfraFlat.length);
            System.arraycopy(initialScores, 0, scores, 0, scores.length);
            System.arraycopy(initialBeigeTurns, 0, beigeTurns, 0, beigeTurns.length);
        }

        void restoreCheckpoint(ProjectionStateCheckpoint checkpoint) {
            System.arraycopy(checkpoint.unitsFlat(), 0, unitsFlat, 0, unitsFlat.length);
            System.arraycopy(checkpoint.unitsBoughtTodayFlat(), 0, unitsBoughtTodayFlat, 0, unitsBoughtTodayFlat.length);
            System.arraycopy(checkpoint.pendingBuysFlat(), 0, pendingBuysFlat, 0, pendingBuysFlat.length);
            if (collectDiagnostics) {
                Arrays.fill(cumulativeUnitLossesFlat, 0);
                turnsAttackerHeldNetControl = 0;
                turnsDefenderHeldNetControl = 0;
                turnsNoControl = 0;
            }
            System.arraycopy(checkpoint.resourcesFlat(), 0, resourcesFlat, 0, resourcesFlat.length);
            System.arraycopy(checkpoint.cityInfraFlat(), 0, cityInfraFlat, 0, cityInfraFlat.length);
            System.arraycopy(checkpoint.scores(), 0, scores, 0, scores.length);
            System.arraycopy(checkpoint.beigeTurns(), 0, beigeTurns, 0, beigeTurns.length);
        }

        void materializePendingBuys() {
            for (int nationIndex = 0; nationIndex < nationIds.length; nationIndex++) {
                boolean changed = false;
                int unitBase = unitBaseOffsets[nationIndex];
                for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                    int index = unitBase + unit.ordinal();
                    int pending = pendingBuysFlat[index];
                    if (pending <= 0) {
                        continue;
                    }
                    unitsFlat[index] += pending;
                    pendingBuysFlat[index] = 0;
                    changed = true;
                }
                if (changed) {
                    recalculateScore(nationIndex);
                }
            }
        }

        void resetUnitBuysToday() {
            for (int nationIndex = 0; nationIndex < nationIds.length; nationIndex++) {
                Arrays.fill(unitsBoughtTodayFlat, unitBaseOffsets[nationIndex], unitBaseOffsets[nationIndex] + MilitaryUnit.values.length, 0);
            }
        }

        void decrementBeigeTurns() {
            for (int nationIndex = 0; nationIndex < beigeTurns.length; nationIndex++) {
                if (beigeTurns[nationIndex] > 0) {
                    beigeTurns[nationIndex]--;
                }
            }
        }

        void applyDailyBuys(boolean freshDay, boolean[] activeWarsByNation) {
            for (int nationIndex = 0; nationIndex < nationIds.length; nationIndex++) {
                boolean changed = false;
                boolean hasActiveWars = baseHasActiveWars[nationIndex]
                        || (activeWarsByNation != null && activeWarsByNation[nationIndex]);
                for (MilitaryUnit unit : PROJECTED_BUY_UNITS) {
                    int unitIndex = unitBaseOffsets[nationIndex] + unit.ordinal();
                    int cap = dailyBuyCap(nationIndex, unit, hasActiveWars);
                    int remaining = freshDay ? cap : Math.max(0, cap - unitsBoughtTodayFlat[unitIndex]);
                    int bought = buyAffordable(nationIndex, unit, remaining);
                    if (bought <= 0) {
                        continue;
                    }
                    unitsFlat[unitIndex] += bought;
                    unitsBoughtTodayFlat[unitIndex] += bought;
                    changed = true;
                }
                if (changed) {
                    recalculateScore(nationIndex);
                }
            }
        }

        private int dailyBuyCap(int nationIndex, MilitaryUnit unit, boolean hasActiveWars) {
            return UnitEconomy.maxBuyPerDayFor(
                    cityCounts[nationIndex],
                    unit,
                    project -> (projectBits[nationIndex] & (1L << project.ordinal())) != 0L,
                    research -> research.getLevel(researchBits[nationIndex]),
                    beigeTurns[nationIndex],
                    hasActiveWars
            );
        }

        private int buyAffordable(int nationIndex, MilitaryUnit unit, int requested) {
            if (requested <= 0) {
                return 0;
            }
            if (!resourceBudgetKnown[nationIndex]) {
                return requested;
            }
            int affordable = requested;
            int resourceBase = resourceBaseOffsets[nationIndex];
            Arrays.fill(costBuffer, 0d);
            unit.addCost(costBuffer, 1, researchBits[nationIndex]);
            for (ResourceType resource : ResourceType.values) {
                double cost = costBuffer[resource.ordinal()];
                if (cost > 0d) {
                    affordable = Math.min(affordable, (int) Math.floor(resourcesFlat[resourceBase + resource.ordinal()] / cost));
                }
            }
            if (affordable <= 0) {
                return 0;
            }
            for (ResourceType resource : ResourceType.values) {
                resourcesFlat[resourceBase + resource.ordinal()] -= costBuffer[resource.ordinal()] * affordable;
            }
            return affordable;
        }

        void applyLosses(int nationIndex, int[] losses) {
            if (losses == null) {
                return;
            }
            boolean changed = false;
            int unitBase = unitBaseOffsets[nationIndex];
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int loss = losses[unit.ordinal()];
                if (loss <= 0) {
                    continue;
                }
                int index = unitBase + unit.ordinal();
                int next = Math.max(0, unitsFlat[index] - loss);
                if (next != unitsFlat[index]) {
                    if (collectDiagnostics) {
                        cumulativeUnitLossesFlat[index] += unitsFlat[index] - next;
                    }
                    unitsFlat[index] = next;
                    changed = true;
                }
            }
            if (changed) {
                recalculateScore(nationIndex);
            }
        }

        void applyInfraDamage(int nationIndex, double amount) {
            if (!(amount > 0d) || cityCounts[nationIndex] == 0) {
                return;
            }
            int cityBase = cityInfraBaseOffsets[nationIndex];
            int cityCount = cityCounts[nationIndex];
            int maxCityIndex = 0;
            for (int cityIndex = 1; cityIndex < cityCount; cityIndex++) {
                if (cityInfraFlat[cityBase + cityIndex] > cityInfraFlat[cityBase + maxCityIndex]) {
                    maxCityIndex = cityIndex;
                }
            }
            int globalCityIndex = cityBase + maxCityIndex;
            double current = cityInfraFlat[globalCityIndex];
            if (current <= 0d) {
                return;
            }
            double removed = Math.min(current, amount);
            cityInfraFlat[globalCityIndex] = current - removed;
            recalculateScore(nationIndex);
        }

        void applyVictoryInfraPercent(int nationIndex, double percent) {
            int percentMilli = WarOutcomeMath.victoryInfraPercentMilli(percent);
            if (percentMilli <= 0 || cityCounts[nationIndex] == 0) {
                return;
            }
            int cityBase = cityInfraBaseOffsets[nationIndex];
            for (int cityIndex = 0; cityIndex < cityCounts[nationIndex]; cityIndex++) {
                int globalCityIndex = cityBase + cityIndex;
                int beforeCents = Math.max(0, (int) Math.round(cityInfraFlat[globalCityIndex] * 100d));
                cityInfraFlat[globalCityIndex] = WarOutcomeMath.victoryInfraAfterCents(beforeCents, percentMilli) * 0.01d;
            }
            recalculateScore(nationIndex);
        }

        double subtractResource(int nationIndex, ResourceType type, double amount) {
            if (!(amount > 0d)) {
                return 0d;
            }
            int index = resourceBaseOffsets[nationIndex] + type.ordinal();
            double debited = Math.min(amount, resourcesFlat[index]);
            resourcesFlat[index] -= debited;
            return debited;
        }

        void addResource(int nationIndex, ResourceType type, double amount) {
            if (amount > 0d) {
                resourcesFlat[resourceBaseOffsets[nationIndex] + type.ordinal()] += amount;
            }
        }

        double resource(int nationIndex, ResourceType type) {
            return resourcesFlat[resourceBaseOffsets[nationIndex] + type.ordinal()];
        }

        int unit(int nationIndex, MilitaryUnit unit) {
            return unitsFlat[unitBaseOffsets[nationIndex] + unit.ordinal()];
        }

        double totalInfra(int nationIndex) {
            double total = 0d;
            int cityBase = cityInfraBaseOffsets[nationIndex];
            for (int cityIndex = 0; cityIndex < cityCounts[nationIndex]; cityIndex++) {
                total += cityInfraFlat[cityBase + cityIndex];
            }
            return total;
        }

        void recalculateScore(int nationIndex) {
            double score = nonInfraScoreBase[nationIndex] + totalInfra(nationIndex) / 40d;
            int unitBase = unitBaseOffsets[nationIndex];
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int amount = unitsFlat[unitBase + unit.ordinal()];
                if (amount > 0) {
                    score += unit.getScore(amount);
                }
            }
            scores[nationIndex] = score;
        }

        double targetPressure(int attackerNationIndex, int defenderNationIndex) {
            return OpeningMetricSummary.targetPressure(
                    groundStrength(attackerNationIndex, false),
                    groundStrength(defenderNationIndex, false),
                    unit(attackerNationIndex, MilitaryUnit.AIRCRAFT),
                    unit(defenderNationIndex, MilitaryUnit.AIRCRAFT),
                    unit(attackerNationIndex, MilitaryUnit.SHIP),
                    unit(defenderNationIndex, MilitaryUnit.SHIP)
            );
        }

        double strategicValue(int nationIndex) {
            return strategicValue(nationIndex, null);
        }

        double slotCapabilityValue(int nationIndex, DenseWarState warState) {
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext(nationIndex, warState).hasActiveWars();
            return PlannerStrategicValue.slotCapabilityValue(capabilityVector(nationIndex, hasActiveWars));
        }

        double strategicValue(int nationIndex, DenseWarState warState) {
            StrategicAssetValue.StrategicRelevance relevance = strategicRelevance(nationIndex);
            StrategicAssetValue.ActiveWarContext activeWarContext = activeWarContext(nationIndex, warState);
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext.hasActiveWars();
            double militaryValue = strategicMilitaryValue(nationIndex, relevance, hasActiveWars);
            double infraValue = StrategicAssetValue.infrastructureValue(
                    cityIndex -> cityInfraFlat[cityInfraBaseOffsets[nationIndex] + cityIndex],
                    cityCounts[nationIndex],
                    activeWarContext,
                    relevance
            );
            return militaryValue + infraValue;
        }

        private int remainingRecoveryCapacity(int nationIndex, MilitaryUnit unit, boolean hasActiveWars) {
            int unitBase = unitBaseOffsets[nationIndex] + unit.ordinal();
            int boughtOrQueued = unitsBoughtTodayFlat[unitBase] + pendingBuysFlat[unitBase];
            return Math.max(0, dailyBuyCap(nationIndex, unit, hasActiveWars) - boughtOrQueued);
        }

        private double strategicMilitaryValue(
                int nationIndex,
                StrategicAssetValue.StrategicRelevance relevance,
                boolean hasActiveWars
        ) {
            return PlannerStrategicValue.strategicMilitaryValue(capabilityVector(nationIndex, hasActiveWars), relevance);
        }

        private StrategicCapabilityVector capabilityVector(int nationIndex, boolean hasActiveWars) {
            return PlannerStrategicValue.capabilityVector(
                groundStrength(nationIndex, false),
                unit(nationIndex, MilitaryUnit.AIRCRAFT),
                unit(nationIndex, MilitaryUnit.SHIP),
                unit(nationIndex, MilitaryUnit.MISSILE),
                unit(nationIndex, MilitaryUnit.NUKE),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.SOLDIER, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.SOLDIER, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.TANK, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.TANK, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.AIRCRAFT, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.AIRCRAFT, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.SHIP, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.SHIP, hasActiveWars)
            );
        }

        private StrategicCapabilityVector capabilityVectorAfterLosses(int nationIndex, int[] losses, boolean hasActiveWars) {
            return PlannerStrategicValue.capabilityVector(
                OpeningMetricSummary.groundStrength(
                    projectedUnitAfterLoss(nationIndex, MilitaryUnit.SOLDIER, losses),
                    projectedUnitAfterLoss(nationIndex, MilitaryUnit.TANK, losses),
                    false
                ),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.AIRCRAFT, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.SHIP, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.MISSILE, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.NUKE, losses),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.SOLDIER, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.SOLDIER, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.TANK, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.TANK, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.AIRCRAFT, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.AIRCRAFT, hasActiveWars),
                remainingRecoveryCapacity(nationIndex, MilitaryUnit.SHIP, hasActiveWars),
                dailyBuyCap(nationIndex, MilitaryUnit.SHIP, hasActiveWars)
            );
        }

        private int projectedUnitAfterLoss(int nationIndex, MilitaryUnit unit, int[] losses) {
            return Math.max(0, unit(nationIndex, unit) - projectedLoss(losses, unit));
        }

        private static int projectedLoss(int[] losses, MilitaryUnit unit) {
            return losses == null ? 0 : Math.max(0, losses[unit.ordinal()]);
        }

        double unitValue(int nationIndex) {
            double value = 0d;
            int unitBase = unitBaseOffsets[nationIndex];
            int research = researchBits[nationIndex];
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int amount = unitsFlat[unitBase + unit.ordinal()] + pendingBuysFlat[unitBase + unit.ordinal()];
                value += StrategicAssetValue.unitValue(unit, amount, research);
            }
            return value;
        }

        double rebuyPreservedValue(int nationIndex, DenseWarState warState) {
            StrategicAssetValue.ActiveWarContext activeWarContext = activeWarContext(nationIndex, warState);
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext.hasActiveWars();
            double value = 0d;
            for (MilitaryUnit unit : PROJECTED_BUY_UNITS) {
                int unitBase = unitBaseOffsets[nationIndex];
                int boughtOrQueued = unitsBoughtTodayFlat[unitBase + unit.ordinal()]
                        + pendingBuysFlat[unitBase + unit.ordinal()];
                int remaining = Math.max(0, dailyBuyCap(nationIndex, unit, hasActiveWars) - boughtOrQueued);
                value += StrategicAssetValue.projectedRecoveryValue(unit, remaining, researchBits[nationIndex]);
            }
            return value;
        }

        double unitLossValue(
                int nationIndex,
                int[] losses,
                StrategicAssetValue.ActiveWarContext activeWarContext
        ) {
            StrategicAssetValue.StrategicRelevance relevance = strategicRelevance(nationIndex);
            boolean hasActiveWars = baseHasActiveWars[nationIndex]
                || (activeWarContext != null && activeWarContext.hasActiveWars());
            double before = strategicMilitaryValue(nationIndex, relevance, hasActiveWars);
            double after = PlannerStrategicValue.strategicMilitaryValue(
                    capabilityVectorAfterLosses(nationIndex, losses, hasActiveWars),
                    relevance
            );
            double damage = Math.max(0d, before - after);
            return damage * StrategicAssetValue.marginalActionSpaceMultiplier(activeWarContext);
        }

        double marginalActionSpaceValue(int nationIndex, DenseWarState warState) {
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext(nationIndex, warState).hasActiveWars();
            return strategicMilitaryValue(nationIndex, strategicRelevance(nationIndex), hasActiveWars)
                    * StrategicAssetValue.marginalActionSpaceMultiplier(activeWarContext(nationIndex, warState));
        }

        private StrategicAssetValue.StrategicRelevance strategicRelevance(int nationIndex) {
            boolean attackerSide = nationIndex < attackerCount;
            int opponentCount = attackerSide ? defenderCount : attackerCount;
            return StrategicAssetValue.relevanceForWarRange(
                    cityCounts[nationIndex],
                    scores[nationIndex],
                    baseHasActiveWars[nationIndex] ? 1 : 0,
                    opponentCount,
                    opponentIndex -> attackerSide
                            ? scores[attackerCount + opponentIndex]
                            : scores[opponentIndex]
            );
        }

        private StrategicAssetValue.ActiveWarContext activeWarContext(int nationIndex, DenseWarState warState) {
            if (warState == null) {
                return StrategicAssetValue.ActiveWarContext.basic(baseHasActiveWars[nationIndex]);
            }
            int activeOpponents = 0;
            int offensiveWars = 0;
            int defensiveWars = 0;
            int ownMaps = 0;
            int enemyMaps = 0;
            int ownResistance = 0;
            int enemyResistance = 0;
            int ownControls = 0;
            int enemyControls = 0;
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                boolean attacker = warState.attackerNationIndex[warIndex] == nationIndex;
                boolean defender = warState.defenderNationIndex[warIndex] == nationIndex;
                if (!attacker && !defender) {
                    continue;
                }
                activeOpponents++;
                if (attacker) {
                    offensiveWars++;
                    ownMaps += warState.attackerMaps[warIndex];
                    enemyMaps += warState.defenderMaps[warIndex];
                    ownResistance += warState.attackerResistance[warIndex];
                    enemyResistance += warState.defenderResistance[warIndex];
                } else {
                    defensiveWars++;
                    ownMaps += warState.defenderMaps[warIndex];
                    enemyMaps += warState.attackerMaps[warIndex];
                    ownResistance += warState.defenderResistance[warIndex];
                    enemyResistance += warState.attackerResistance[warIndex];
                }
                int ownOwnerCode = attacker ? DenseWarState.OWNER_ATTACKER : DenseWarState.OWNER_DEFENDER;
                int enemyOwnerCode = attacker ? DenseWarState.OWNER_DEFENDER : DenseWarState.OWNER_ATTACKER;
                ownControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        ownOwnerCode,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
                enemyControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        enemyOwnerCode,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
            }
            if (activeOpponents == 0) {
                return StrategicAssetValue.ActiveWarContext.basic(baseHasActiveWars[nationIndex]);
            }
            double slotPressure = Math.max(offensiveWars / 3.0d, defensiveWars / 3.0d);
            return PlannerControlStateReducer.activeWarContextFromRelativeState(
                    activeOpponents,
                    slotPressure,
                    ownMaps,
                    enemyMaps,
                    ownResistance,
                    enemyResistance,
                    ownControls,
                    enemyControls
            );
        }

        double forceWindowScore(
                int attackerNationIndex,
                int defenderNationIndex,
                boolean attackerHasAirControl,
                boolean defenderHasAirControl
        ) {
            return OpeningMetricSummary.forceWindowScore(
                    baselineGroundStrength(attackerNationIndex, defenderHasAirControl),
                    groundStrength(attackerNationIndex, defenderHasAirControl),
                    baselineGroundStrength(defenderNationIndex, attackerHasAirControl),
                    groundStrength(defenderNationIndex, attackerHasAirControl),
                    baselineAircraft[attackerNationIndex],
                    unit(attackerNationIndex, MilitaryUnit.AIRCRAFT),
                    baselineAircraft[defenderNationIndex],
                    unit(defenderNationIndex, MilitaryUnit.AIRCRAFT),
                    baselineShips[attackerNationIndex],
                    unit(attackerNationIndex, MilitaryUnit.SHIP),
                    baselineShips[defenderNationIndex],
                    unit(defenderNationIndex, MilitaryUnit.SHIP)
            );
        }

        double score(int nationIndex) {
            return scores[nationIndex];
        }

        double combatStrength(int nationIndex) {
            return groundStrength(nationIndex, false)
                    + (3d * unit(nationIndex, MilitaryUnit.AIRCRAFT))
                    + (2d * unit(nationIndex, MilitaryUnit.SHIP));
        }

        double baselineCombatStrength(int nationIndex) {
            return baselineGroundStrength(nationIndex, false)
                    + (3d * baselineAircraft[nationIndex])
                    + (2d * baselineShips[nationIndex]);
        }

        private double baselineGroundStrength(int nationIndex, boolean underAir) {
            return UnitEconomy.groundStrengthRaw(baselineSoldiers[nationIndex], baselineTanks[nationIndex], false, underAir);
        }

        private double groundStrength(int nationIndex, boolean underAir) {
            return UnitEconomy.groundStrengthRaw(
                    unit(nationIndex, MilitaryUnit.SOLDIER),
                    unit(nationIndex, MilitaryUnit.TANK),
                    false,
                    underAir
            );
        }

        double infraAttackModifier(int nationIndex, AttackType type) {
            return infraAttackModifiersFlat[nationIndex * AttackType.values.length + type.ordinal()];
        }

        double infraDefendModifier(int nationIndex, AttackType type) {
            return infraDefendModifiersFlat[nationIndex * AttackType.values.length + type.ordinal()];
        }

        double looterModifier(int nationIndex, boolean ground) {
            return ground ? groundLooterModifiers[nationIndex] : nonGroundLooterModifiers[nationIndex];
        }

        double lootModifier(int nationIndex) {
            return lootModifiers[nationIndex];
        }

        @Override
        public int[] unitsFlat() {
            return unitsFlat;
        }

        @Override
        public int unitBaseOffset(int nationIndex) {
            return unitBaseOffsets[nationIndex];
        }

        @Override
        public double[] cityInfraFlat() {
            return cityInfraFlat;
        }

        @Override
        public int cityInfraBaseOffset(int nationIndex) {
            return cityInfraBaseOffsets[nationIndex];
        }

        @Override
        public int cityCount(int nationIndex) {
            return cityCounts[nationIndex];
        }

        private static final class ProjectionNation implements CombatKernel.BufferBackedNationState {
            private final ProjectionState state;
            private final int nationIndex;

            private ProjectionNation(ProjectionState state, int nationIndex) {
                this.state = state;
                this.nationIndex = nationIndex;
            }

            @Override
            public int nationId() {
                return state.nationIds[nationIndex];
            }

            @Override
            public ProjectionState nationBuffer() {
                return state;
            }

            @Override
            public int nationIndex() {
                return nationIndex;
            }

            @Override
            public int researchBits() {
                return state.researchBits[nationIndex];
            }

            @Override
            public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return Map.entry(0, 0);
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].missileDamage(infra, this::hasProject);
            }

            @Override
            public int cityMissileDamageMin(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return 0;
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].missileDamageMin(infra, this::hasProject);
            }

            @Override
            public int cityMissileDamageMax(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return 0;
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].missileDamageMax(infra, this::hasProject);
            }

            @Override
            public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return Map.entry(0, 0);
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].nukeDamage(infra, this::hasProject);
            }

            @Override
            public int cityNukeDamageMin(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return 0;
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].nukeDamageMin(infra, this::hasProject);
            }

            @Override
            public int cityNukeDamageMax(int cityIndex) {
                if (cityIndex < 0 || cityIndex >= state.cityCounts[nationIndex]) {
                    return 0;
                }
                double infra = cityInfra(cityIndex);
                return state.citySpecialistProfilesFlat[state.cityInfraBaseOffsets[nationIndex] + cityIndex].nukeDamageMax(infra, this::hasProject);
            }

            @Override
            public double infraAttackModifier(AttackType type) {
                return state.infraAttackModifier(nationIndex, type);
            }

            @Override
            public double infraDefendModifier(AttackType type) {
                return state.infraDefendModifier(nationIndex, type);
            }

            @Override
            public double looterModifier(boolean ground) {
                return state.looterModifier(nationIndex, ground);
            }

            @Override
            public double lootModifier() {
                return state.lootModifier(nationIndex);
            }

            @Override
            public boolean isBlitzkrieg() {
                return false;
            }

            @Override
            public boolean hasProject(Project project) {
                return (state.projectBits[nationIndex] & (1L << project.ordinal())) != 0L;
            }
        }
    }

    private static final class DenseWarState implements CombatKernel.PrimitiveWarBuffer {
        private static final int OWNER_NONE = 0;
        private static final int OWNER_ATTACKER = 1;
        private static final int OWNER_DEFENDER = 2;

        private int[] attackerNationIndex;
        private int[] defenderNationIndex;
        private boolean[] active;
        private int[] attackerMaps;
        private int[] defenderMaps;
        private int[] startTurn;
        private int[] attackerResistance;
        private int[] defenderResistance;
        private WarType[] warTypes;
        private int[] groundSuperiorityOwner;
        private int[] airSuperiorityOwner;
        private int[] blockadeOwner;
        private boolean[] seededCurrentWar;
        private int[] initialOutcomeOwner;
        private int[] outcomeOwner;
        private final boolean[] activePairs;
        private final int[] pairUnlockTurn;
        private final int nationCount;
        private int openingEdgeCount;
        private int warCount;

        private DenseWarState(
                int[] attackerNationIndex,
                int[] defenderNationIndex,
                boolean[] active,
                int[] attackerMaps,
                int[] defenderMaps,
                int[] startTurn,
                int[] attackerResistance,
                int[] defenderResistance,
                WarType[] warTypes,
                int[] groundSuperiorityOwner,
                int[] airSuperiorityOwner,
                int[] blockadeOwner,
                boolean[] seededCurrentWar,
                int[] initialOutcomeOwner,
                int[] outcomeOwner,
                boolean[] activePairs,
                int[] pairUnlockTurn,
                int nationCount,
                int warCount
        ) {
            this.attackerNationIndex = attackerNationIndex;
            this.defenderNationIndex = defenderNationIndex;
            this.active = active;
            this.attackerMaps = attackerMaps;
            this.defenderMaps = defenderMaps;
            this.startTurn = startTurn;
            this.attackerResistance = attackerResistance;
            this.defenderResistance = defenderResistance;
            this.warTypes = warTypes;
            this.groundSuperiorityOwner = groundSuperiorityOwner;
            this.airSuperiorityOwner = airSuperiorityOwner;
            this.blockadeOwner = blockadeOwner;
            this.seededCurrentWar = seededCurrentWar;
            this.initialOutcomeOwner = initialOutcomeOwner;
            this.outcomeOwner = outcomeOwner;
            this.activePairs = activePairs;
            this.pairUnlockTurn = pairUnlockTurn;
            this.nationCount = nationCount;
            this.warCount = warCount;
        }

        static DenseWarState create(CandidateEdgeTable edges, ProjectionState state, int initialCapacity) {
            int capacity = Math.max(edges.edgeCount(), Math.max(1, initialCapacity));
            int[] attackerNationIndex = new int[capacity];
            int[] defenderNationIndex = new int[capacity];
            boolean[] active = new boolean[capacity];
            int[] attackerMaps = new int[capacity];
            int[] defenderMaps = new int[capacity];
            int[] startTurn = new int[capacity];
            int[] attackerResistance = new int[capacity];
            int[] defenderResistance = new int[capacity];
            WarType[] warTypes = new WarType[capacity];
            int[] groundSuperiorityOwner = new int[capacity];
            int[] airSuperiorityOwner = new int[capacity];
            int[] blockadeOwner = new int[capacity];
            boolean[] seededCurrentWar = new boolean[capacity];
            int[] initialOutcomeOwner = new int[capacity];
            int[] outcomeOwner = new int[capacity];
            int nationCount = state.nationIds.length;
            boolean[] activePairs = new boolean[nationCount * nationCount];
            int[] pairUnlockTurn = new int[nationCount * nationCount];
            return new DenseWarState(
                    attackerNationIndex,
                    defenderNationIndex,
                    active,
                    attackerMaps,
                    defenderMaps,
                    startTurn,
                    attackerResistance,
                    defenderResistance,
                    warTypes,
                    groundSuperiorityOwner,
                    airSuperiorityOwner,
                    blockadeOwner,
                    seededCurrentWar,
                    initialOutcomeOwner,
                    outcomeOwner,
                    activePairs,
                    pairUnlockTurn,
                    nationCount,
                    0
            );
        }

        static DenseWarState from(CandidateEdgeTable edges, boolean[] edgeAssigned, ProjectionState state, int counterCapacity) {
            DenseWarState warState = create(edges, state, edges.edgeCount() + Math.max(0, counterCapacity));
            warState.reset(edges, edgeAssigned, state, counterCapacity, java.util.List.of());
            return warState;
        }

        void initializeOpeningTemplate(CandidateEdgeTable edges, ProjectionState state, int counterCapacity) {
            int edgeCount = edges.edgeCount();
            ensureCapacity(edgeCount + Math.max(0, counterCapacity));
            Arrays.fill(activePairs, false);
            Arrays.fill(pairUnlockTurn, 0);
            openingEdgeCount = edgeCount;
            warCount = edgeCount;
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                attackerNationIndex[edgeIndex] = edges.attackerIndex(edgeIndex);
                defenderNationIndex[edgeIndex] = state.attackerCount + edges.defenderIndex(edgeIndex);
                active[edgeIndex] = false;
                attackerMaps[edgeIndex] = INITIAL_WAR_MAPS;
                defenderMaps[edgeIndex] = INITIAL_WAR_MAPS;
                startTurn[edgeIndex] = 0;
                attackerResistance[edgeIndex] = INITIAL_RESISTANCE;
                defenderResistance[edgeIndex] = INITIAL_RESISTANCE;
                warTypes[edgeIndex] = warTypeFromEdge(edges, edgeIndex);
                groundSuperiorityOwner[edgeIndex] = OWNER_NONE;
                airSuperiorityOwner[edgeIndex] = OWNER_NONE;
                blockadeOwner[edgeIndex] = OWNER_NONE;
                seededCurrentWar[edgeIndex] = false;
                initialOutcomeOwner[edgeIndex] = OWNER_NONE;
                outcomeOwner[edgeIndex] = OWNER_NONE;
            }
        }

        DenseWarStateCheckpoint captureCheckpoint() {
            return new DenseWarStateCheckpoint(
                    Arrays.copyOf(attackerNationIndex, attackerNationIndex.length),
                    Arrays.copyOf(defenderNationIndex, defenderNationIndex.length),
                    Arrays.copyOf(active, active.length),
                    Arrays.copyOf(attackerMaps, attackerMaps.length),
                    Arrays.copyOf(defenderMaps, defenderMaps.length),
                    Arrays.copyOf(startTurn, startTurn.length),
                    Arrays.copyOf(attackerResistance, attackerResistance.length),
                    Arrays.copyOf(defenderResistance, defenderResistance.length),
                    Arrays.copyOf(warTypes, warTypes.length),
                    Arrays.copyOf(groundSuperiorityOwner, groundSuperiorityOwner.length),
                    Arrays.copyOf(airSuperiorityOwner, airSuperiorityOwner.length),
                    Arrays.copyOf(blockadeOwner, blockadeOwner.length),
                    Arrays.copyOf(seededCurrentWar, seededCurrentWar.length),
                    Arrays.copyOf(initialOutcomeOwner, initialOutcomeOwner.length),
                    Arrays.copyOf(outcomeOwner, outcomeOwner.length),
                    Arrays.copyOf(activePairs, activePairs.length),
                    Arrays.copyOf(pairUnlockTurn, pairUnlockTurn.length),
                    openingEdgeCount,
                    warCount
            );
        }

        void restoreCheckpoint(DenseWarStateCheckpoint checkpoint) {
            ensureCapacity(checkpoint.attackerNationIndex().length);
            System.arraycopy(checkpoint.attackerNationIndex(), 0, attackerNationIndex, 0, checkpoint.attackerNationIndex().length);
            System.arraycopy(checkpoint.defenderNationIndex(), 0, defenderNationIndex, 0, checkpoint.defenderNationIndex().length);
            System.arraycopy(checkpoint.active(), 0, active, 0, checkpoint.active().length);
            System.arraycopy(checkpoint.attackerMaps(), 0, attackerMaps, 0, checkpoint.attackerMaps().length);
            System.arraycopy(checkpoint.defenderMaps(), 0, defenderMaps, 0, checkpoint.defenderMaps().length);
            System.arraycopy(checkpoint.startTurn(), 0, startTurn, 0, checkpoint.startTurn().length);
            System.arraycopy(checkpoint.attackerResistance(), 0, attackerResistance, 0, checkpoint.attackerResistance().length);
            System.arraycopy(checkpoint.defenderResistance(), 0, defenderResistance, 0, checkpoint.defenderResistance().length);
            System.arraycopy(checkpoint.warTypes(), 0, warTypes, 0, checkpoint.warTypes().length);
            System.arraycopy(checkpoint.groundSuperiorityOwner(), 0, groundSuperiorityOwner, 0, checkpoint.groundSuperiorityOwner().length);
            System.arraycopy(checkpoint.airSuperiorityOwner(), 0, airSuperiorityOwner, 0, checkpoint.airSuperiorityOwner().length);
            System.arraycopy(checkpoint.blockadeOwner(), 0, blockadeOwner, 0, checkpoint.blockadeOwner().length);
            System.arraycopy(checkpoint.seededCurrentWar(), 0, seededCurrentWar, 0, checkpoint.seededCurrentWar().length);
            System.arraycopy(checkpoint.initialOutcomeOwner(), 0, initialOutcomeOwner, 0, checkpoint.initialOutcomeOwner().length);
            System.arraycopy(checkpoint.outcomeOwner(), 0, outcomeOwner, 0, checkpoint.outcomeOwner().length);
            System.arraycopy(checkpoint.activePairs(), 0, activePairs, 0, checkpoint.activePairs().length);
            System.arraycopy(checkpoint.pairUnlockTurn(), 0, pairUnlockTurn, 0, checkpoint.pairUnlockTurn().length);
            openingEdgeCount = checkpoint.openingEdgeCount();
            warCount = checkpoint.warCount();
        }

        void applyOpeningAssignment(boolean[] edgeAssigned) {
            for (int edgeIndex = 0; edgeIndex < openingEdgeCount; edgeIndex++) {
                active[edgeIndex] = edgeAssigned[edgeIndex];
                if (edgeAssigned[edgeIndex]) {
                    activePairs[pairIndex(attackerNationIndex[edgeIndex], defenderNationIndex[edgeIndex], nationCount)] = true;
                }
            }
        }

        void appendSeedWars(ProjectionState state, java.util.List<CompiledActiveWar> activeWarSeeds) {
            for (CompiledActiveWar seed : activeWarSeeds) {
                addWarSeed(state, seed);
            }
        }

        void reset(
                CandidateEdgeTable edges,
                boolean[] edgeAssigned,
                ProjectionState state,
                int counterCapacity,
                java.util.List<CompiledActiveWar> activeWarSeeds
        ) {
            int edgeCount = edges.edgeCount();
            ensureCapacity(edgeCount + activeWarSeeds.size() + Math.max(0, counterCapacity));
            Arrays.fill(activePairs, false);
            Arrays.fill(pairUnlockTurn, 0);
            openingEdgeCount = edgeCount;
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                attackerNationIndex[edgeIndex] = edges.attackerIndex(edgeIndex);
                defenderNationIndex[edgeIndex] = state.attackerCount + edges.defenderIndex(edgeIndex);
                active[edgeIndex] = edgeAssigned[edgeIndex];
                if (edgeAssigned[edgeIndex]) {
                    activePairs[pairIndex(attackerNationIndex[edgeIndex], defenderNationIndex[edgeIndex], nationCount)] = true;
                }
                attackerMaps[edgeIndex] = INITIAL_WAR_MAPS;
                defenderMaps[edgeIndex] = INITIAL_WAR_MAPS;
                attackerResistance[edgeIndex] = INITIAL_RESISTANCE;
                defenderResistance[edgeIndex] = INITIAL_RESISTANCE;
                warTypes[edgeIndex] = warTypeFromEdge(edges, edgeIndex);
                groundSuperiorityOwner[edgeIndex] = OWNER_NONE;
                airSuperiorityOwner[edgeIndex] = OWNER_NONE;
                blockadeOwner[edgeIndex] = OWNER_NONE;
                seededCurrentWar[edgeIndex] = false;
                initialOutcomeOwner[edgeIndex] = OWNER_NONE;
                outcomeOwner[edgeIndex] = OWNER_NONE;
            }
            warCount = edgeCount;
            for (CompiledActiveWar seed : activeWarSeeds) {
                addWarSeed(state, seed);
            }
        }



        int addWar(int attackerIndex, int defenderIndex, int turn) {
            return addWar(attackerIndex, defenderIndex, turn, WarType.ORD);
        }

        int addWar(int attackerIndex, int defenderIndex, int turn, WarType warType) {
            ensureCapacity(warCount + 1);
            int index = warCount++;
            attackerNationIndex[index] = attackerIndex;
            defenderNationIndex[index] = defenderIndex;
            active[index] = true;
            activePairs[pairIndex(attackerIndex, defenderIndex, nationCount)] = true;
            pairUnlockTurn[lockoutPairIndex(attackerIndex, defenderIndex, nationCount)] = 0;
            attackerMaps[index] = INITIAL_WAR_MAPS;
            defenderMaps[index] = INITIAL_WAR_MAPS;
            startTurn[index] = turn;
            attackerResistance[index] = INITIAL_RESISTANCE;
            defenderResistance[index] = INITIAL_RESISTANCE;
            warTypes[index] = warType == null ? WarType.ORD : warType;
            groundSuperiorityOwner[index] = OWNER_NONE;
            airSuperiorityOwner[index] = OWNER_NONE;
            blockadeOwner[index] = OWNER_NONE;
            seededCurrentWar[index] = false;
            initialOutcomeOwner[index] = OWNER_NONE;
            outcomeOwner[index] = OWNER_NONE;
            return index;
        }

        private int addWarSeed(ProjectionState state, CompiledActiveWar seed) {
            int attackerIndex = state.nationIndexById(seed.attackerNationId());
            int defenderIndex = state.nationIndexById(seed.defenderNationId());
            if (attackerIndex < 0 || defenderIndex < 0) {
                return -1;
            }
            if (activePairs[pairIndex(attackerIndex, defenderIndex, nationCount)]) {
                return -1;
            }
            int warIndex = addWar(attackerIndex, defenderIndex, seed.startTurn(), seed.warType());
            attackerMaps[warIndex] = seed.attackerMaps();
            defenderMaps[warIndex] = seed.defenderMaps();
            attackerResistance[warIndex] = seed.attackerResistance();
            defenderResistance[warIndex] = seed.defenderResistance();
            groundSuperiorityOwner[warIndex] = ownerCode(seed.groundSuperiorityOwner());
            airSuperiorityOwner[warIndex] = ownerCode(seed.airSuperiorityOwner());
            blockadeOwner[warIndex] = ownerCode(seed.blockadeOwner());
            seededCurrentWar[warIndex] = true;
            initialOutcomeOwner[warIndex] = winningOwner(warIndex);
            outcomeOwner[warIndex] = OWNER_NONE;
            return warIndex;
        }

        private int winningOwner(int warIndex) {
            int attackerControls = PlannerControlStateReducer.controlCountForOwnerCode(
                    OWNER_ATTACKER,
                    groundSuperiorityOwner[warIndex],
                    airSuperiorityOwner[warIndex],
                    blockadeOwner[warIndex]
            );
            int defenderControls = PlannerControlStateReducer.controlCountForOwnerCode(
                    OWNER_DEFENDER,
                    groundSuperiorityOwner[warIndex],
                    airSuperiorityOwner[warIndex],
                    blockadeOwner[warIndex]
            );
            int attackerEdge = attackerResistance[warIndex] - defenderResistance[warIndex];
            if (attackerEdge > 0 || (attackerEdge == 0 && attackerControls > defenderControls)) {
                return OWNER_ATTACKER;
            }
            if (attackerEdge < 0 || defenderControls > attackerControls) {
                return OWNER_DEFENDER;
            }
            return OWNER_NONE;
        }

        private static int ownerCode(CompiledActiveWar.ControlOwner owner) {
            return switch (owner) {
                case ATTACKER -> OWNER_ATTACKER;
                case DEFENDER -> OWNER_DEFENDER;
                default -> OWNER_NONE;
            };
        }

        void deactivateWar(int warIndex, int currentTurn) {
            if (!active[warIndex]) {
                return;
            }
            active[warIndex] = false;
            activePairs[pairIndex(attackerNationIndex[warIndex], defenderNationIndex[warIndex], nationCount)] = false;
            pairUnlockTurn[lockoutPairIndex(attackerNationIndex[warIndex], defenderNationIndex[warIndex], nationCount)]
                    = currentTurn + WarSlotRules.sameOpponentLockoutTurns();
        }

        private void ensureCapacity(int needed) {
            if (needed <= active.length) {
                return;
            }
            int nextCapacity = Math.max(needed, Math.max(4, active.length * 2));
            attackerNationIndex = Arrays.copyOf(attackerNationIndex, nextCapacity);
            defenderNationIndex = Arrays.copyOf(defenderNationIndex, nextCapacity);
            active = Arrays.copyOf(active, nextCapacity);
            attackerMaps = Arrays.copyOf(attackerMaps, nextCapacity);
            defenderMaps = Arrays.copyOf(defenderMaps, nextCapacity);
            startTurn = Arrays.copyOf(startTurn, nextCapacity);
            attackerResistance = Arrays.copyOf(attackerResistance, nextCapacity);
            defenderResistance = Arrays.copyOf(defenderResistance, nextCapacity);
            warTypes = Arrays.copyOf(warTypes, nextCapacity);
            groundSuperiorityOwner = Arrays.copyOf(groundSuperiorityOwner, nextCapacity);
            airSuperiorityOwner = Arrays.copyOf(airSuperiorityOwner, nextCapacity);
            blockadeOwner = Arrays.copyOf(blockadeOwner, nextCapacity);
            seededCurrentWar = Arrays.copyOf(seededCurrentWar, nextCapacity);
            initialOutcomeOwner = Arrays.copyOf(initialOutcomeOwner, nextCapacity);
            outcomeOwner = Arrays.copyOf(outcomeOwner, nextCapacity);
        }

        void fillActiveWarsByNation(boolean[] activeWarsByNation) {
            Arrays.fill(activeWarsByNation, false);
            for (int edgeIndex = 0; edgeIndex < warCount; edgeIndex++) {
                if (!active[edgeIndex]) {
                    continue;
                }
                activeWarsByNation[attackerNationIndex[edgeIndex]] = true;
                activeWarsByNation[defenderNationIndex[edgeIndex]] = true;
            }
        }

        void fillActiveWarCounts(int[] activeOffensiveWarsByNation, int[] activeDefensiveWarsByNation) {
            Arrays.fill(activeOffensiveWarsByNation, 0);
            Arrays.fill(activeDefensiveWarsByNation, 0);
            for (int edgeIndex = 0; edgeIndex < warCount; edgeIndex++) {
                if (!active[edgeIndex]) {
                    continue;
                }
                activeOffensiveWarsByNation[attackerNationIndex[edgeIndex]]++;
                activeDefensiveWarsByNation[defenderNationIndex[edgeIndex]]++;
            }
        }

        boolean hasActivePair(int attackerIndex, int defenderIndex) {
            return activePairs[pairIndex(attackerIndex, defenderIndex, nationCount)];
        }

        int lockoutTurnsRemaining(int attackerIndex, int defenderIndex, int currentTurn) {
            int unlockTurn = pairUnlockTurn[lockoutPairIndex(attackerIndex, defenderIndex, nationCount)];
            return unlockTurn <= currentTurn ? 0 : unlockTurn - currentTurn;
        }

        private static int pairIndex(int attackerIndex, int defenderIndex, int nationCount) {
            return attackerIndex * nationCount + defenderIndex;
        }

        private static int lockoutPairIndex(int attackerIndex, int defenderIndex, int nationCount) {
            int lower = Math.min(attackerIndex, defenderIndex);
            int upper = Math.max(attackerIndex, defenderIndex);
            return lower * nationCount + upper;
        }

        @Override
        public WarType warType(int warIndex) {
            WarType warType = warIndex >= 0 && warIndex < warTypes.length ? warTypes[warIndex] : null;
            return warType == null ? WarType.ORD : warType;
        }

        @Override
        public boolean attackerHasAirControl(int warIndex) {
            return airSuperiorityOwner[warIndex] == OWNER_ATTACKER;
        }

        @Override
        public boolean defenderHasAirControl(int warIndex) {
            return airSuperiorityOwner[warIndex] == OWNER_DEFENDER;
        }

        @Override
        public boolean attackerHasGroundSuperiority(int warIndex) {
            return groundSuperiorityOwner[warIndex] == OWNER_ATTACKER;
        }

        @Override
        public boolean defenderHasGroundSuperiority(int warIndex) {
            return groundSuperiorityOwner[warIndex] == OWNER_DEFENDER;
        }

        @Override
        public boolean attackerFortified(int warIndex) {
            return false;
        }

        @Override
        public boolean defenderFortified(int warIndex) {
            return false;
        }

        @Override
        public int attackerMaps(int warIndex) {
            return attackerMaps[warIndex];
        }

        @Override
        public int defenderMaps(int warIndex) {
            return defenderMaps[warIndex];
        }

        @Override
        public int attackerResistance(int warIndex) {
            return attackerResistance[warIndex];
        }

        @Override
        public int defenderResistance(int warIndex) {
            return defenderResistance[warIndex];
        }

        @Override
        public int blockadeOwner(int warIndex) {
            return switch (blockadeOwner[warIndex]) {
                case OWNER_ATTACKER -> CombatKernel.AttackContext.BLOCKADE_ATTACKER;
                case OWNER_DEFENDER -> CombatKernel.AttackContext.BLOCKADE_DEFENDER;
                default -> CombatKernel.AttackContext.BLOCKADE_NONE;
            };
        }
    }

    private static final class DenseWarContext implements CombatKernel.BufferBackedAttackContext, WarControlRules.MutableWarControlState {
        private final ProjectionState state;
        private final DenseWarState warState;
        private int warIndex;

        private DenseWarContext(ProjectionState state, DenseWarState warState) {
            this.state = state;
            this.warState = warState;
        }

        void setWarIndex(int warIndex) {
            this.warIndex = warIndex;
        }

        @Override
        public boolean isActive() {
            return warState.active[warIndex];
        }

        @Override
        public Integer groundSuperiorityNationId() {
            return controlNationId(warState.groundSuperiorityOwner[warIndex]);
        }

        @Override
        public Integer airSuperiorityNationId() {
            return controlNationId(warState.airSuperiorityOwner[warIndex]);
        }

        @Override
        public Integer blockadeNationId() {
            return controlNationId(warState.blockadeOwner[warIndex]);
        }

        @Override
        public void setgroundSuperiorityNationId(Integer nationId) {
            warState.groundSuperiorityOwner[warIndex] = ownerCode(nationId);
        }

        @Override
        public void setAirSuperiorityNationId(Integer nationId) {
            warState.airSuperiorityOwner[warIndex] = ownerCode(nationId);
        }

        @Override
        public void setBlockadeNationId(Integer nationId) {
            warState.blockadeOwner[warIndex] = ownerCode(nationId);
        }

        private Integer controlNationId(int ownerCode) {
            return switch (ownerCode) {
                case DenseWarState.OWNER_ATTACKER -> state.nationIds[warState.attackerNationIndex[warIndex]];
                case DenseWarState.OWNER_DEFENDER -> state.nationIds[warState.defenderNationIndex[warIndex]];
                default -> null;
            };
        }

        private int ownerCode(Integer nationId) {
            if (nationId == null) {
                return DenseWarState.OWNER_NONE;
            }
            if (nationId == state.nationIds[warState.attackerNationIndex[warIndex]]) {
                return DenseWarState.OWNER_ATTACKER;
            }
            if (nationId == state.nationIds[warState.defenderNationIndex[warIndex]]) {
                return DenseWarState.OWNER_DEFENDER;
            }
            throw new IllegalArgumentException("Nation " + nationId + " is not in projected war edge " + warIndex);
        }

        @Override
        public CombatKernel.NationState attacker() {
            return state.nationViews[warState.attackerNationIndex[warIndex]];
        }

        @Override
        public CombatKernel.NationState defender() {
            return state.nationViews[warState.defenderNationIndex[warIndex]];
        }

        @Override
        public CombatKernel.PrimitiveWarBuffer warBuffer() {
            return warState;
        }

        @Override
        public int warIndex() {
            return warIndex;
        }
    }

    final class ProjectionView implements TeamWarControlView {
        private final ProjectionState state;
        private final DenseWarState warState;
        private final boolean[] edgeAssigned;

        private ProjectionView(
                ProjectionState state,
                DenseWarState warState,
                boolean[] edgeAssigned
        ) {
            this.state = state;
            this.warState = warState;
            this.edgeAssigned = edgeAssigned;
        }

        @Override
        public void forEachNation(NationScoreConsumer consumer) {
            for (int attackerIndex = 0; attackerIndex < state.attackerCount; attackerIndex++) {
                consumer.accept(scenario.attackerNationId(attackerIndex), scenario.attacker(attackerIndex).teamId(), state.score(attackerIndex));
            }
            for (int defenderIndex = 0; defenderIndex < state.defenderCount; defenderIndex++) {
                int nationIndex = state.attackerCount + defenderIndex;
                consumer.accept(scenario.defenderNationId(defenderIndex), scenario.defender(defenderIndex).teamId(), state.score(nationIndex));
            }
        }

        @Override
        public void forEachNationStrategicValue(NationValueConsumer consumer) {
            for (int attackerIndex = 0; attackerIndex < state.attackerCount; attackerIndex++) {
                consumer.accept(
                        scenario.attackerNationId(attackerIndex),
                        scenario.attacker(attackerIndex).teamId(),
                        state.strategicValue(attackerIndex, warState)
                );
            }
            for (int defenderIndex = 0; defenderIndex < state.defenderCount; defenderIndex++) {
                int nationIndex = state.attackerCount + defenderIndex;
                consumer.accept(
                        scenario.defenderNationId(defenderIndex),
                        scenario.defender(defenderIndex).teamId(),
                        state.strategicValue(nationIndex, warState)
                );
            }
        }

        @Override
        public void forEachWarControl(WarControlConsumer consumer) {
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warMetricPresent(warIndex)) {
                    continue;
                }
                int warAttackerTeamId = stateTeamId(warState.attackerNationIndex[warIndex]);
                int warDefenderTeamId = stateTeamId(warState.defenderNationIndex[warIndex]);
                consumer.accept(
                        warAttackerTeamId,
                        warDefenderTeamId,
                        controlTeamId(warIndex, warState.groundSuperiorityOwner[warIndex]),
                        controlTeamId(warIndex, warState.airSuperiorityOwner[warIndex]),
                        controlTeamId(warIndex, warState.blockadeOwner[warIndex]),
                        warState.attackerResistance[warIndex],
                        warState.defenderResistance[warIndex]
                );
            }
        }

        @Override
        public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                int attackerNationIndex = warState.attackerNationIndex[warIndex];
                int defenderNationIndex = warState.defenderNationIndex[warIndex];
                boolean attackerHasAirControl = warState.attackerHasAirControl(warIndex);
                boolean defenderHasAirControl = warState.defenderHasAirControl(warIndex);
                consumer.accept(
                        stateTeamId(attackerNationIndex),
                        stateTeamId(defenderNationIndex),
                        state.targetPressure(attackerNationIndex, defenderNationIndex),
                        OpeningMetricSummary.tacticalMomentumScore(warState.defenderResistance[warIndex]),
                        state.forceWindowScore(attackerNationIndex, defenderNationIndex, attackerHasAirControl, defenderHasAirControl)
                );
            }
        }

        @Override
        public void forEachActiveWarSlotMetric(ActiveWarSlotMetricConsumer consumer) {
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                int attackerNationIndex = warState.attackerNationIndex[warIndex];
                int defenderNationIndex = warState.defenderNationIndex[warIndex];
                double attackerSlotPressure = offensiveSlotPressure(attackerNationIndex);
                double defenderSlotPressure = defensiveSlotPressure(defenderNationIndex);
                int attackerOpponents = activeOpponentCount(attackerNationIndex);
                int defenderOpponents = activeOpponentCount(defenderNationIndex);
                double attackerPressure = state.targetPressure(defenderNationIndex, attackerNationIndex);
                double defenderPressure = state.targetPressure(attackerNationIndex, defenderNationIndex);
                consumer.accept(
                        stateTeamId(attackerNationIndex),
                        stateTeamId(defenderNationIndex),
                        StrategicAssetValue.offensiveWarSlotOpportunityCost(
                        PlannerStrategicValue.offensiveSlotCapabilityValue(
                            state.slotCapabilityValue(attackerNationIndex, warState),
                            attackerSlotPressure
                        ),
                                attackerPressure,
                                attackerSlotPressure,
                                attackerOpponents
                        ),
                        StrategicAssetValue.defensiveWarSlotDenialValue(
                        PlannerStrategicValue.defensiveSlotCapabilityValue(
                            state.slotCapabilityValue(defenderNationIndex, warState),
                            defenderSlotPressure
                        ),
                                defenderPressure,
                                defenderSlotPressure,
                                defenderOpponents
                        )
                );
            }
        }

        private boolean warMetricPresent(int warIndex) {
            return warState.active[warIndex]
                    || warState.seededCurrentWar[warIndex]
                    || (warIndex < edges.edgeCount() && edgeAssigned[warIndex]);
        }

        private int controlTeamId(int warIndex, int ownerCode) {
            return switch (ownerCode) {
                case DenseWarState.OWNER_ATTACKER -> stateTeamId(warState.attackerNationIndex[warIndex]);
                case DenseWarState.OWNER_DEFENDER -> stateTeamId(warState.defenderNationIndex[warIndex]);
                default -> Integer.MIN_VALUE;
            };
        }

        private int stateTeamId(int nationIndex) {
            return nationIndex < scenario.attackerCount()
                    ? scenario.attacker(nationIndex).teamId()
                    : scenario.defender(nationIndex - scenario.attackerCount()).teamId();
        }

        private double offensiveSlotPressure(int nationIndex) {
            DBNationSnapshot snapshot = snapshot(nationIndex);
            int maxOffensiveSlots = Math.max(1, snapshot.maxOff());
            return effectiveOffensiveWars(nationIndex, snapshot.currentOffensiveWars()) / (double) maxOffensiveSlots;
        }

        private double defensiveSlotPressure(int nationIndex) {
            DBNationSnapshot snapshot = snapshot(nationIndex);
            return effectiveDefensiveWars(nationIndex, snapshot.currentDefensiveWars()) / (double) WarSlotRules.defensiveSlotCap();
        }

        private int activeOpponentCount(int nationIndex) {
            int count = 0;
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                if (warState.attackerNationIndex[warIndex] == nationIndex || warState.defenderNationIndex[warIndex] == nationIndex) {
                    count++;
                }
            }
            return Math.max(count, snapshot(nationIndex).activeOpponentNationIds().size());
        }

        private int effectiveOffensiveWars(int nationIndex, int baselineOffensiveWars) {
            int seeded = 0;
            int projected = 0;
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex] || warState.attackerNationIndex[warIndex] != nationIndex) {
                    continue;
                }
                if (warState.seededCurrentWar[warIndex]) {
                    seeded++;
                } else {
                    projected++;
                }
            }
            return Math.max(Math.max(0, baselineOffensiveWars), seeded) + projected;
        }

        private int effectiveDefensiveWars(int nationIndex, int baselineDefensiveWars) {
            int seeded = 0;
            int projected = 0;
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex] || warState.defenderNationIndex[warIndex] != nationIndex) {
                    continue;
                }
                if (warState.seededCurrentWar[warIndex]) {
                    seeded++;
                } else {
                    projected++;
                }
            }
            return Math.max(Math.max(0, baselineDefensiveWars), seeded) + projected;
        }

        private DBNationSnapshot snapshot(int nationIndex) {
            return nationIndex < scenario.attackerCount()
                    ? scenario.attacker(nationIndex)
                    : scenario.defender(nationIndex - scenario.attackerCount());
        }

        int horizonTurns() {
            return horizonTurns;
        }

        ProjectionDiagnostics diagnostics() {
            double attackerStrategicValue = 0d;
            double defenderStrategicValue = 0d;
            double attackerInfraDestroyed = 0d;
            double defenderInfraDestroyed = 0d;
            int[] attackerUnitLosses = new int[SimUnits.PURCHASABLE_UNITS.length];
            int[] defenderUnitLosses = new int[SimUnits.PURCHASABLE_UNITS.length];
            double attackerRebuyPreserved = 0d;
            double defenderRebuyPreserved = 0d;
            int attackerWiped = 0;
            int defenderWiped = 0;
            int attackerWipeRisk = 0;
            int defenderWipeRisk = 0;

            for (int attackerIndex = 0; attackerIndex < state.attackerCount; attackerIndex++) {
                attackerStrategicValue += state.strategicValue(attackerIndex, warState);
                attackerInfraDestroyed += Math.max(0d, state.baselineInfra[attackerIndex] - state.totalInfra(attackerIndex));
                addUnitLosses(attackerUnitLosses, attackerIndex);
                attackerRebuyPreserved += state.rebuyPreservedValue(attackerIndex, warState);
                double baselineStrength = state.baselineCombatStrength(attackerIndex);
                double terminalStrength = state.combatStrength(attackerIndex);
                if (terminalStrength <= 0d && baselineStrength > 0d) {
                    attackerWiped++;
                } else if (isWipeRisk(baselineStrength, terminalStrength)) {
                    attackerWipeRisk++;
                }
            }
            for (int defenderIndex = 0; defenderIndex < state.defenderCount; defenderIndex++) {
                int nationIndex = state.attackerCount + defenderIndex;
                defenderStrategicValue += state.strategicValue(nationIndex, warState);
                defenderInfraDestroyed += Math.max(0d, state.baselineInfra[nationIndex] - state.totalInfra(nationIndex));
                addUnitLosses(defenderUnitLosses, nationIndex);
                defenderRebuyPreserved += state.rebuyPreservedValue(nationIndex, warState);
                double baselineStrength = state.baselineCombatStrength(nationIndex);
                double terminalStrength = state.combatStrength(nationIndex);
                if (terminalStrength <= 0d && baselineStrength > 0d) {
                    defenderWiped++;
                } else if (isWipeRisk(baselineStrength, terminalStrength)) {
                    defenderWipeRisk++;
                }
            }

            int activeWars = 0;
            int attackerSuperiorityFlags = 0;
            int defenderSuperiorityFlags = 0;
            int attackerWinningWars = 0;
            int defenderWinningWars = 0;
            int concludedWars = 0;
            int currentWarOutcomeFlips = 0;
            int[] concludedWarsByDefenderTier = new int[TierSegment.values().length];
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (warState.active[warIndex]) {
                    activeWars++;
                    int attackerControls = PlannerControlStateReducer.controlCountForOwnerCode(
                            DenseWarState.OWNER_ATTACKER,
                            warState.groundSuperiorityOwner[warIndex],
                            warState.airSuperiorityOwner[warIndex],
                            warState.blockadeOwner[warIndex]
                    );
                    int defenderControls = PlannerControlStateReducer.controlCountForOwnerCode(
                            DenseWarState.OWNER_DEFENDER,
                            warState.groundSuperiorityOwner[warIndex],
                            warState.airSuperiorityOwner[warIndex],
                            warState.blockadeOwner[warIndex]
                    );
                    attackerSuperiorityFlags += attackerControls;
                    defenderSuperiorityFlags += defenderControls;
                    int attackerEdge = warState.attackerResistance[warIndex] - warState.defenderResistance[warIndex];
                    if (attackerEdge > 0 || (attackerEdge == 0 && attackerControls > defenderControls)) {
                        attackerWinningWars++;
                    } else if (attackerEdge < 0 || defenderControls > attackerControls) {
                        defenderWinningWars++;
                    }
                    int terminalOwner = warState.winningOwner(warIndex);
                    if (warState.seededCurrentWar[warIndex]
                            && warState.initialOutcomeOwner[warIndex] != DenseWarState.OWNER_NONE
                            && terminalOwner != DenseWarState.OWNER_NONE
                            && terminalOwner != warState.initialOutcomeOwner[warIndex]) {
                        currentWarOutcomeFlips++;
                    }
                } else if (warMetricPresent(warIndex)) {
                    concludedWars++;
                    if (warState.seededCurrentWar[warIndex]
                            && warState.initialOutcomeOwner[warIndex] != DenseWarState.OWNER_NONE
                            && warState.outcomeOwner[warIndex] != DenseWarState.OWNER_NONE
                            && warState.outcomeOwner[warIndex] != warState.initialOutcomeOwner[warIndex]) {
                        currentWarOutcomeFlips++;
                    }
                    int defenderNationIndex = warState.defenderNationIndex[warIndex];
                    if (defenderNationIndex >= state.attackerCount) {
                        int defenderIndex = defenderNationIndex - state.attackerCount;
                        concludedWarsByDefenderTier[TierSegment.fromCities(scenario.defender(defenderIndex).cities()).ordinal()]++;
                    }
                }
            }

            double attackerRebuyDestroyed = projectedRebuyDestroyedValue(0, state.attackerCount, true);
            double defenderRebuyDestroyed = projectedRebuyDestroyedValue(
                    state.attackerCount,
                    state.attackerCount + state.defenderCount,
                    false
            );
            return new ProjectionDiagnostics(
                    attackerStrategicValue,
                    defenderStrategicValue,
                    attackerUnitLosses,
                    defenderUnitLosses,
                    attackerRebuyPreserved,
                    defenderRebuyPreserved,
                    attackerRebuyDestroyed,
                    defenderRebuyDestroyed,
                    attackerInfraDestroyed,
                    defenderInfraDestroyed,
                    attackerWiped,
                    defenderWiped,
                    attackerWipeRisk,
                    defenderWipeRisk,
                    activeWars,
                    attackerSuperiorityFlags,
                    defenderSuperiorityFlags,
                    attackerWinningWars,
                    defenderWinningWars,
                    currentWarOutcomeFlips,
                    concludedWars,
                    concludedWarsByDefenderTier,
                    state.turnsAttackerHeldNetControl,
                    state.turnsDefenderHeldNetControl,
                    state.turnsNoControl
            );
        }

        private boolean isWipeRisk(double baselineStrength, double terminalStrength) {
            return baselineStrength > 0d
                    && terminalStrength > 0d
                    && terminalStrength <= baselineStrength * WIPE_RISK_COMBAT_STRENGTH_RATIO;
        }

        private double projectedRebuyDestroyedValue(int startNationIndex, int endNationIndex, boolean attackerSide) {
            double lossRecoveryValue = 0d;
            for (int nationIndex = startNationIndex; nationIndex < endNationIndex; nationIndex++) {
                int unitBase = state.unitBaseOffsets[nationIndex];
                for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                    int losses = state.cumulativeUnitLossesFlat[unitBase + unit.ordinal()];
                    if (losses > 0) {
                        lossRecoveryValue += StrategicAssetValue.projectedRecoveryValue(
                                unit,
                                losses,
                                state.researchBits[nationIndex]
                        );
                    }
                }
            }
            double[] values = attackerSide ? attackerProjectedBuyScore : defenderProjectedBuyScore;
            double projectedCapacityValue = 0d;
            for (double value : values) {
                projectedCapacityValue += value;
            }
            return Math.min(lossRecoveryValue, projectedCapacityValue);
        }

        private void addUnitLosses(int[] out, int nationIndex) {
            int unitBase = state.unitBaseOffsets[nationIndex];
            for (int unitIndex = 0; unitIndex < SimUnits.PURCHASABLE_UNITS.length; unitIndex++) {
                MilitaryUnit unit = SimUnits.PURCHASABLE_UNITS[unitIndex];
                int loss = state.cumulativeUnitLossesFlat[unitBase + unit.ordinal()];
                if (loss > 0) {
                    out[unitIndex] += loss;
                }
            }
        }
    }

    /**
     * Dense per-nation snapshot of mid-horizon projected combat strength and score relative to the
     * baseline (pre-simulation) values, plus the realized counter incidence vector that drove the
     * over-counter detection.
     *
     * <p>Used by the optimizer to rebuild candidate edge components (immediate harm, control
     * leverage, future-war leverage) from real projected mid-horizon nation state.</p>
     */
    record MidHorizonSnapshot(
            double[] attackerStrengthsBaseline,
            double[] attackerStrengthsMid,
            double[] defenderStrengthsBaseline,
            double[] defenderStrengthsMid,
            double[] attackerScoresBaseline,
            double[] attackerScoresMid,
            double[] defenderScoresBaseline,
            double[] defenderScoresMid,
            int[] realizedCounterIncidence
    ) {
        /**
         * Returns the multiplicative factor for an attacker's outgoing edges, comparing projected
         * mid-horizon combat strength + score to the baseline. Clamped to a small positive floor so
         * an attacker that gets fully wiped is still preferred over one that has not been touched
         * but is otherwise at parity.
         */
        double attackerEdgeFactor(int attackerIndex) {
            return clampedRatio(attackerStrengthsBaseline, attackerStrengthsMid, attackerIndex,
                    attackerScoresBaseline, attackerScoresMid);
        }

        double defenderEdgeFactor(int defenderIndex) {
            return clampedRatio(defenderStrengthsBaseline, defenderStrengthsMid, defenderIndex,
                    defenderScoresBaseline, defenderScoresMid);
        }

        private static double clampedRatio(
                double[] strengthBase,
                double[] strengthMid,
                int index,
                double[] scoreBase,
                double[] scoreMid
        ) {
            if (index < 0 || index >= strengthBase.length) {
                return 1d;
            }
            double strengthRatio = strengthBase[index] > 0d
                    ? strengthMid[index] / strengthBase[index]
                    : 1d;
            double scoreRatio = scoreBase[index] > 0d
                    ? scoreMid[index] / scoreBase[index]
                    : 1d;
            // Geometric mean weights strength + score equally so a heavily damaged attacker drops
            // both immediate harm AND future-war leverage proportionally.
            double combined = Math.sqrt(Math.max(0d, strengthRatio) * Math.max(0d, scoreRatio));
            return Math.max(0.01d, Math.min(1d, combined));
        }
    }

            private record MidHorizonBaseline(
                double[] attackerStrengthsBaseline,
                double[] defenderStrengthsBaseline,
                double[] attackerScoresBaseline,
                double[] defenderScoresBaseline
            ) {
            }

    record ProjectedEvaluation(
            double objectiveScore,
            int[] realizedCounterIncidence
    ) {
    }

    record ProjectedFeedbackEvaluation(
            ProjectedEvaluation projectedEvaluation,
            MidHorizonSnapshot midHorizonSnapshot
    ) {
    }

    private record ActiveWarProfileKey(
            boolean[] attackerActive,
            boolean[] defenderActive
    ) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ActiveWarProfileKey other)) {
                return false;
            }
            return Arrays.equals(attackerActive, other.attackerActive)
                    && Arrays.equals(defenderActive, other.defenderActive);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(attackerActive);
            result = 31 * result + Arrays.hashCode(defenderActive);
            return result;
        }
    }

    private record ProjectionStateCheckpoint(
            int[] unitsFlat,
            int[] unitsBoughtTodayFlat,
            int[] pendingBuysFlat,
            double[] resourcesFlat,
            double[] cityInfraFlat,
            double[] scores,
            int[] beigeTurns
    ) {
    }

    private record DenseWarStateCheckpoint(
            int[] attackerNationIndex,
            int[] defenderNationIndex,
            boolean[] active,
            int[] attackerMaps,
            int[] defenderMaps,
            int[] startTurn,
            int[] attackerResistance,
            int[] defenderResistance,
            WarType[] warTypes,
            int[] groundSuperiorityOwner,
            int[] airSuperiorityOwner,
            int[] blockadeOwner,
            boolean[] seededCurrentWar,
            int[] initialOutcomeOwner,
            int[] outcomeOwner,
            boolean[] activePairs,
                int[] pairUnlockTurn,
            int openingEdgeCount,
            int warCount
    ) {
    }

    record ProjectionDiagnostics(
            double attackerStrategicValue,
            double defenderStrategicValue,
            int[] attackerUnitLosses,
            int[] defenderUnitLosses,
            double attackerRebuyPreservedValue,
            double defenderRebuyPreservedValue,
            double attackerRebuyDestroyedValue,
            double defenderRebuyDestroyedValue,
            double attackerInfraDestroyed,
            double defenderInfraDestroyed,
            int attackerWiped,
            int defenderWiped,
            int attackerWipeRisk,
            int defenderWipeRisk,
            int activeWars,
            int attackerSuperiorityFlags,
            int defenderSuperiorityFlags,
            int attackerWinningWars,
            int defenderWinningWars,
            int currentWarOutcomeFlips,
            int concludedWars,
            int[] concludedWarsByDefenderTier,
            int turnsAttackerHeldNetControl,
            int turnsDefenderHeldNetControl,
            int turnsNoControl
    ) {
    }

            private record ProjectedLaterDeclarationInputs(
                CompiledScenario scenario,
                CandidateEdgeTable edges,
                Map<Integer, Integer> declarerOverallIndexesByNationId,
                    Map<Integer, Integer> targetOverallIndexesByNationId,
                    Map<Long, Integer> edgeIndexByPair
            ) {
            }

                private record ProjectedAssignedDeclaration(
                    int declarerNationId,
                    int targetNationId,
                    int declarerNationIndex,
                    int targetNationIndex,
                    int edgeIndex
                ) {
                }

    private enum TierSegment {
        LOW,
        MID,
        HIGH;

        static TierSegment fromCities(int cities) {
            if (cities >= 28) {
                return HIGH;
            }
            if (cities >= 20) {
                return MID;
            }
            return LOW;
        }
    }
}
