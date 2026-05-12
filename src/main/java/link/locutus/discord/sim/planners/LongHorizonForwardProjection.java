package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.DamageObjective;
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
import link.locutus.discord.sim.planners.compile.OpeningEvaluationScenario;
import link.locutus.discord.sim.planners.compile.ProjectedOpeningEvaluationScenario;
import link.locutus.discord.sim.planners.compile.ScenarioCompiler;
import link.locutus.discord.util.PW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Primitive forward-projection surface for long-horizon blitz assignment scoring.
 *
 * <p>This is intentionally not a replay engine. It owns dense planner-local arrays that can price
 * terminal score, active-war control metrics, unit rebuy capacity, and expected counter exposure
 * without constructing local conflict worlds for each assignment candidate.</p>
 */
final class LongHorizonForwardProjection {
    static final class ScenarioBoundInputs {
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

        private ScenarioBoundInputs(
                CompiledScenario scenario,
                int horizonTurns,
                double horizonFactor,
                double[] attackerInitialScores,
                double[] defenderInitialScores,
                double[] attackerProjectedBuyScore,
                double[] defenderProjectedBuyScore,
                double[] attackerCombatStrengths,
                double[] defenderCombatStrengths,
                LongHorizonCounterOpportunityModel counterOpportunityModel
        ) {
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
        }

        private void requireCompatible(CompiledScenario scenario, int horizonTurns, double horizonFactor) {
            if (this.scenario != scenario || this.horizonTurns != horizonTurns || Double.compare(this.horizonFactor, horizonFactor) != 0) {
                throw new IllegalArgumentException("Scenario-bound projection inputs do not match the requested projection variant");
            }
        }

        LongHorizonCounterOpportunityModel counterOpportunityModel() {
            return counterOpportunityModel;
        }
    }

    private static final ScenarioCompiler PROJECTED_DECLARATION_SCENARIO_COMPILER = new ScenarioCompiler();
    private static final PlannerProfiler.CounterToken PROFILED_PREPARED_STATE_PROFILES = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "preparedStateProfiles"
    );
    private static final PlannerProfiler.CounterToken PROFILED_PREPARED_STATE_RESTORES = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "preparedStateRestores"
    );
    private static final PlannerProfiler.CounterToken PROFILED_PREPARED_WAR_TEMPLATE_BUILDS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "preparedWarTemplateBuilds"
    );
    private static final PlannerProfiler.CounterToken PROFILED_PREPARED_WAR_RESTORES = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "preparedWarRestores"
    );
    private static final PlannerProfiler.CounterToken PROFILED_PROJECTION_TURNS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "projectionTurns"
    );
    private static final PlannerProfiler.CounterToken PROFILED_COUNTER_TURNS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "counterTurns"
    );
    private static final PlannerProfiler.CounterToken PROFILED_COUNTER_TURNS_NO_SLOTS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "counterTurnsNoSlots"
    );
    private static final PlannerProfiler.CounterToken PROFILED_COUNTER_CANDIDATE_EVALUATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "counterCandidateEvaluations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_COUNTER_DECLARATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "respondingSideLaterDeclarations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_COUNTER_DECLARATIONS_THROTTLED = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "respondingSideLaterDeclarationsThrottled"
    );
    private static final PlannerProfiler.CounterToken PROFILED_REDECLARE_TURNS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "redeclareTurns"
    );
    private static final PlannerProfiler.CounterToken PROFILED_REDECLARE_TURNS_NO_SLOTS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "redeclareTurnsNoSlots"
    );
    private static final PlannerProfiler.CounterToken PROFILED_REDECLARE_CANDIDATE_EVALUATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "redeclareCandidateEvaluations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_REDECLARE_DECLARATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "openingSideLaterDeclarations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_WAR_ITERATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "warIterations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_ATTACK_CHOICE_CALLS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "attackChoiceCalls"
    );
    private static final PlannerProfiler.CounterToken PROFILED_ATTACK_TYPE_EVALUATIONS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "attackTypeEvaluations"
    );
    private static final PlannerProfiler.CounterToken PROFILED_RESOLVED_ATTACKS = PlannerProfiler.counterToken(
        PlannerProfiler.Scope.LONG_HORIZON_PROJECTED_EVALUATION,
        "resolvedAttacks"
    );
    private static final int INITIAL_WAR_MAPS = 6;
    private static final int INITIAL_RESISTANCE = 100;
    private static final int MAP_CAP = 12;
    private static final int WAR_EXPIRATION_TURN = 60;
    private static final int PROJECTED_COUNTER_START_TURN = 1;
    private static final double MIN_PROJECTED_DECLARATION_TARGET_VALUE = 50d;
    private static final double PROJECTED_DECLARATION_TARGET_VALUE_MULTIPLIER = 0.10d;
    private static final double MAX_PROJECTED_DECLARATION_STRENGTH_RATIO = 2.0d;
    private static final int PROJECTED_DECLARATION_TOP_K_MAX_LIMIT = 256;
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

    private enum ProjectedLaterDeclarationLane {
        OPENING_SIDE,
        RESPONDING_SIDE
    }

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
    private final PreparedProjectionCaches preparedCaches;
    private final AttackScratch projectionScratch;
    private final MutableAttackResult projectionResult;
    private final boolean[] scratchActiveWarsByNation;
    private final int[] scratchActiveOffWarsByNation;
    private final int[] scratchActiveDefWarsByNation;
    private final int[] scratchCounterOffSlots;
    private final int[] scratchCounterDefSlots;
    private final int[] scratchRedeclareAttSlots;
    private final int[] scratchRedeclareDefSlots;
    private final int[] scratchCounterIncidence;
    private final int[] scratchProjectedDeclarationSeededOffWars;
    private final int[] scratchProjectedDeclarationProjectedOffWars;
    private final int[] scratchProjectedDeclarationSeededDefWars;
    private final int[] scratchProjectedDeclarationProjectedDefWars;
    private final IntOpenHashSet[] scratchProjectedDeclarationActiveOpponentsByNation;
    private final ProjectionAttackEvaluator projectionAttackEvaluator;
    private final HeuristicAttackChoicePolicy.MutableAttackCandidate heuristicAttackCandidate;
    private final MutableAttackResult heuristicAttackSelectionResult;
    private final DenseWarContext projectionWarContext;
    private long profiledProjectionTurns;
    private long profiledCounterTurns;
    private long profiledCounterTurnsNoSlots;
    private long profiledCounterCandidateEvaluations;
    private long profiledCounterDeclarations;
    private long profiledCounterDeclarationsThrottled;
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
                SideProjectionPolicies defenderProjectionPolicies,
                PreparedProjectionCaches preparedCaches
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
        this.preparedCaches = preparedCaches == null ? new PreparedProjectionCaches() : preparedCaches;
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
        this.scratchRedeclareAttSlots = new int[attackerCountVal];
        this.scratchRedeclareDefSlots = new int[defenderCountVal];
        this.scratchCounterIncidence = new int[attackerCountVal];
        this.scratchProjectedDeclarationSeededOffWars = new int[nationCount];
        this.scratchProjectedDeclarationProjectedOffWars = new int[nationCount];
        this.scratchProjectedDeclarationSeededDefWars = new int[nationCount];
        this.scratchProjectedDeclarationProjectedDefWars = new int[nationCount];
        this.scratchProjectedDeclarationActiveOpponentsByNation = new IntOpenHashSet[nationCount];
        this.projectionAttackEvaluator = new ProjectionAttackEvaluator();
        this.heuristicAttackCandidate = new HeuristicAttackChoicePolicy.MutableAttackCandidate();
        this.heuristicAttackSelectionResult = new MutableAttackResult();
        this.projectionWarContext = new DenseWarContext();
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
            return create(
                edges,
                scenario,
                attackerCaps,
                horizonTurns,
                horizonFactor,
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies,
                scenarioBoundInputs(scenario, horizonTurns, horizonFactor)
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
                SideProjectionPolicies defenderProjectionPolicies,
                ScenarioBoundInputs scenarioBoundInputs
                ) {
                return create(
                    edges,
                    scenario,
                    attackerCaps,
                    horizonTurns,
                    horizonFactor,
                    projectionObjective,
                    attackerOpeningSettings,
                    defenderOpeningSettings,
                    attackerPlannerSettings,
                    defenderPlannerSettings,
                    attackerProjectionPolicies,
                    defenderProjectionPolicies,
                    scenarioBoundInputs,
                    null
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
                    SideProjectionPolicies defenderProjectionPolicies,
                    ScenarioBoundInputs scenarioBoundInputs,
                    PreparedProjectionCaches preparedCaches
            ) {
            scenarioBoundInputs.requireCompatible(scenario, horizonTurns, horizonFactor);
        return new LongHorizonForwardProjection(
                edges,
                scenario,
                Math.max(1, horizonTurns),
                horizonFactor,
                scenarioBoundInputs.attackerInitialScores,
                scenarioBoundInputs.defenderInitialScores,
                scenarioBoundInputs.attackerProjectedBuyScore,
                scenarioBoundInputs.defenderProjectedBuyScore,
                scenarioBoundInputs.attackerCombatStrengths,
                scenarioBoundInputs.defenderCombatStrengths,
                scenarioBoundInputs.counterOpportunityModel,
                Arrays.copyOf(attackerCaps, attackerCaps.length),
                projectionObjective,
                attackerOpeningSettings,
                defenderOpeningSettings,
                attackerPlannerSettings,
                defenderPlannerSettings,
                attackerProjectionPolicies,
                defenderProjectionPolicies,
                preparedCaches
        );
    }

    static ScenarioBoundInputs scenarioBoundInputs(
            CompiledScenario scenario,
            int horizonTurns,
            double horizonFactor
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
        return new ScenarioBoundInputs(
                scenario,
                Math.max(1, horizonTurns),
                horizonFactor,
                attackerInitialScores,
                defenderInitialScores,
                attackerProjectedBuyScore,
                defenderProjectedBuyScore,
                attackerCombatStrengths,
                defenderCombatStrengths,
                counterOpportunityModel
        );
    }

    static LongHorizonCounterOpportunityModel counterOpportunityModel(
            CompiledScenario scenario,
            int horizonTurns,
            double horizonFactor
    ) {
        return scenarioBoundInputs(scenario, horizonTurns, horizonFactor).counterOpportunityModel();
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

    PreparedProjectionCaches sharedPreparedCaches() {
        return preparedCaches;
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
        int[] counterIncidence = resetCounterIncidenceScratch();
        ProjectionView view = project(edgeAssigned, attackerCounts, defenderCounts, counterIncidence);
        return new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence.clone());
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
        int[] counterIncidence = resetCounterIncidenceScratch();
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
            new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence.clone()),
            midHorizonSnapshot
        );
        }

    ProjectedAttackerFeedbackEvaluation projectedAttackerFeedbackEvaluation(
            StrategicObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int midHorizonTurns
    ) {
        int turns = Math.max(1, Math.min(horizonTurns, midHorizonTurns));
        int[] counterIncidence = resetCounterIncidenceScratch();
        resetProjectedEvaluationProfile();
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        warState.fillActiveWarsByNation(scratchActiveWarsByNation);
        AttackerMidHorizonBaseline baseline = captureAttackerMidHorizonBaseline(state);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, 0, turns);
        AttackerMidHorizonSnapshot midHorizonSnapshot = captureAttackerMidHorizonSnapshot(state, baseline);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, turns, horizonTurns);
        flushProjectedEvaluationProfile();
        ProjectionView view = new ProjectionView(state, warState, edgeAssigned);
        return new ProjectedAttackerFeedbackEvaluation(
            new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence.clone()),
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
        int[] counterIncidence = resetCounterIncidenceScratch();
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        simulateProjectedWars(state, warState, attackerCounts, defenderCounts, counterIncidence);
        return counterIncidence.clone();
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
        int[] counterIncidence = resetCounterIncidenceScratch();
        ProjectionState state = prepareProjectionState(attackerCounts, defenderCounts, false);
        DenseWarState warState = prepareWarState(edgeAssigned);
        warState.fillActiveWarsByNation(scratchActiveWarsByNation);
        MidHorizonBaseline baseline = captureMidHorizonBaseline(state);
        runProjectionTurns(state, warState, scratchActiveWarsByNation, attackerCounts, defenderCounts, counterIncidence, 0, turns);
        return captureMidHorizonSnapshot(state, baseline, counterIncidence);
    }

    private int[] resetCounterIncidenceScratch() {
        Arrays.fill(scratchCounterIncidence, 0);
        return scratchCounterIncidence;
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
        ProjectionStateCheckpoint checkpoint = preparedCaches.stateCheckpoints.get(key);
        if (checkpoint == null) {
            state.resetMutableState();
            fillActiveWarsByNationProfile(attackerCounts, defenderCounts, scratchActiveWarsByNation);
            state.materializePendingBuys();
            state.applyDailyBuys(false, scratchActiveWarsByNation);
            checkpoint = state.captureCheckpoint();
            preparedCaches.stateCheckpoints.put(key, checkpoint);
            profiledPreparedStateProfiles++;
        } else {
            state.restoreCheckpoint(checkpoint);
            profiledPreparedStateRestores++;
        }
        return state;
    }

    private DenseWarState prepareWarState(boolean[] edgeAssigned) {
        DenseWarState state = ensureWarState();
        DenseWarStateCheckpoint preparedWarTemplateCheckpoint = preparedCaches.warTemplateCheckpointFor(edges);
        if (preparedWarTemplateCheckpoint == null) {
            state.initializeOpeningTemplate(edges, projectionState, maxProjectedExtraDeclareCapacity());
            preparedCaches.rememberWarTemplate(edges, state.captureCheckpoint());
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
        DenseWarContext context = projectionWarContext.bind(state, warState);
        int start = Math.max(0, Math.min(horizonTurns, startTurn));
        int bound = Math.max(start, Math.min(horizonTurns, endTurnExclusive));
        for (int turn = start; turn < bound; turn++) {
            profiledProjectionTurns++;
            if (turn > 0) {
                advanceTurn(state, warState, turn, activeWarsByNation);
            }
            if (shouldDeclareProjectedLaterDeclarations(ProjectedLaterDeclarationLane.RESPONDING_SIDE, turn, warState, attackerCounts, defenderCounts)) {
                declareProjectedLaterDeclarations(
                        state,
                        warState,
                        ProjectedLaterDeclarationLane.RESPONDING_SIDE,
                        turn,
                        counterIncidenceOut
                );
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            if (shouldDeclareProjectedLaterDeclarations(ProjectedLaterDeclarationLane.OPENING_SIDE, turn, warState, attackerCounts, defenderCounts)) {
                declareProjectedLaterDeclarations(
                        state,
                        warState,
                        ProjectedLaterDeclarationLane.OPENING_SIDE,
                        turn,
                        null
                );
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            for (int warIndex = warState.firstActiveWar(); warIndex >= 0; ) {
                int nextWarIndex = warState.nextActiveWar(warIndex);
                profiledWarIterations++;
                context.setWarIndex(warIndex);
                simulateAdaptiveAttacks(state, warState, context, projectionScratch, projectionResult);
                warIndex = nextWarIndex;
            }
            if (state.collectDiagnostics) {
                // Strategic control per turn: measured by resistance edge across all active wars.
                // Attacker controls a war when the defender's resistance is lower (being drained faster).
                // Net leverage = sum over wars of (defenderResistance - attackerResistance);
                // positive = attacker side holds net control, negative = defender side holds net control.
                int netResistanceEdge = 0;
                for (int wi = warState.firstActiveWar(); wi >= 0; wi = warState.nextActiveWar(wi)) {
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
        long[] activeWords = new long[(scenario.attackerCount() + scenario.defenderCount() + Long.SIZE - 1) / Long.SIZE];
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] > 0) {
                activeWords[attackerIndex >>> 6] |= 1L << (attackerIndex & 63);
            }
        }
        for (int defenderIndex = 0; defenderIndex < defenderCounts.length; defenderIndex++) {
            if (defenderCounts[defenderIndex] > 0) {
                int nationIndex = scenario.attackerCount() + defenderIndex;
                activeWords[nationIndex >>> 6] |= 1L << (nationIndex & 63);
            }
        }
        return new ActiveWarProfileKey(activeWords, Arrays.hashCode(activeWords));
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

    private AttackerMidHorizonBaseline captureAttackerMidHorizonBaseline(ProjectionState state) {
        int attackerCount = scenario.attackerCount();
        double[] attackerBaselineStrengths = new double[attackerCount];
        double[] attackerBaselineScores = new double[attackerCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            attackerBaselineStrengths[attackerIndex] = state.combatStrength(attackerIndex);
            attackerBaselineScores[attackerIndex] = state.score(attackerIndex);
        }
        return new AttackerMidHorizonBaseline(attackerBaselineStrengths, attackerBaselineScores);
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

    private AttackerMidHorizonSnapshot captureAttackerMidHorizonSnapshot(
            ProjectionState state,
            AttackerMidHorizonBaseline baseline
    ) {
        int attackerCount = scenario.attackerCount();
        double[] attackerStrengths = new double[attackerCount];
        double[] attackerScores = new double[attackerCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            attackerStrengths[attackerIndex] = state.combatStrength(attackerIndex);
            attackerScores[attackerIndex] = state.score(attackerIndex);
        }
        return new AttackerMidHorizonSnapshot(
                baseline.attackerStrengthsBaseline(),
                attackerStrengths,
                baseline.attackerScoresBaseline(),
                attackerScores
        );
    }

    private boolean shouldDeclareProjectedLaterDeclarations(
            ProjectedLaterDeclarationLane lane,
            int turn,
            DenseWarState warState,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
            return edges.edgeCount() > 0 && warState.warCount > 0;
        }
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

    private void declareProjectedLaterDeclarations(
            ProjectionState state,
            DenseWarState warState,
            ProjectedLaterDeclarationLane lane,
            int turn,
            int[] counterIncidenceOut
    ) {
        if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
            profiledRedeclareTurns++;
        } else {
            profiledCounterTurns++;
        }
        SidePlannerSettings plannerSettings = lane == ProjectedLaterDeclarationLane.OPENING_SIDE
                ? attackerPlannerSettings
                : defenderPlannerSettings;
        warState.fillActiveWarCounts(scratchActiveOffWarsByNation, scratchActiveDefWarsByNation);
        int[] remainingDeclarerSlots = lane == ProjectedLaterDeclarationLane.OPENING_SIDE
                ? scratchRedeclareAttSlots
                : scratchCounterOffSlots;
        int[] remainingTargetSlots = lane == ProjectedLaterDeclarationLane.OPENING_SIDE
                ? scratchRedeclareDefSlots
                : scratchCounterDefSlots;
        fillProjectedLaterDeclarationOffensiveSlots(
                state,
                lane,
                scratchActiveOffWarsByNation,
                remainingDeclarerSlots,
                plannerSettings.activityActThreshold()
        );
        fillProjectedLaterDeclarationTargetDefensiveSlots(
                state,
                lane,
                scratchActiveDefWarsByNation,
                remainingTargetSlots
        );
        if (!hasAnyAvailable(remainingDeclarerSlots) || !hasAnyAvailable(remainingTargetSlots)) {
            if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
                profiledRedeclareTurnsNoSlots++;
            } else {
                profiledCounterTurnsNoSlots++;
            }
            return;
        }
        ProjectedLaterDeclarationInputs inputs = buildProjectedLaterDeclarationInputs(
                lane,
                state,
                warState,
                remainingDeclarerSlots,
                remainingTargetSlots,
                plannerSettings,
                turn
        );
        if (inputs == null) {
            if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
                profiledRedeclareTurnsNoSlots++;
            } else {
                profiledCounterTurnsNoSlots++;
            }
            return;
        }
        int declarations = applyProjectedLaterDeclarationPlan(
                warState,
                inputs,
                plannerSettings,
                turn,
                lane == ProjectedLaterDeclarationLane.RESPONDING_SIDE ? counterIncidenceOut : null,
                lane == ProjectedLaterDeclarationLane.RESPONDING_SIDE
                        ? plannerSettings.maxLaterDeclarationsPerTurn()
                        : Integer.MAX_VALUE,
                lane == ProjectedLaterDeclarationLane.RESPONDING_SIDE
        );
        if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
            profiledRedeclarations += declarations;
        } else {
            profiledCounterDeclarations += declarations;
        }
    }

    private int projectedExtraDeclareCapacity(int[] attackerCounts) {
        int opposingSideTargetCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] > 0) {
                opposingSideTargetCapacity += scenario.attacker(attackerIndex).rawFreeDef();
            }
        }
        int opposingSideDeclarerCapacity = 0;
        int openingSideTargetCapacity = 0;
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            opposingSideDeclarerCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeOff());
            openingSideTargetCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeDef());
        }
        int openingSideDeclarerCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            openingSideDeclarerCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeOff());
        }
        int opposingSideSimultaneous = Math.min(opposingSideTargetCapacity, opposingSideDeclarerCapacity);
        int openingSideSimultaneous = Math.min(openingSideDeclarerCapacity, openingSideTargetCapacity);
        int slotReuseCycles = Math.max(1, 1 + (horizonTurns / WAR_EXPIRATION_TURN));
        return (opposingSideSimultaneous + openingSideSimultaneous) * slotReuseCycles;
    }

    private int maxProjectedExtraDeclareCapacity() {
        int opposingSideTargetCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            opposingSideTargetCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeDef());
        }
        int opposingSideDeclarerCapacity = 0;
        int openingSideTargetCapacity = 0;
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            opposingSideDeclarerCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeOff());
            openingSideTargetCapacity += Math.max(0, scenario.defender(defenderIndex).rawFreeDef());
        }
        int openingSideDeclarerCapacity = 0;
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            openingSideDeclarerCapacity += Math.max(0, scenario.attacker(attackerIndex).rawFreeOff());
        }
        int opposingSideSimultaneous = Math.min(opposingSideTargetCapacity, opposingSideDeclarerCapacity);
        int openingSideSimultaneous = Math.min(openingSideDeclarerCapacity, openingSideTargetCapacity);
        int slotReuseCycles = Math.max(1, 1 + (horizonTurns / WAR_EXPIRATION_TURN));
        return (opposingSideSimultaneous + openingSideSimultaneous) * slotReuseCycles;
    }

    private static boolean hasAnyAvailable(int[] slots) {
        for (int slotCount : slots) {
            if (slotCount > 0) {
                return true;
            }
        }
        return false;
    }



    private void fillProjectedLaterDeclarationOffensiveSlots(
            ProjectionState state,
            ProjectedLaterDeclarationLane lane,
            int[] activeOffensiveWarsByNation,
            int[] slots,
            double activityThreshold
    ) {
        Arrays.fill(slots, 0);
        if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
            for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                    continue;
                }
                int rawFreeOff = scenario.attacker(attackerIndex).rawFreeOff();
                slots[attackerIndex] = Math.max(
                        0,
                        Math.min(attackerCaps[attackerIndex], rawFreeOff) - activeOffensiveWarsByNation[attackerIndex]
                );
            }
            return;
        }
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            int nationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[nationIndex] > 0
                    || state.combatStrength(nationIndex) <= 0d
                    || scenario.defenderActivityWeight(defenderIndex) < activityThreshold) {
                continue;
            }
            slots[defenderIndex] = Math.max(
                    0,
                    scenario.defender(defenderIndex).rawFreeOff() - activeOffensiveWarsByNation[nationIndex]
            );
        }
    }

    private void fillProjectedLaterDeclarationTargetDefensiveSlots(
            ProjectionState state,
            ProjectedLaterDeclarationLane lane,
            int[] activeDefensiveWarsByNation,
            int[] slots
    ) {
        Arrays.fill(slots, 0);
        if (lane == ProjectedLaterDeclarationLane.OPENING_SIDE) {
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                int defenderNationIndex = state.attackerCount + defenderIndex;
                if (state.beigeTurns[defenderNationIndex] > 0 || state.combatStrength(defenderNationIndex) <= 0d) {
                    continue;
                }
                slots[defenderIndex] = Math.max(
                        0,
                        scenario.defender(defenderIndex).rawFreeDef() - activeDefensiveWarsByNation[defenderNationIndex]
                );
            }
            return;
        }
        for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            slots[attackerIndex] = Math.max(
                    0,
                    scenario.attacker(attackerIndex).rawFreeDef() - activeDefensiveWarsByNation[attackerIndex]
            );
        }
    }

    private ProjectedLaterDeclarationInputs buildProjectedLaterDeclarationInputs(
            ProjectedLaterDeclarationLane lane,
            ProjectionState state,
            DenseWarState warState,
            int[] remainingDeclarerSlots,
            int[] remainingTargetSlots,
            SidePlannerSettings plannerSettings,
            int turn
    ) {
        boolean declarersAreScenarioAttackers = lane == ProjectedLaterDeclarationLane.OPENING_SIDE;
        return buildProjectedDeclarationInputs(
                state,
                warState,
                remainingDeclarerSlots,
                remainingTargetSlots,
                declarersAreScenarioAttackers,
                declarersAreScenarioAttackers ? attackerOpeningSettings : defenderOpeningSettings,
                plannerSettings,
                plannerSettings.laterDeclarationScoreThreshold(),
                turn,
                declarersAreScenarioAttackers
        );
    }

    private ProjectedLaterDeclarationInputs buildProjectedDeclarationInputs(
            ProjectionState state,
            DenseWarState warState,
            int[] remainingDeclarerSlots,
            int[] remainingTargetSlots,
            boolean declarersAreScenarioAttackers,
            SideOpeningSettings openingSettings,
            SidePlannerSettings plannerSettings,
            double scoreThreshold,
            int turn,
            boolean applyPairLockoutTiming
    ) {
        ProjectedDeclarationSnapshotState snapshotState = buildProjectedDeclarationSnapshotState(state, warState);
        int declarerSourceCount = declarersAreScenarioAttackers ? scenario.attackerCount() : scenario.defenderCount();
        int targetSourceCount = declarersAreScenarioAttackers ? scenario.defenderCount() : scenario.attackerCount();
        int[] eligibleDeclarerOverallIndexes = new int[declarerSourceCount];
        int[] eligibleDeclarerCaps = new int[declarerSourceCount];
        int eligibleDeclarerCount = 0;
        for (int sourceIndex = 0; sourceIndex < declarerSourceCount; sourceIndex++) {
            if (remainingDeclarerSlots[sourceIndex] <= 0) {
                continue;
            }
            int overallIndex = declarersAreScenarioAttackers ? sourceIndex : state.attackerCount + sourceIndex;
            if (state.beigeTurns[overallIndex] > 0 || state.combatStrength(overallIndex) <= 0d) {
                continue;
            }
            eligibleDeclarerOverallIndexes[eligibleDeclarerCount] = overallIndex;
            eligibleDeclarerCaps[eligibleDeclarerCount] = remainingDeclarerSlots[sourceIndex];
            eligibleDeclarerCount++;
        }
        int[] eligibleTargetOverallIndexes = new int[targetSourceCount];
        int[] eligibleTargetCaps = new int[targetSourceCount];
        int eligibleTargetCount = 0;
        for (int sourceIndex = 0; sourceIndex < targetSourceCount; sourceIndex++) {
            if (remainingTargetSlots[sourceIndex] <= 0) {
                continue;
            }
            int overallIndex = declarersAreScenarioAttackers ? state.attackerCount + sourceIndex : sourceIndex;
            if (state.beigeTurns[overallIndex] > 0 || state.combatStrength(overallIndex) <= 0d) {
                continue;
            }
            eligibleTargetOverallIndexes[eligibleTargetCount] = overallIndex;
            eligibleTargetCaps[eligibleTargetCount] = remainingTargetSlots[sourceIndex];
            eligibleTargetCount++;
        }
        if (eligibleDeclarerCount == 0 || eligibleTargetCount == 0) {
            return null;
        }

        boolean[] retainedDeclarers = new boolean[eligibleDeclarerCount];
        boolean[] retainedTargets = new boolean[eligibleTargetCount];
        int retainedDeclarerCount = 0;
        int retainedTargetCount = 0;
        for (int declarerIndex = 0; declarerIndex < eligibleDeclarerCount; declarerIndex++) {
            int declarerOverallIndex = eligibleDeclarerOverallIndexes[declarerIndex];
            for (int targetIndex = 0; targetIndex < eligibleTargetCount; targetIndex++) {
                int targetOverallIndex = eligibleTargetOverallIndexes[targetIndex];
                if (warState.hasActivePair(declarerOverallIndex, targetOverallIndex)
                        || !canProjectedDeclare(state, declarerOverallIndex, targetOverallIndex)) {
                    continue;
                }
                if (!retainedDeclarers[declarerIndex]) {
                    retainedDeclarers[declarerIndex] = true;
                    retainedDeclarerCount++;
                }
                if (!retainedTargets[targetIndex]) {
                    retainedTargets[targetIndex] = true;
                    retainedTargetCount++;
                }
            }
        }
        if (retainedDeclarerCount == 0 || retainedTargetCount == 0) {
            return null;
        }

        List<DBNationSnapshot> declarerSnapshots = new ArrayList<>(retainedDeclarerCount);
        List<DBNationSnapshot> targetSnapshots = new ArrayList<>(retainedTargetCount);
        int[] declarerOverallIndexes = new int[retainedDeclarerCount];
        int[] targetOverallIndexes = new int[retainedTargetCount];
        int[] declarerCaps = new int[retainedDeclarerCount];
        int[] targetCaps = new int[retainedTargetCount];
        int declarerWriteIndex = 0;
        for (int eligibleIndex = 0; eligibleIndex < eligibleDeclarerCount; eligibleIndex++) {
            if (!retainedDeclarers[eligibleIndex]) {
                continue;
            }
            int overallIndex = eligibleDeclarerOverallIndexes[eligibleIndex];
            declarerOverallIndexes[declarerWriteIndex] = overallIndex;
            declarerCaps[declarerWriteIndex] = eligibleDeclarerCaps[eligibleIndex];
            declarerSnapshots.add(projectedSnapshot(state, overallIndex, snapshotState));
            declarerWriteIndex++;
        }
        int targetWriteIndex = 0;
        for (int eligibleIndex = 0; eligibleIndex < eligibleTargetCount; eligibleIndex++) {
            if (!retainedTargets[eligibleIndex]) {
                continue;
            }
            int overallIndex = eligibleTargetOverallIndexes[eligibleIndex];
            targetOverallIndexes[targetWriteIndex] = overallIndex;
            targetCaps[targetWriteIndex] = eligibleTargetCaps[eligibleIndex];
            targetSnapshots.add(projectedSnapshot(state, overallIndex, snapshotState));
            targetWriteIndex++;
        }

        OpeningEvaluationScenario projectedOpeningScenario = ProjectedOpeningEvaluationScenario.create(
            declarerSnapshots,
            targetSnapshots
        );
        CandidateEdgeTable rawEdges = new CandidateEdgeTable();
        StrategicObjective declarationObjective = projectionObjective == null ? new DamageObjective() : projectionObjective;
        OpeningEvaluator.evaluate(
            projectedOpeningScenario,
                PlannerAutonomousDeclarationPlanner.tuningForPlannerSettings(SimTuning.defaults(), plannerSettings),
                OverrideSet.EMPTY,
                declarationObjective,
                openingSettings == null ? SideOpeningSettings.legacy(declarationObjective) : openingSettings,
            declarerCaps,
            targetCaps,
                rawEdges
        );
        // Legal later declarations must remain visible even when no attack type clears
        // opening admission; lost-control and counter-pressure regimes can still value
        // the declaration itself.

        CandidateEdgeTable projectedEdges = new CandidateEdgeTable();
        Long2IntOpenHashMap rawEdgeByPair = new Long2IntOpenHashMap(Math.max(16, rawEdges.edgeCount() * 2));
        rawEdgeByPair.defaultReturnValue(-1);
        for (int edgeIndex = 0; edgeIndex < rawEdges.edgeCount(); edgeIndex++) {
            rawEdgeByPair.put(
                    projectedIndexPairKey(rawEdges.attackerIndex(edgeIndex), rawEdges.defenderIndex(edgeIndex)),
                    edgeIndex
            );
        }
        double[] deferredBestByDeclarer = new double[declarerSnapshots.size()];
        int horizonRemainingTurns = Math.max(0, horizonTurns - turn);
        if (applyPairLockoutTiming) {
            for (int declarerCompiledIndex = 0; declarerCompiledIndex < declarerSnapshots.size(); declarerCompiledIndex++) {
                for (int targetCompiledIndex = 0; targetCompiledIndex < targetSnapshots.size(); targetCompiledIndex++) {
                    int declarerOverallIndex = declarerOverallIndexes[declarerCompiledIndex];
                    int targetOverallIndex = targetOverallIndexes[targetCompiledIndex];
                    profiledRedeclareCandidateEvaluations++;
                    if (warState.hasActivePair(declarerOverallIndex, targetOverallIndex)) {
                        continue;
                    }
                    int blockedTurns = projectedDeclarationBlockedTurns(state, warState, declarerOverallIndex, targetOverallIndex, turn);
                    if (blockedTurns <= 0) {
                        continue;
                    }
                    double deferredScore = projectedDeclarationScore(
                            state,
                            warState,
                            rawEdges,
                            rawEdgeByPair.get(projectedIndexPairKey(declarerCompiledIndex, targetCompiledIndex)),
                            declarerOverallIndex,
                            targetOverallIndex,
                            remainingDeclarerSlots[declarersAreScenarioAttackers
                                    ? declarerOverallIndex
                                    : declarerOverallIndex - state.attackerCount],
                            remainingTargetSlots[declarersAreScenarioAttackers
                                    ? targetOverallIndex - state.attackerCount
                                    : targetOverallIndex],
                            activityWeightForOverallIndex(declarerOverallIndex)
                    )
                            * StrategicTimingValue.redeclareWaitDiscount(blockedTurns, horizonRemainingTurns);
                    deferredBestByDeclarer[declarerCompiledIndex] = Math.max(
                            deferredBestByDeclarer[declarerCompiledIndex],
                            deferredScore
                    );
                }
            }
        }

        for (int declarerCompiledIndex = 0; declarerCompiledIndex < declarerSnapshots.size(); declarerCompiledIndex++) {
            for (int targetCompiledIndex = 0; targetCompiledIndex < targetSnapshots.size(); targetCompiledIndex++) {
                int declarerOverallIndex = declarerOverallIndexes[declarerCompiledIndex];
                int targetOverallIndex = targetOverallIndexes[targetCompiledIndex];
                if (applyPairLockoutTiming) {
                    profiledRedeclareCandidateEvaluations++;
                } else {
                    profiledCounterCandidateEvaluations++;
                }
                if (warState.hasActivePair(declarerOverallIndex, targetOverallIndex)
                    || !canProjectedDeclare(state, declarerOverallIndex, targetOverallIndex)) {
                    continue;
                }
                int blockedTurns = applyPairLockoutTiming
                    ? projectedDeclarationBlockedTurns(state, warState, declarerOverallIndex, targetOverallIndex, turn)
                        : 0;
                int declarerSourceIndex = declarersAreScenarioAttackers
                        ? declarerOverallIndex
                        : declarerOverallIndex - state.attackerCount;
                int targetSourceIndex = declarersAreScenarioAttackers
                        ? targetOverallIndex - state.attackerCount
                        : targetOverallIndex;
                int rawEdgeIndex = rawEdgeByPair.get(projectedIndexPairKey(declarerCompiledIndex, targetCompiledIndex));
                double score = projectedDeclarationScore(
                        state,
                        warState,
                        rawEdges,
                        rawEdgeIndex,
                        declarerOverallIndex,
                        targetOverallIndex,
                        remainingDeclarerSlots[declarerSourceIndex],
                        remainingTargetSlots[targetSourceIndex],
                        activityWeightForOverallIndex(declarerOverallIndex)
                );
                if (blockedTurns > 0
                        || score <= scoreThreshold
                        || score <= deferredBestByDeclarer[declarerCompiledIndex]) {
                    continue;
                }
                projectedEdges.add(
                        declarerCompiledIndex,
                        targetCompiledIndex,
                        rawEdgeIndex >= 0 ? rawEdges.preferredWarTypeId(rawEdgeIndex) : (byte) WarType.ORD.ordinal(),
                        rawEdgeIndex >= 0 ? rawEdges.bestAttackTypeId(rawEdgeIndex) : (byte) 0,
                        (float) score,
                        rawEdgeIndex >= 0 ? rawEdges.counterRisk(rawEdgeIndex) : 0f
                );
            }
        }
        if (projectedEdges.edgeCount() == 0) {
            return null;
        }
        CompiledScenario projectedScenario = CompiledScenario.scorerOnlyPlannerView(
            declarerSnapshots,
            targetSnapshots,
            declarerCaps,
            targetCaps
        );
        return projectedLaterDeclarationInputs(projectedScenario, projectedEdges, declarerCaps, targetCaps, declarerOverallIndexes, targetOverallIndexes);
    }

    private double projectedDeclarationScore(
            ProjectionState state,
            DenseWarState warState,
            CandidateEdgeTable rawEdges,
            int rawEdgeIndex,
            int declarerOverallIndex,
            int targetOverallIndex,
            int remainingDeclarerSlots,
            int remainingTargetSlots,
            double activityWeight
    ) {
        double openingScore = rawEdgeIndex >= 0 ? Math.max(0d, rawEdges.scalarScore(rawEdgeIndex)) : 0d;
        double declarerStrength = state.combatStrength(declarerOverallIndex);
        double targetStrength = state.combatStrength(targetOverallIndex);
        if (!(declarerStrength > 0d) || !(targetStrength > 0d)) {
            return openingScore;
        }
        double activity = Math.max(0d, Math.min(1d, activityWeight));
        double strengthRatio = declarerStrength / Math.max(1d, targetStrength);
        double targetValue = Math.max(
                MIN_PROJECTED_DECLARATION_TARGET_VALUE,
                state.marginalActionSpaceValue(targetOverallIndex, warState)
                        * PROJECTED_DECLARATION_TARGET_VALUE_MULTIPLIER
        );
        double declarationScore = activity
                * targetValue
                * Math.min(MAX_PROJECTED_DECLARATION_STRENGTH_RATIO, strengthRatio)
                / Math.max(1, Math.min(Math.max(1, remainingDeclarerSlots), Math.max(1, remainingTargetSlots)));
        return Math.max(openingScore, declarationScore);
    }

    private double activityWeightForOverallIndex(int nationIndex) {
        return nationIndex < scenario.attackerCount()
                ? scenario.attackerActivityWeight(nationIndex)
                : scenario.defenderActivityWeight(nationIndex - scenario.attackerCount());
    }


    private int applyProjectedLaterDeclarationPlan(
            DenseWarState warState,
            ProjectedLaterDeclarationInputs inputs,
            SidePlannerSettings plannerSettings,
            int turn,
            int[] counterIncidenceOut
    ) {
        PlannerAutonomousDeclarationPlanner.Plan plan = PlannerAutonomousDeclarationPlanner.planScorerOnly(
                inputs.scenario(),
                inputs.edges(),
            inputs.declarerCaps(),
            inputs.targetCaps(),
            inputs.declarerNationIds(),
            inputs.targetNationIds(),
                plannerSettings,
                Math.max(1, horizonTurns - turn)
        );
        int declarations = 0;
        for (Map.Entry<Integer, List<Integer>> entry : plan.assignment().entrySet()) {
            int declarerNationIndex = inputs.declarerOverallIndexesByNationId().get(entry.getKey());
            if (declarerNationIndex < 0) {
                continue;
            }
            for (int targetNationId : entry.getValue()) {
                int targetNationIndex = inputs.targetOverallIndexesByNationId().get(targetNationId);
                if (targetNationIndex < 0) {
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
            int maxDeclarations,
            boolean countThrottledDeclarations
    ) {
        PlannerAutonomousDeclarationPlanner.Plan plan = PlannerAutonomousDeclarationPlanner.planScorerOnly(
            inputs.scenario(),
            inputs.edges(),
            inputs.declarerCaps(),
            inputs.targetCaps(),
            inputs.declarerNationIds(),
            inputs.targetNationIds(),
            plannerSettings,
            Math.max(1, horizonTurns - turn)
        );
        int declarationLimit = Math.max(0, maxDeclarations);
        boolean useBoundedTopK = declarationLimit > 0 && declarationLimit <= PROJECTED_DECLARATION_TOP_K_MAX_LIMIT;
        PriorityQueue<ProjectedAssignedDeclarationCandidate> topDeclarations = useBoundedTopK
                ? new PriorityQueue<>(declarationLimit, (left, right) -> {
                    int scoreCompare = Float.compare(left.scalarScore(), right.scalarScore());
                    if (scoreCompare != 0) {
                        return scoreCompare;
                    }
                    return Integer.compare(right.selectionOrder(), left.selectionOrder());
                })
                : null;
        List<ProjectedAssignedDeclarationCandidate> selectedDeclarations = useBoundedTopK ? null : new ArrayList<>();
        int selectedDeclarationCount = 0;
        int selectionOrder = 0;
        for (Map.Entry<Integer, List<Integer>> entry : plan.assignment().entrySet()) {
            int declarerNationIndex = inputs.declarerOverallIndexesByNationId().get(entry.getKey());
            if (declarerNationIndex < 0) {
                continue;
            }
            for (int targetNationId : entry.getValue()) {
                int targetNationIndex = inputs.targetOverallIndexesByNationId().get(targetNationId);
                if (targetNationIndex < 0) {
                    continue;
                }
                int edgeIndex = inputs.edgeIndexByPair().get(projectedPairKey(entry.getKey(), targetNationId));
                if (edgeIndex < 0) {
                    continue;
                }
                selectedDeclarationCount++;
                ProjectedAssignedDeclarationCandidate candidate = new ProjectedAssignedDeclarationCandidate(
                        entry.getKey(),
                        targetNationId,
                        declarerNationIndex,
                        targetNationIndex,
                        edgeIndex,
                        inputs.edges().scalarScore(edgeIndex),
                        selectionOrder++
                );
                if (!useBoundedTopK) {
                    selectedDeclarations.add(candidate);
                    continue;
                }
                if (topDeclarations.size() < declarationLimit) {
                    topDeclarations.add(candidate);
                    continue;
                }
                ProjectedAssignedDeclarationCandidate worstSelected = topDeclarations.peek();
                if (worstSelected == null) {
                    continue;
                }
                if (compareProjectedAssignedDeclarations(candidate, worstSelected) < 0) {
                    continue;
                }
                topDeclarations.poll();
                topDeclarations.add(candidate);
            }
        }
        int declarations = 0;
        if (countThrottledDeclarations && selectedDeclarationCount > declarationLimit) {
            profiledCounterDeclarationsThrottled += selectedDeclarationCount - declarationLimit;
        }
        if (declarationLimit == 0) {
            return 0;
        }
        if (useBoundedTopK) {
            if (topDeclarations == null || topDeclarations.isEmpty()) {
                return 0;
            }
            selectedDeclarations = new ArrayList<>(topDeclarations);
        } else if (selectedDeclarations == null || selectedDeclarations.isEmpty()) {
            return 0;
        }
        selectedDeclarations.sort(LongHorizonForwardProjection::compareProjectedAssignedDeclarations);
        int appliedDeclarations = Math.min(declarationLimit, selectedDeclarations.size());
        for (int declarationIndex = 0; declarationIndex < appliedDeclarations; declarationIndex++) {
            ProjectedAssignedDeclarationCandidate declaration = selectedDeclarations.get(declarationIndex);
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

    private static int compareProjectedAssignedDeclarations(
            ProjectedAssignedDeclarationCandidate left,
            ProjectedAssignedDeclarationCandidate right
    ) {
        int scoreCompare = Float.compare(right.scalarScore(), left.scalarScore());
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        return Integer.compare(left.selectionOrder(), right.selectionOrder());
    }


    private long projectedPairKey(int declarerNationId, int targetNationId) {
        return ((long) declarerNationId << 32) ^ (targetNationId & 0xffffffffL);
    }

    private long projectedIndexPairKey(int declarerCompiledIndex, int targetCompiledIndex) {
        return ((long) declarerCompiledIndex << 32) ^ (targetCompiledIndex & 0xffffffffL);
    }

    private ProjectedLaterDeclarationInputs projectedLaterDeclarationInputs(
            CompiledScenario projectedScenario,
            CandidateEdgeTable projectedEdges,
            int[] declarerCaps,
            int[] targetCaps,
            int[] declarerOverallIndexes,
            int[] targetOverallIndexes
    ) {
        int[] declarerNationIds = new int[projectedScenario.attackerCount()];
        Int2IntOpenHashMap declarerOverallIndexesByNationId = new Int2IntOpenHashMap(Math.max(16, projectedScenario.attackerCount() * 2));
        declarerOverallIndexesByNationId.defaultReturnValue(-1);
        for (int attackerIndex = 0; attackerIndex < projectedScenario.attackerCount(); attackerIndex++) {
            declarerNationIds[attackerIndex] = projectedScenario.attackerNationId(attackerIndex);
            declarerOverallIndexesByNationId.put(
                declarerNationIds[attackerIndex],
                    declarerOverallIndexes[attackerIndex]
            );
        }
        int[] targetNationIds = new int[projectedScenario.defenderCount()];
        Int2IntOpenHashMap targetOverallIndexesByNationId = new Int2IntOpenHashMap(Math.max(16, projectedScenario.defenderCount() * 2));
        targetOverallIndexesByNationId.defaultReturnValue(-1);
        for (int defenderIndex = 0; defenderIndex < projectedScenario.defenderCount(); defenderIndex++) {
            targetNationIds[defenderIndex] = projectedScenario.defenderNationId(defenderIndex);
            targetOverallIndexesByNationId.put(
                targetNationIds[defenderIndex],
                    targetOverallIndexes[defenderIndex]
            );
        }
        Long2IntOpenHashMap edgeIndexByPair = new Long2IntOpenHashMap(Math.max(16, projectedEdges.edgeCount() * 2));
        edgeIndexByPair.defaultReturnValue(-1);
        for (int edgeIndex = 0; edgeIndex < projectedEdges.edgeCount(); edgeIndex++) {
            edgeIndexByPair.put(
                    projectedPairKey(
                    declarerNationIds[projectedEdges.attackerIndex(edgeIndex)],
                    targetNationIds[projectedEdges.defenderIndex(edgeIndex)]
                    ),
                    edgeIndex
            );
        }
        return new ProjectedLaterDeclarationInputs(
                projectedScenario,
                projectedEdges,
            declarerCaps,
            targetCaps,
            declarerNationIds,
            targetNationIds,
                declarerOverallIndexesByNationId,
                targetOverallIndexesByNationId,
                edgeIndexByPair
        );
    }

    private ProjectedDeclarationSnapshotState buildProjectedDeclarationSnapshotState(
            ProjectionState state,
            DenseWarState warState
    ) {
        int nationCount = state.attackerCount + state.defenderCount;
        int[] seededOffensiveWars = scratchProjectedDeclarationSeededOffWars;
        int[] projectedOffensiveWars = scratchProjectedDeclarationProjectedOffWars;
        int[] seededDefensiveWars = scratchProjectedDeclarationSeededDefWars;
        int[] projectedDefensiveWars = scratchProjectedDeclarationProjectedDefWars;
        IntOpenHashSet[] activeOpponentsByNation = scratchProjectedDeclarationActiveOpponentsByNation;
        Arrays.fill(seededOffensiveWars, 0, nationCount, 0);
        Arrays.fill(projectedOffensiveWars, 0, nationCount, 0);
        Arrays.fill(seededDefensiveWars, 0, nationCount, 0);
        Arrays.fill(projectedDefensiveWars, 0, nationCount, 0);
        for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
            DBNationSnapshot baselineSnapshot = nationIndex < scenario.attackerCount()
                    ? scenario.attacker(nationIndex)
                    : scenario.defender(nationIndex - scenario.attackerCount());
            IntOpenHashSet activeOpponents = activeOpponentsByNation[nationIndex];
            if (activeOpponents == null) {
                activeOpponents = new IntOpenHashSet(Math.max(4, baselineSnapshot.activeOpponentNationIds().size() * 2));
                activeOpponentsByNation[nationIndex] = activeOpponents;
            } else {
                activeOpponents.clear();
            }
            activeOpponents.addAll(baselineSnapshot.activeOpponentNationIds());
        }
        for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
            if (!warState.active[warIndex]) {
                continue;
            }
            int attackerNationIndex = warState.attackerNationIndex[warIndex];
            int defenderNationIndex = warState.defenderNationIndex[warIndex];
            if (warState.seededCurrentWar[warIndex]) {
                seededOffensiveWars[attackerNationIndex]++;
                seededDefensiveWars[defenderNationIndex]++;
            } else {
                projectedOffensiveWars[attackerNationIndex]++;
                projectedDefensiveWars[defenderNationIndex]++;
            }
            activeOpponentsByNation[attackerNationIndex].add(nationIdForIndex(defenderNationIndex));
            activeOpponentsByNation[defenderNationIndex].add(nationIdForIndex(attackerNationIndex));
        }
        return new ProjectedDeclarationSnapshotState(
                seededOffensiveWars,
                projectedOffensiveWars,
                seededDefensiveWars,
                projectedDefensiveWars,
                activeOpponentsByNation
        );
    }

    private DBNationSnapshot projectedSnapshot(
            ProjectionState state,
            int nationIndex,
            ProjectedDeclarationSnapshotState snapshotState
    ) {
        DBNationSnapshot baselineSnapshot = nationIndex < scenario.attackerCount()
                ? scenario.attacker(nationIndex)
                : scenario.defender(nationIndex - scenario.attackerCount());
        int currentOffensiveWars = snapshotState.effectiveOffensiveWars(nationIndex, baselineSnapshot.currentOffensiveWars());
        int currentDefensiveWars = snapshotState.effectiveDefensiveWars(nationIndex, baselineSnapshot.currentDefensiveWars());
        int unitBase = state.unitBaseOffsets[nationIndex];
        Set<Integer> activeOpponentNationIds = snapshotState.activeOpponentNationIds(nationIndex);
        int beigeTurns = Math.max(0, state.beigeTurns[nationIndex]);
        if (projectedSnapshotMatchesBaseline(
                state,
                nationIndex,
                baselineSnapshot,
                currentOffensiveWars,
                currentDefensiveWars,
                activeOpponentNationIds,
                beigeTurns,
                unitBase
        )) {
            return baselineSnapshot;
        }
        double[] cityInfra = projectedCityInfra(state, nationIndex, baselineSnapshot);
        double[] resources = projectedResources(state, nationIndex, baselineSnapshot);
        return DBNationSnapshot.projectedFrom(
            baselineSnapshot,
            currentOffensiveWars,
            currentDefensiveWars,
            activeOpponentNationIds,
            beigeTurns,
            cityInfra,
            resources,
            state.unitsFlat,
            unitBase,
            state.unitsBoughtTodayFlat,
            state.pendingBuysFlat
        );
    }

    private boolean projectedSnapshotMatchesBaseline(
            ProjectionState state,
            int nationIndex,
            DBNationSnapshot baselineSnapshot,
            int currentOffensiveWars,
            int currentDefensiveWars,
            Set<Integer> activeOpponentNationIds,
            int beigeTurns,
            int unitBase
    ) {
        return baselineSnapshot.currentOffensiveWars() == currentOffensiveWars
                && baselineSnapshot.currentDefensiveWars() == currentDefensiveWars
                && baselineSnapshot.beigeTurns() == beigeTurns
                && baselineSnapshot.activeOpponentNationIds().equals(activeOpponentNationIds)
                && projectedResourcesMatchBaseline(state, nationIndex, baselineSnapshot)
                && projectedCityInfraMatchesBaseline(state, nationIndex, baselineSnapshot)
                && projectedUnitsMatchBaseline(state, unitBase, baselineSnapshot);
    }

    private double[] projectedResources(
            ProjectionState state,
            int nationIndex,
            DBNationSnapshot baselineSnapshot
    ) {
        if (projectedResourcesMatchBaseline(state, nationIndex, baselineSnapshot)) {
            return baselineSnapshot.resourcesRaw();
        }
        double[] resources = new double[ResourceType.values.length];
        System.arraycopy(state.resourcesFlat, state.resourceBaseOffsets[nationIndex], resources, 0, resources.length);
        return resources;
    }

    private boolean projectedResourcesMatchBaseline(
            ProjectionState state,
            int nationIndex,
            DBNationSnapshot baselineSnapshot
    ) {
        int resourceBase = state.resourceBaseOffsets[nationIndex];
        for (ResourceType type : ResourceType.values) {
            if (Double.compare(state.resourcesFlat[resourceBase + type.ordinal()], baselineSnapshot.resource(type)) != 0) {
                return false;
            }
        }
        return true;
    }

    private double[] projectedCityInfra(
            ProjectionState state,
            int nationIndex,
            DBNationSnapshot baselineSnapshot
    ) {
        if (projectedCityInfraMatchesBaseline(state, nationIndex, baselineSnapshot)) {
            return baselineSnapshot.cityInfraRaw();
        }
        int cityCount = state.cityCounts[nationIndex];
        double[] cityInfra = new double[cityCount];
        System.arraycopy(state.cityInfraFlat, state.cityInfraBaseOffsets[nationIndex], cityInfra, 0, cityCount);
        return cityInfra;
    }

    private boolean projectedCityInfraMatchesBaseline(
            ProjectionState state,
            int nationIndex,
            DBNationSnapshot baselineSnapshot
    ) {
        double[] baselineCityInfra = baselineSnapshot.cityInfraRaw();
        int cityCount = state.cityCounts[nationIndex];
        if (baselineCityInfra.length != cityCount) {
            return false;
        }
        int cityBase = state.cityInfraBaseOffsets[nationIndex];
        for (int cityIndex = 0; cityIndex < cityCount; cityIndex++) {
            if (Double.compare(state.cityInfraFlat[cityBase + cityIndex], baselineCityInfra[cityIndex]) != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean projectedUnitsMatchBaseline(
            ProjectionState state,
            int unitBase,
            DBNationSnapshot baselineSnapshot
    ) {
        for (MilitaryUnit unit : MilitaryUnit.values) {
            int ordinal = unit.ordinal();
            if (baselineSnapshot.unit(unit) != Math.max(0, state.unitsFlat[unitBase + ordinal])) {
                return false;
            }
            if (baselineSnapshot.unitsBoughtToday(unit) != Math.max(0, state.unitsBoughtTodayFlat[unitBase + ordinal])) {
                return false;
            }
            if (baselineSnapshot.pendingBuysNextTurn(unit) != Math.max(0, state.pendingBuysFlat[unitBase + ordinal])) {
                return false;
            }
        }
        return true;
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

    private static int projectedDeclarationBlockedTurns(
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

    private static boolean canProjectedDeclare(ProjectionState state, int declarerNationIndex, int targetNationIndex) {
        if (state.nationIds[declarerNationIndex] == state.nationIds[targetNationIndex]) {
            return false;
        }
        double declarerScore = state.score(declarerNationIndex);
        double targetScore = state.score(targetNationIndex);
        double minScore = declarerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = declarerScore * PW.WAR_RANGE_MAX_MODIFIER;
        return targetScore >= minScore && targetScore <= maxScore;
    }

    private void advanceTurn(ProjectionState state, DenseWarState warState, int turn, boolean[] activeWarsByNation) {
        boolean newDay = turn % DAY_TURNS == 0;
        if (newDay) {
            state.resetUnitBuysToday();
        }
        state.decrementBeigeTurns();
        for (int edgeIndex = warState.firstActiveWar(); edgeIndex >= 0; ) {
            int nextEdgeIndex = warState.nextActiveWar(edgeIndex);
            if (turn - warState.startTurn[edgeIndex] >= WAR_EXPIRATION_TURN) {
                warState.deactivateWar(edgeIndex, turn);
                edgeIndex = nextEdgeIndex;
                continue;
            }
            warState.attackerMaps[edgeIndex] = Math.min(MAP_CAP, warState.attackerMaps[edgeIndex] + 1);
            warState.defenderMaps[edgeIndex] = Math.min(MAP_CAP, warState.defenderMaps[edgeIndex] + 1);
            edgeIndex = nextEdgeIndex;
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
            if (projectionPoliciesForAttacker(warState.attackerNationIndex[context.warIndex()], state.attackerCount).attackChoicePolicy()
                    == HeuristicAttackChoicePolicy.INSTANCE) {
                applyResolvedAttack(state, warState, context, heuristicAttackSelectionResult);
            } else {
                resolveAttack(state, warState, context, attackType, scratch, result);
            }
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
        StrategicAssetValue.ActiveWarContext attackerActiveWarContext = state.activeWarContext(attackerNationIndex, warState);
        StrategicAssetValue.ActiveWarContext defenderActiveWarContext = state.activeWarContext(defenderNationIndex, warState);
        StrategicCapabilityVector attackerBaselineCapability = state.capabilityVector(
            attackerNationIndex,
            state.baseHasActiveWars[attackerNationIndex] || attackerActiveWarContext.hasActiveWars()
        );
        StrategicCapabilityVector defenderBaselineCapability = state.capabilityVector(
            defenderNationIndex,
            state.baseHasActiveWars[defenderNationIndex] || defenderActiveWarContext.hasActiveWars()
        );
        StrategicAssetValue.StrategicRelevance attackerRelevance = state.strategicRelevance(attackerNationIndex);
        StrategicAssetValue.StrategicRelevance defenderRelevance = state.strategicRelevance(defenderNationIndex);
        double attackerBaselineMilitaryValue = PlannerStrategicValue.strategicMilitaryValue(attackerBaselineCapability, attackerRelevance);
        double defenderBaselineMilitaryValue = PlannerStrategicValue.strategicMilitaryValue(defenderBaselineCapability, defenderRelevance);
        SideProjectionPolicies projectionPolicies = projectionPoliciesForAttacker(attackerNationIndex, state.attackerCount);
        if (projectionPolicies.attackChoicePolicy() == HeuristicAttackChoicePolicy.INSTANCE) {
            projectionAttackEvaluator.bind(
                state,
                warState,
                context,
                scratch,
                result,
                mapsAvailable,
                attackerNationIndex,
                defenderNationIndex,
                attacker,
                attackerActiveWarContext,
                defenderActiveWarContext,
                attackerRelevance,
                defenderRelevance,
                attackerBaselineCapability,
                defenderBaselineCapability,
                attackerBaselineMilitaryValue,
                defenderBaselineMilitaryValue
            );
            return HeuristicAttackChoicePolicy.INSTANCE.chooseAttackType(
                    ADAPTIVE_ATTACK_TYPES,
                    mapsAvailable,
                    projectionAttackEvaluator,
                    heuristicAttackCandidate,
                    () -> projectionAttackEvaluator.copyResultInto(heuristicAttackSelectionResult)
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
                            defenderActiveWarContext,
                            defenderRelevance,
                            defenderBaselineCapability,
                            defenderBaselineMilitaryValue
                    );
                    double attackerUnitDamage = state.unitLossValue(
                            attackerNationIndex,
                            result.attackerLosses(),
                            attackerActiveWarContext,
                            attackerRelevance,
                            attackerBaselineCapability,
                            attackerBaselineMilitaryValue
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
        private StrategicAssetValue.StrategicRelevance attackerRelevance;
        private StrategicAssetValue.StrategicRelevance defenderRelevance;
        private StrategicCapabilityVector attackerBaselineCapability;
        private StrategicCapabilityVector defenderBaselineCapability;
        private double attackerBaselineMilitaryValue;
        private double defenderBaselineMilitaryValue;

        void bind(
                ProjectionState state,
                DenseWarState warState,
                DenseWarContext context,
                AttackScratch scratch,
                MutableAttackResult result,
                int mapsAvailable,
                int attackerNationIndex,
                int defenderNationIndex,
            CombatKernel.NationState attacker,
            StrategicAssetValue.ActiveWarContext attackerActiveWarContext,
            StrategicAssetValue.ActiveWarContext defenderActiveWarContext,
            StrategicAssetValue.StrategicRelevance attackerRelevance,
            StrategicAssetValue.StrategicRelevance defenderRelevance,
            StrategicCapabilityVector attackerBaselineCapability,
            StrategicCapabilityVector defenderBaselineCapability,
            double attackerBaselineMilitaryValue,
            double defenderBaselineMilitaryValue
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
            this.attackerActiveWarContext = attackerActiveWarContext;
            this.defenderActiveWarContext = defenderActiveWarContext;
            this.attackerRelevance = attackerRelevance;
            this.defenderRelevance = defenderRelevance;
            this.attackerBaselineCapability = attackerBaselineCapability;
            this.defenderBaselineCapability = defenderBaselineCapability;
            this.attackerBaselineMilitaryValue = attackerBaselineMilitaryValue;
            this.defenderBaselineMilitaryValue = defenderBaselineMilitaryValue;
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
                    defenderActiveWarContext,
                    defenderRelevance,
                    defenderBaselineCapability,
                    defenderBaselineMilitaryValue
            );
            double attackerUnitDamage = state.unitLossValue(
                    attackerNationIndex,
                    result.attackerLosses(),
                    attackerActiveWarContext,
                    attackerRelevance,
                    attackerBaselineCapability,
                    attackerBaselineMilitaryValue
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

        void copyResultInto(MutableAttackResult out) {
            out.copyFrom(result);
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
        CombatKernel.resolveInto(context, attackType, ResolutionMode.MOST_LIKELY, scratch, result);
        applyResolvedAttack(state, warState, context, result);
        }

        private void applyResolvedAttack(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            MutableAttackResult result
        ) {
        profiledResolvedAttacks++;
        int edgeIndex = context.warIndex();
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
        state.invalidateScore(warState.attackerNationIndex[edgeIndex]);
        state.invalidateScore(warState.defenderNationIndex[edgeIndex]);
        clearInvalidControls(
            state,
            warState,
            warState.attackerNationIndex[edgeIndex],
            warState.defenderNationIndex[edgeIndex]
        );
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
        state.invalidateScore(winnerIndex);
        state.invalidateScore(loserIndex);
    }

    private static void clearInvalidControls(
            ProjectionState state,
            DenseWarState warState,
            int attackerNationIndex,
            int defenderNationIndex
    ) {
        clearInvalidControlsForNation(state, warState, attackerNationIndex);
        if (defenderNationIndex != attackerNationIndex) {
            clearInvalidControlsForNation(state, warState, defenderNationIndex);
        }
    }

    private static void clearInvalidControlsForNation(ProjectionState state, DenseWarState warState, int nationIndex) {
        boolean canLoseGroundControl = state.unit(nationIndex, MilitaryUnit.SOLDIER) <= 0
                && state.unit(nationIndex, MilitaryUnit.TANK) <= 0;
        boolean canLoseAirControl = state.unit(nationIndex, MilitaryUnit.AIRCRAFT) <= 0;
        boolean canLoseBlockade = state.unit(nationIndex, MilitaryUnit.SHIP) <= 0;
        if (!canLoseGroundControl && !canLoseAirControl && !canLoseBlockade) {
            return;
        }
        for (int warIndex = warState.firstOffensiveWarForNation(nationIndex);
             warIndex >= 0;
             warIndex = warState.nextOffensiveWarForNation(warIndex)) {
            if (!warState.active[warIndex]) {
                continue;
            }
            if (canLoseGroundControl) {
                clearControlIfUnable(state, warState, warIndex, warState.groundSuperiorityOwner, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
            }
            if (canLoseAirControl) {
                clearControlIfUnable(state, warState, warIndex, warState.airSuperiorityOwner, MilitaryUnit.AIRCRAFT, null);
            }
            if (canLoseBlockade) {
                clearControlIfUnable(state, warState, warIndex, warState.blockadeOwner, MilitaryUnit.SHIP, null);
            }
        }
        for (int warIndex = warState.firstDefensiveWarForNation(nationIndex);
             warIndex >= 0;
             warIndex = warState.nextDefensiveWarForNation(warIndex)) {
            if (!warState.active[warIndex]) {
                continue;
            }
            if (canLoseGroundControl) {
                clearControlIfUnable(state, warState, warIndex, warState.groundSuperiorityOwner, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
            }
            if (canLoseAirControl) {
                clearControlIfUnable(state, warState, warIndex, warState.airSuperiorityOwner, MilitaryUnit.AIRCRAFT, null);
            }
            if (canLoseBlockade) {
                clearControlIfUnable(state, warState, warIndex, warState.blockadeOwner, MilitaryUnit.SHIP, null);
            }
        }
    }

    private void resetProjectedEvaluationProfile() {
        profiledProjectionTurns = 0L;
        profiledCounterTurns = 0L;
        profiledCounterTurnsNoSlots = 0L;
        profiledCounterCandidateEvaluations = 0L;
        profiledCounterDeclarations = 0L;
        profiledCounterDeclarationsThrottled = 0L;
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
        PlannerProfiler.addCounter(PROFILED_PREPARED_STATE_PROFILES, profiledPreparedStateProfiles);
        PlannerProfiler.addCounter(PROFILED_PREPARED_STATE_RESTORES, profiledPreparedStateRestores);
        PlannerProfiler.addCounter(PROFILED_PREPARED_WAR_TEMPLATE_BUILDS, profiledPreparedWarTemplateBuilds);
        PlannerProfiler.addCounter(PROFILED_PREPARED_WAR_RESTORES, profiledPreparedWarRestores);
        PlannerProfiler.addCounter(PROFILED_PROJECTION_TURNS, profiledProjectionTurns);
        PlannerProfiler.addCounter(PROFILED_COUNTER_TURNS, profiledCounterTurns);
        PlannerProfiler.addCounter(PROFILED_COUNTER_TURNS_NO_SLOTS, profiledCounterTurnsNoSlots);
        PlannerProfiler.addCounter(PROFILED_COUNTER_CANDIDATE_EVALUATIONS, profiledCounterCandidateEvaluations);
        PlannerProfiler.addCounter(PROFILED_COUNTER_DECLARATIONS, profiledCounterDeclarations);
        PlannerProfiler.addCounter(PROFILED_COUNTER_DECLARATIONS_THROTTLED, profiledCounterDeclarationsThrottled);
        PlannerProfiler.addCounter(PROFILED_REDECLARE_TURNS, profiledRedeclareTurns);
        PlannerProfiler.addCounter(PROFILED_REDECLARE_TURNS_NO_SLOTS, profiledRedeclareTurnsNoSlots);
        PlannerProfiler.addCounter(PROFILED_REDECLARE_CANDIDATE_EVALUATIONS, profiledRedeclareCandidateEvaluations);
        PlannerProfiler.addCounter(PROFILED_REDECLARE_DECLARATIONS, profiledRedeclarations);
        PlannerProfiler.addCounter(PROFILED_WAR_ITERATIONS, profiledWarIterations);
        PlannerProfiler.addCounter(PROFILED_ATTACK_CHOICE_CALLS, profiledAttackChoiceCalls);
        PlannerProfiler.addCounter(PROFILED_ATTACK_TYPE_EVALUATIONS, profiledAttackTypeEvaluations);
        PlannerProfiler.addCounter(PROFILED_RESOLVED_ATTACKS, profiledResolvedAttacks);
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
        private final Int2IntOpenHashMap nationIndexById;
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
        private final boolean[] scoreDirty;
        private final double[] staticScoreComponent;
        private final int[] researchBits;
        private final long[] projectBits;
        private final int[] baseDailyBuyCapsFlat;
        private final double[] infraAttackModifiersFlat;
        private final double[] infraDefendModifiersFlat;
        private final double[] groundLooterModifiers;
        private final double[] nonGroundLooterModifiers;
        private final double[] lootModifiers;
        private final SpecialistCityProfile[] citySpecialistProfilesFlat;
        private final ProjectionNation[] nationViews;
        private final boolean[] resourceBudgetKnown;
        private final boolean[] baseHasActiveWars;
        private final int[] maxInfraCityIndexByNation;
        private final int[] runnerUpInfraCityIndexByNation;
        private int[] initialUnitsFlat;
        private int[] initialUnitsBoughtTodayFlat;
        private int[] initialPendingBuysFlat;
        private double[] initialResourcesFlat;
        private double[] initialCityInfraFlat;
        private double[] initialScores;
        private int[] initialBeigeTurns;
        private int[] initialMaxInfraCityIndexByNation;
        private int[] initialRunnerUpInfraCityIndexByNation;
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
        private final StrategicAssetValue.StrategicRelevance[] strategicRelevanceCache;
        private final long[] strategicRelevanceOwnScoreVersion;
        private final long[] strategicRelevanceOpposingSideScoreVersion;
        private final long[] scoreVersionByNation;
        private final StrategicCapabilityVector[] inactiveWarCapabilityCache;
        private final StrategicCapabilityVector[] activeWarCapabilityCache;
        private final long[] capabilityVersionByNation;
        private final double[] inactiveWarStrategicMilitaryValueCache;
        private final double[] activeWarStrategicMilitaryValueCache;
        private final long[] inactiveWarStrategicMilitaryOwnScoreVersion;
        private final long[] activeWarStrategicMilitaryOwnScoreVersion;
        private final long[] inactiveWarStrategicMilitaryOpposingSideScoreVersion;
        private final long[] activeWarStrategicMilitaryOpposingSideScoreVersion;
        private final long[] inactiveWarStrategicMilitaryCapabilityVersion;
        private final long[] activeWarStrategicMilitaryCapabilityVersion;
        private final double[] groundStrengthCache;
        private final double[] groundStrengthUnderAirCache;
        private final double[] combatStrengthCache;
        private long attackerSideScoreVersion;
        private long defenderSideScoreVersion;
        private final double[] costBuffer = new double[ResourceType.values.length];

        private ProjectionState(
                int attackerCount,
                int defenderCount,
                int[] nationIds,
            Int2IntOpenHashMap nationIndexById,
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
                boolean[] scoreDirty,
                double[] staticScoreComponent,
                int[] researchBits,
                long[] projectBits,
                int[] baseDailyBuyCapsFlat,
                double[] infraAttackModifiersFlat,
                double[] infraDefendModifiersFlat,
                double[] groundLooterModifiers,
                double[] nonGroundLooterModifiers,
                double[] lootModifiers,
                SpecialistCityProfile[] citySpecialistProfilesFlat,
                ProjectionNation[] nationViews,
                boolean[] resourceBudgetKnown,
                boolean[] baseHasActiveWars,
                int[] maxInfraCityIndexByNation,
                int[] runnerUpInfraCityIndexByNation,
                int[] baselineSoldiers,
                int[] baselineTanks,
                int[] baselineAircraft,
                int[] baselineShips,
                double[] baselineScores,
                double[] baselineInfra,
                int[] beigeTurns,
                StrategicAssetValue.StrategicRelevance[] strategicRelevanceCache,
                long[] strategicRelevanceOwnScoreVersion,
                long[] strategicRelevanceOpposingSideScoreVersion,
                long[] scoreVersionByNation,
                StrategicCapabilityVector[] inactiveWarCapabilityCache,
                StrategicCapabilityVector[] activeWarCapabilityCache,
                long[] capabilityVersionByNation,
                double[] inactiveWarStrategicMilitaryValueCache,
                double[] activeWarStrategicMilitaryValueCache,
                long[] inactiveWarStrategicMilitaryOwnScoreVersion,
                long[] activeWarStrategicMilitaryOwnScoreVersion,
                long[] inactiveWarStrategicMilitaryOpposingSideScoreVersion,
                long[] activeWarStrategicMilitaryOpposingSideScoreVersion,
                long[] inactiveWarStrategicMilitaryCapabilityVersion,
                long[] activeWarStrategicMilitaryCapabilityVersion,
                double[] groundStrengthCache,
                double[] groundStrengthUnderAirCache,
                double[] combatStrengthCache
        ) {
            this.attackerCount = attackerCount;
            this.defenderCount = defenderCount;
            this.nationIds = nationIds;
            this.nationIndexById = nationIndexById;
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
            this.scoreDirty = scoreDirty;
            this.staticScoreComponent = staticScoreComponent;
            this.researchBits = researchBits;
            this.projectBits = projectBits;
            this.baseDailyBuyCapsFlat = baseDailyBuyCapsFlat;
            this.infraAttackModifiersFlat = infraAttackModifiersFlat;
            this.infraDefendModifiersFlat = infraDefendModifiersFlat;
            this.groundLooterModifiers = groundLooterModifiers;
            this.nonGroundLooterModifiers = nonGroundLooterModifiers;
            this.lootModifiers = lootModifiers;
            this.citySpecialistProfilesFlat = citySpecialistProfilesFlat;
            this.nationViews = nationViews;
            this.resourceBudgetKnown = resourceBudgetKnown;
            this.baseHasActiveWars = baseHasActiveWars;
            this.maxInfraCityIndexByNation = maxInfraCityIndexByNation;
            this.runnerUpInfraCityIndexByNation = runnerUpInfraCityIndexByNation;
            this.baselineSoldiers = baselineSoldiers;
            this.baselineTanks = baselineTanks;
            this.baselineAircraft = baselineAircraft;
            this.baselineShips = baselineShips;
            this.baselineScores = baselineScores;
            this.baselineInfra = baselineInfra;
            this.beigeTurns = beigeTurns;
            this.strategicRelevanceCache = strategicRelevanceCache;
            this.strategicRelevanceOwnScoreVersion = strategicRelevanceOwnScoreVersion;
            this.strategicRelevanceOpposingSideScoreVersion = strategicRelevanceOpposingSideScoreVersion;
            this.scoreVersionByNation = scoreVersionByNation;
            this.inactiveWarCapabilityCache = inactiveWarCapabilityCache;
            this.activeWarCapabilityCache = activeWarCapabilityCache;
            this.capabilityVersionByNation = capabilityVersionByNation;
            this.inactiveWarStrategicMilitaryValueCache = inactiveWarStrategicMilitaryValueCache;
            this.activeWarStrategicMilitaryValueCache = activeWarStrategicMilitaryValueCache;
            this.inactiveWarStrategicMilitaryOwnScoreVersion = inactiveWarStrategicMilitaryOwnScoreVersion;
            this.activeWarStrategicMilitaryOwnScoreVersion = activeWarStrategicMilitaryOwnScoreVersion;
            this.inactiveWarStrategicMilitaryOpposingSideScoreVersion = inactiveWarStrategicMilitaryOpposingSideScoreVersion;
            this.activeWarStrategicMilitaryOpposingSideScoreVersion = activeWarStrategicMilitaryOpposingSideScoreVersion;
            this.inactiveWarStrategicMilitaryCapabilityVersion = inactiveWarStrategicMilitaryCapabilityVersion;
            this.activeWarStrategicMilitaryCapabilityVersion = activeWarStrategicMilitaryCapabilityVersion;
            this.groundStrengthCache = groundStrengthCache;
            this.groundStrengthUnderAirCache = groundStrengthUnderAirCache;
            this.combatStrengthCache = combatStrengthCache;
        }

        static ProjectionState from(CompiledScenario scenario) {
            int attackerCount = scenario.attackerCount();
            int defenderCount = scenario.defenderCount();
            int nationCount = attackerCount + defenderCount;
            int unitStride = MilitaryUnit.values.length;
            int resourceStride = ResourceType.values.length;
            int attackStride = AttackType.values.length;
            int[] nationIds = new int[nationCount];
            Int2IntOpenHashMap nationIndexById = new Int2IntOpenHashMap(nationCount);
            nationIndexById.defaultReturnValue(-1);
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
            boolean[] scoreDirty = new boolean[nationCount];
            double[] staticScoreComponent = new double[nationCount];
            int[] researchBits = new int[nationCount];
            long[] projectBits = new long[nationCount];
            int[] baseDailyBuyCapsFlat = new int[nationCount * unitStride];
            double[] infraAttackModifiersFlat = new double[nationCount * attackStride];
            double[] infraDefendModifiersFlat = new double[nationCount * attackStride];
            double[] groundLooterModifiers = new double[nationCount];
            double[] nonGroundLooterModifiers = new double[nationCount];
            double[] lootModifiers = new double[nationCount];
            SpecialistCityProfile[] citySpecialistProfilesFlat = new SpecialistCityProfile[totalCities];
            ProjectionNation[] nationViews = new ProjectionNation[nationCount];
            boolean[] resourceBudgetKnown = new boolean[nationCount];
            boolean[] baseHasActiveWars = new boolean[nationCount];
            int[] maxInfraCityIndexByNation = new int[nationCount];
            int[] runnerUpInfraCityIndexByNation = new int[nationCount];
            int[] baselineSoldiers = new int[nationCount];
            int[] baselineTanks = new int[nationCount];
            int[] baselineAircraft = new int[nationCount];
            int[] baselineShips = new int[nationCount];
            double[] baselineScores = new double[nationCount];
            double[] baselineInfra = new double[nationCount];
            int[] beigeTurns = new int[nationCount];
            StrategicAssetValue.StrategicRelevance[] strategicRelevanceCache = new StrategicAssetValue.StrategicRelevance[nationCount];
            long[] strategicRelevanceOwnScoreVersion = new long[nationCount];
            long[] strategicRelevanceOpposingSideScoreVersion = new long[nationCount];
            long[] scoreVersionByNation = new long[nationCount];
            StrategicCapabilityVector[] inactiveWarCapabilityCache = new StrategicCapabilityVector[nationCount];
            StrategicCapabilityVector[] activeWarCapabilityCache = new StrategicCapabilityVector[nationCount];
            long[] capabilityVersionByNation = new long[nationCount];
            double[] inactiveWarStrategicMilitaryValueCache = new double[nationCount];
            double[] activeWarStrategicMilitaryValueCache = new double[nationCount];
            long[] inactiveWarStrategicMilitaryOwnScoreVersion = new long[nationCount];
            long[] activeWarStrategicMilitaryOwnScoreVersion = new long[nationCount];
            long[] inactiveWarStrategicMilitaryOpposingSideScoreVersion = new long[nationCount];
            long[] activeWarStrategicMilitaryOpposingSideScoreVersion = new long[nationCount];
            long[] inactiveWarStrategicMilitaryCapabilityVersion = new long[nationCount];
            long[] activeWarStrategicMilitaryCapabilityVersion = new long[nationCount];
            double[] groundStrengthCache = new double[nationCount];
            double[] groundStrengthUnderAirCache = new double[nationCount];
            double[] combatStrengthCache = new double[nationCount];
            Arrays.fill(strategicRelevanceOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(strategicRelevanceOpposingSideScoreVersion, Long.MIN_VALUE);
            Arrays.fill(inactiveWarStrategicMilitaryValueCache, Double.NaN);
            Arrays.fill(activeWarStrategicMilitaryValueCache, Double.NaN);
            Arrays.fill(inactiveWarStrategicMilitaryOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(inactiveWarStrategicMilitaryOpposingSideScoreVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryOpposingSideScoreVersion, Long.MIN_VALUE);
            Arrays.fill(inactiveWarStrategicMilitaryCapabilityVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryCapabilityVersion, Long.MIN_VALUE);
            Arrays.fill(groundStrengthCache, Double.NaN);
            Arrays.fill(groundStrengthUnderAirCache, Double.NaN);
            Arrays.fill(combatStrengthCache, Double.NaN);
            Arrays.fill(maxInfraCityIndexByNation, -1);
            Arrays.fill(runnerUpInfraCityIndexByNation, -1);

            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                DBNationSnapshot snapshot = snapshotAt(scenario, attackerCount, nationIndex);
                nationIds[nationIndex] = snapshot.nationId();
                nationIndexById.put(snapshot.nationId(), nationIndex);
                teamIds[nationIndex] = snapshot.teamId();
                staticScoreComponent[nationIndex] = snapshot.staticScoreComponent();
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
                    baseDailyBuyCapsFlat[unitIndex] = UnitEconomy.maxBuyPerDayFor(
                            cityCounts[nationIndex],
                            unit,
                            projectBits[nationIndex],
                            researchBits[nationIndex]
                    );
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
                    nationIndexById,
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
                    scoreDirty,
                    staticScoreComponent,
                    researchBits,
                    projectBits,
                    baseDailyBuyCapsFlat,
                    infraAttackModifiersFlat,
                    infraDefendModifiersFlat,
                    groundLooterModifiers,
                    nonGroundLooterModifiers,
                    lootModifiers,
                    citySpecialistProfilesFlat,
                    nationViews,
                    resourceBudgetKnown,
                    baseHasActiveWars,
                    maxInfraCityIndexByNation,
                    runnerUpInfraCityIndexByNation,
                    baselineSoldiers,
                    baselineTanks,
                    baselineAircraft,
                    baselineShips,
                    baselineScores,
                    baselineInfra,
                        beigeTurns,
                        strategicRelevanceCache,
                        strategicRelevanceOwnScoreVersion,
                        strategicRelevanceOpposingSideScoreVersion,
                            scoreVersionByNation,
                            inactiveWarCapabilityCache,
                            activeWarCapabilityCache,
                            capabilityVersionByNation,
                            inactiveWarStrategicMilitaryValueCache,
                            activeWarStrategicMilitaryValueCache,
                            inactiveWarStrategicMilitaryOwnScoreVersion,
                            activeWarStrategicMilitaryOwnScoreVersion,
                            inactiveWarStrategicMilitaryOpposingSideScoreVersion,
                            activeWarStrategicMilitaryOpposingSideScoreVersion,
                            inactiveWarStrategicMilitaryCapabilityVersion,
                                activeWarStrategicMilitaryCapabilityVersion,
                                groundStrengthCache,
                                groundStrengthUnderAirCache,
                                combatStrengthCache
            );
            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                nationViews[nationIndex] = new ProjectionNation(state, nationIndex);
                state.refreshInfraLeaders(nationIndex);
                baselineInfra[nationIndex] = state.totalInfra(nationIndex);
                state.recalculateScore(nationIndex);
                baselineScores[nationIndex] = state.score(nationIndex);
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
            return nationIndexById.get(nationId);
        }

        private void captureInitialMutableState() {
            initialUnitsFlat = unitsFlat.clone();
            initialUnitsBoughtTodayFlat = unitsBoughtTodayFlat.clone();
            initialPendingBuysFlat = pendingBuysFlat.clone();
            initialResourcesFlat = resourcesFlat.clone();
            initialCityInfraFlat = cityInfraFlat.clone();
            initialScores = scores.clone();
            initialBeigeTurns = beigeTurns.clone();
            initialMaxInfraCityIndexByNation = maxInfraCityIndexByNation.clone();
            initialRunnerUpInfraCityIndexByNation = runnerUpInfraCityIndexByNation.clone();
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
                    scoreDirty.clone(),
                    beigeTurns.clone(),
                    maxInfraCityIndexByNation.clone(),
                    runnerUpInfraCityIndexByNation.clone()
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
            Arrays.fill(scoreDirty, false);
            Arrays.fill(scoreVersionByNation, 0L);
            attackerSideScoreVersion = 0L;
            defenderSideScoreVersion = 0L;
            clearStrategicRelevanceCache();
            clearCapabilityCache();
            System.arraycopy(initialBeigeTurns, 0, beigeTurns, 0, beigeTurns.length);
            System.arraycopy(initialMaxInfraCityIndexByNation, 0, maxInfraCityIndexByNation, 0, maxInfraCityIndexByNation.length);
            System.arraycopy(initialRunnerUpInfraCityIndexByNation, 0, runnerUpInfraCityIndexByNation, 0, runnerUpInfraCityIndexByNation.length);
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
            System.arraycopy(checkpoint.scoreDirty(), 0, scoreDirty, 0, scoreDirty.length);
            Arrays.fill(scoreVersionByNation, 0L);
            attackerSideScoreVersion = 0L;
            defenderSideScoreVersion = 0L;
            clearStrategicRelevanceCache();
            clearCapabilityCache();
            System.arraycopy(checkpoint.beigeTurns(), 0, beigeTurns, 0, beigeTurns.length);
            System.arraycopy(checkpoint.maxInfraCityIndexByNation(), 0, maxInfraCityIndexByNation, 0, maxInfraCityIndexByNation.length);
            System.arraycopy(checkpoint.runnerUpInfraCityIndexByNation(), 0, runnerUpInfraCityIndexByNation, 0, runnerUpInfraCityIndexByNation.length);
        }

        private void clearStrategicRelevanceCache() {
            Arrays.fill(strategicRelevanceCache, null);
            Arrays.fill(strategicRelevanceOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(strategicRelevanceOpposingSideScoreVersion, Long.MIN_VALUE);
        }

        private void clearCapabilityCache() {
            Arrays.fill(inactiveWarCapabilityCache, null);
            Arrays.fill(activeWarCapabilityCache, null);
            Arrays.fill(inactiveWarStrategicMilitaryValueCache, Double.NaN);
            Arrays.fill(activeWarStrategicMilitaryValueCache, Double.NaN);
            Arrays.fill(inactiveWarStrategicMilitaryOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryOwnScoreVersion, Long.MIN_VALUE);
            Arrays.fill(inactiveWarStrategicMilitaryOpposingSideScoreVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryOpposingSideScoreVersion, Long.MIN_VALUE);
            Arrays.fill(inactiveWarStrategicMilitaryCapabilityVersion, Long.MIN_VALUE);
            Arrays.fill(activeWarStrategicMilitaryCapabilityVersion, Long.MIN_VALUE);
            Arrays.fill(groundStrengthCache, Double.NaN);
            Arrays.fill(groundStrengthUnderAirCache, Double.NaN);
            Arrays.fill(combatStrengthCache, Double.NaN);
        }

        private void invalidateCapability(int nationIndex) {
            capabilityVersionByNation[nationIndex]++;
            inactiveWarCapabilityCache[nationIndex] = null;
            activeWarCapabilityCache[nationIndex] = null;
            inactiveWarStrategicMilitaryValueCache[nationIndex] = Double.NaN;
            activeWarStrategicMilitaryValueCache[nationIndex] = Double.NaN;
            groundStrengthCache[nationIndex] = Double.NaN;
            groundStrengthUnderAirCache[nationIndex] = Double.NaN;
            combatStrengthCache[nationIndex] = Double.NaN;
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
                    invalidateCapability(nationIndex);
                    invalidateScore(nationIndex);
                }
            }
        }

        void resetUnitBuysToday() {
            for (int nationIndex = 0; nationIndex < nationIds.length; nationIndex++) {
                Arrays.fill(unitsBoughtTodayFlat, unitBaseOffsets[nationIndex], unitBaseOffsets[nationIndex] + MilitaryUnit.values.length, 0);
                invalidateCapability(nationIndex);
            }
        }

        void decrementBeigeTurns() {
            for (int nationIndex = 0; nationIndex < beigeTurns.length; nationIndex++) {
                if (beigeTurns[nationIndex] > 0) {
                    beigeTurns[nationIndex]--;
                    invalidateCapability(nationIndex);
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
                    invalidateCapability(nationIndex);
                    invalidateScore(nationIndex);
                }
            }
        }

        private int dailyBuyCap(int nationIndex, MilitaryUnit unit, boolean hasActiveWars) {
            return UnitEconomy.applyBeigeDailyBuyBonus(
                baseDailyBuyCapsFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                unit,
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
                invalidateCapability(nationIndex);
                invalidateScore(nationIndex);
            }
        }

        void applyInfraDamage(int nationIndex, double amount) {
            if (!(amount > 0d) || cityCounts[nationIndex] == 0) {
                return;
            }
            int cityBase = cityInfraBaseOffsets[nationIndex];
            int maxCityIndex = maxInfraCityIndex(nationIndex);
            if (maxCityIndex < 0) {
                return;
            }
            int globalCityIndex = cityBase + maxCityIndex;
            double current = cityInfraFlat[globalCityIndex];
            if (current <= 0d) {
                return;
            }
            double removed = Math.min(current, amount);
            cityInfraFlat[globalCityIndex] = current - removed;
            refreshInfraLeadersAfterDamage(nationIndex, maxCityIndex);
            invalidateScore(nationIndex);
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
            refreshInfraLeaders(nationIndex);
            invalidateScore(nationIndex);
        }

        private int maxInfraCityIndex(int nationIndex) {
            if (cityCounts[nationIndex] == 0) {
                return -1;
            }
            if (maxInfraCityIndexByNation[nationIndex] < 0) {
                refreshInfraLeaders(nationIndex);
            }
            return maxInfraCityIndexByNation[nationIndex];
        }

        private void refreshInfraLeadersAfterDamage(int nationIndex, int maxCityIndex) {
            int runnerUpCityIndex = runnerUpInfraCityIndexByNation[nationIndex];
            if (runnerUpCityIndex < 0) {
                return;
            }
            int cityBase = cityInfraBaseOffsets[nationIndex];
            double currentValue = cityInfraFlat[cityBase + maxCityIndex];
            double runnerUpValue = cityInfraFlat[cityBase + runnerUpCityIndex];
            if (currentValue < runnerUpValue
                    || (currentValue == runnerUpValue && runnerUpCityIndex < maxCityIndex)) {
                refreshInfraLeaders(nationIndex);
            }
        }

        private void refreshInfraLeaders(int nationIndex) {
            int cityCount = cityCounts[nationIndex];
            if (cityCount <= 0) {
                maxInfraCityIndexByNation[nationIndex] = -1;
                runnerUpInfraCityIndexByNation[nationIndex] = -1;
                return;
            }
            int cityBase = cityInfraBaseOffsets[nationIndex];
            int maxCityIndex = 0;
            int runnerUpCityIndex = -1;
            for (int cityIndex = 1; cityIndex < cityCount; cityIndex++) {
                double cityInfra = cityInfraFlat[cityBase + cityIndex];
                double maxInfra = cityInfraFlat[cityBase + maxCityIndex];
                if (cityInfra > maxInfra) {
                    runnerUpCityIndex = maxCityIndex;
                    maxCityIndex = cityIndex;
                    continue;
                }
                if (runnerUpCityIndex < 0 || cityInfra > cityInfraFlat[cityBase + runnerUpCityIndex]) {
                    runnerUpCityIndex = cityIndex;
                }
            }
            maxInfraCityIndexByNation[nationIndex] = maxCityIndex;
            runnerUpInfraCityIndexByNation[nationIndex] = runnerUpCityIndex;
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
            double score = staticScoreComponent[nationIndex] + totalInfra(nationIndex) / 40d;
            int unitBase = unitBaseOffsets[nationIndex];
            for (MilitaryUnit unit : SimUnits.PURCHASABLE_UNITS) {
                int amount = unitsFlat[unitBase + unit.ordinal()];
                if (amount > 0) {
                    score += unit.getScore(amount);
                }
            }
            scores[nationIndex] = score;
            scoreDirty[nationIndex] = false;
        }

        void invalidateScore(int nationIndex) {
            if (!scoreDirty[nationIndex]) {
                scoreVersionByNation[nationIndex]++;
                if (nationIndex < attackerCount) {
                    attackerSideScoreVersion++;
                } else {
                    defenderSideScoreVersion++;
                }
            }
            scoreDirty[nationIndex] = true;
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
            double[] valueCache = hasActiveWars ? activeWarStrategicMilitaryValueCache : inactiveWarStrategicMilitaryValueCache;
            long[] ownVersionCache = hasActiveWars ? activeWarStrategicMilitaryOwnScoreVersion : inactiveWarStrategicMilitaryOwnScoreVersion;
            long[] opposingVersionCache = hasActiveWars ? activeWarStrategicMilitaryOpposingSideScoreVersion : inactiveWarStrategicMilitaryOpposingSideScoreVersion;
            long[] capabilityVersionCache = hasActiveWars ? activeWarStrategicMilitaryCapabilityVersion : inactiveWarStrategicMilitaryCapabilityVersion;
            long ownScoreVersion = scoreVersionByNation[nationIndex];
            long opposingSideScoreVersion = nationIndex < attackerCount ? defenderSideScoreVersion : attackerSideScoreVersion;
            long capabilityVersion = capabilityVersionByNation[nationIndex];
            double cached = valueCache[nationIndex];
            if (!Double.isNaN(cached)
                    && ownVersionCache[nationIndex] == ownScoreVersion
                    && opposingVersionCache[nationIndex] == opposingSideScoreVersion
                    && capabilityVersionCache[nationIndex] == capabilityVersion) {
                return cached;
            }
            double value = PlannerStrategicValue.strategicMilitaryValue(capabilityVector(nationIndex, hasActiveWars), relevance);
            valueCache[nationIndex] = value;
            ownVersionCache[nationIndex] = ownScoreVersion;
            opposingVersionCache[nationIndex] = opposingSideScoreVersion;
            capabilityVersionCache[nationIndex] = capabilityVersion;
            return value;
        }

        private StrategicCapabilityVector capabilityVector(int nationIndex, boolean hasActiveWars) {
            StrategicCapabilityVector[] cache = hasActiveWars ? activeWarCapabilityCache : inactiveWarCapabilityCache;
            StrategicCapabilityVector cached = cache[nationIndex];
            if (cached != null) {
                return cached;
            }
            StrategicCapabilityVector capability = PlannerStrategicValue.capabilityVector(
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
            cache[nationIndex] = capability;
            return capability;
        }

        private StrategicCapabilityVector capabilityVectorAfterLosses(
                int nationIndex,
                int[] losses,
                StrategicCapabilityVector baselineCapability
        ) {
            return new StrategicCapabilityVector(
                OpeningMetricSummary.groundStrength(
                    projectedUnitAfterLoss(nationIndex, MilitaryUnit.SOLDIER, losses),
                    projectedUnitAfterLoss(nationIndex, MilitaryUnit.TANK, losses),
                    false
                ),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.AIRCRAFT, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.SHIP, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.MISSILE, losses),
                projectedUnitAfterLoss(nationIndex, MilitaryUnit.NUKE, losses),
                baselineCapability.soldierRemainingRecovery(),
                baselineCapability.soldierDailyCap(),
                baselineCapability.tankRemainingRecovery(),
                baselineCapability.tankDailyCap(),
                baselineCapability.airRemainingRecovery(),
                baselineCapability.airDailyCap(),
                baselineCapability.navalRemainingRecovery(),
                baselineCapability.navalDailyCap()
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
                StrategicAssetValue.ActiveWarContext activeWarContext,
                StrategicAssetValue.StrategicRelevance relevance,
                StrategicCapabilityVector baselineCapability,
                double baselineMilitaryValue
        ) {
            double after = PlannerStrategicValue.strategicMilitaryValue(
                capabilityVectorAfterLosses(nationIndex, losses, baselineCapability),
                    relevance
            );
            double damage = Math.max(0d, baselineMilitaryValue - after);
            return damage * StrategicAssetValue.marginalActionSpaceMultiplier(activeWarContext);
        }

        double marginalActionSpaceValue(int nationIndex, DenseWarState warState) {
            StrategicAssetValue.ActiveWarContext activeWarContext = activeWarContext(nationIndex, warState);
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext.hasActiveWars();
            return strategicMilitaryValue(nationIndex, strategicRelevance(nationIndex), hasActiveWars)
                * StrategicAssetValue.marginalActionSpaceMultiplier(activeWarContext);
        }

        private StrategicAssetValue.StrategicRelevance strategicRelevance(int nationIndex) {
            long ownScoreVersion = scoreVersionByNation[nationIndex];
            long opposingSideScoreVersion = nationIndex < attackerCount ? defenderSideScoreVersion : attackerSideScoreVersion;
            StrategicAssetValue.StrategicRelevance cached = strategicRelevanceCache[nationIndex];
            if (cached != null
                    && strategicRelevanceOwnScoreVersion[nationIndex] == ownScoreVersion
                    && strategicRelevanceOpposingSideScoreVersion[nationIndex] == opposingSideScoreVersion) {
                return cached;
            }
            boolean attackerSide = nationIndex < attackerCount;
            int opponentCount = attackerSide ? defenderCount : attackerCount;
            StrategicAssetValue.StrategicRelevance relevance = StrategicAssetValue.relevanceForWarRange(
                    cityCounts[nationIndex],
                score(nationIndex),
                    baseHasActiveWars[nationIndex] ? 1 : 0,
                    opponentCount,
                    opponentIndex -> attackerSide
                    ? score(attackerCount + opponentIndex)
                    : score(opponentIndex)
            );
            strategicRelevanceCache[nationIndex] = relevance;
            strategicRelevanceOwnScoreVersion[nationIndex] = ownScoreVersion;
            strategicRelevanceOpposingSideScoreVersion[nationIndex] = opposingSideScoreVersion;
            return relevance;
        }

        private StrategicAssetValue.ActiveWarContext activeWarContext(int nationIndex, DenseWarState warState) {
            if (warState == null) {
                return StrategicAssetValue.ActiveWarContext.basic(baseHasActiveWars[nationIndex]);
            }
            int offensiveWars = warState.activeOffensiveWarCount(nationIndex);
            int defensiveWars = warState.activeDefensiveWarCount(nationIndex);
            int activeOpponents = offensiveWars + defensiveWars;
            if (activeOpponents == 0) {
                return StrategicAssetValue.ActiveWarContext.basic(baseHasActiveWars[nationIndex]);
            }
            int ownMaps = 0;
            int enemyMaps = 0;
            int ownResistance = 0;
            int enemyResistance = 0;
            int ownControls = 0;
            int enemyControls = 0;
            for (int warIndex = warState.firstOffensiveWarForNation(nationIndex);
                 warIndex >= 0;
                 warIndex = warState.nextOffensiveWarForNation(warIndex)) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                ownMaps += warState.attackerMaps[warIndex];
                enemyMaps += warState.defenderMaps[warIndex];
                ownResistance += warState.attackerResistance[warIndex];
                enemyResistance += warState.defenderResistance[warIndex];
                ownControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        DenseWarState.OWNER_ATTACKER,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
                enemyControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        DenseWarState.OWNER_DEFENDER,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
            }
            for (int warIndex = warState.firstDefensiveWarForNation(nationIndex);
                 warIndex >= 0;
                 warIndex = warState.nextDefensiveWarForNation(warIndex)) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                ownMaps += warState.defenderMaps[warIndex];
                enemyMaps += warState.attackerMaps[warIndex];
                ownResistance += warState.defenderResistance[warIndex];
                enemyResistance += warState.attackerResistance[warIndex];
                ownControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        DenseWarState.OWNER_DEFENDER,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
                enemyControls += PlannerControlStateReducer.controlCountForOwnerCode(
                        DenseWarState.OWNER_ATTACKER,
                        warState.groundSuperiorityOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
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
            if (scoreDirty[nationIndex]) {
                recalculateScore(nationIndex);
            }
            return scores[nationIndex];
        }

        double combatStrength(int nationIndex) {
            double cached = combatStrengthCache[nationIndex];
            if (!Double.isNaN(cached)) {
                return cached;
            }
            double value = groundStrength(nationIndex, false)
                    + (3d * unit(nationIndex, MilitaryUnit.AIRCRAFT))
                    + (2d * unit(nationIndex, MilitaryUnit.SHIP));
            combatStrengthCache[nationIndex] = value;
            return value;
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
            double[] cache = underAir ? groundStrengthUnderAirCache : groundStrengthCache;
            double cached = cache[nationIndex];
            if (!Double.isNaN(cached)) {
                return cached;
            }
            double value = UnitEconomy.groundStrengthRaw(
                    unit(nationIndex, MilitaryUnit.SOLDIER),
                    unit(nationIndex, MilitaryUnit.TANK),
                    false,
                    underAir
            );
            cache[nationIndex] = value;
            return value;
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
        private final int[] firstOffensiveWarByNation;
        private final int[] firstDefensiveWarByNation;
        private final int[] activeOffensiveWarsByNation;
        private final int[] activeDefensiveWarsByNation;
        private int[] previousActiveWar;
        private int[] nextActiveWar;
        private int firstActiveWar;
        private int[] nextOffensiveWarByNation;
        private int[] nextDefensiveWarByNation;
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
                int[] firstOffensiveWarByNation,
                int[] firstDefensiveWarByNation,
                int[] activeOffensiveWarsByNation,
                int[] activeDefensiveWarsByNation,
                int[] previousActiveWar,
                int[] nextActiveWar,
                int firstActiveWar,
                int[] nextOffensiveWarByNation,
                int[] nextDefensiveWarByNation,
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
            this.firstOffensiveWarByNation = firstOffensiveWarByNation;
            this.firstDefensiveWarByNation = firstDefensiveWarByNation;
            this.activeOffensiveWarsByNation = activeOffensiveWarsByNation;
            this.activeDefensiveWarsByNation = activeDefensiveWarsByNation;
            this.previousActiveWar = previousActiveWar;
            this.nextActiveWar = nextActiveWar;
            this.firstActiveWar = firstActiveWar;
            this.nextOffensiveWarByNation = nextOffensiveWarByNation;
            this.nextDefensiveWarByNation = nextDefensiveWarByNation;
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
                int[] firstOffensiveWarByNation = new int[nationCount];
                int[] firstDefensiveWarByNation = new int[nationCount];
                int[] activeOffensiveWarsByNation = new int[nationCount];
                int[] activeDefensiveWarsByNation = new int[nationCount];
                int[] previousActiveWar = new int[capacity];
                int[] nextActiveWar = new int[capacity];
                int[] nextOffensiveWarByNation = new int[capacity];
                int[] nextDefensiveWarByNation = new int[capacity];
                Arrays.fill(firstOffensiveWarByNation, -1);
                Arrays.fill(firstDefensiveWarByNation, -1);
                Arrays.fill(previousActiveWar, -1);
                Arrays.fill(nextActiveWar, -1);
                Arrays.fill(nextOffensiveWarByNation, -1);
                Arrays.fill(nextDefensiveWarByNation, -1);
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
                    firstOffensiveWarByNation,
                    firstDefensiveWarByNation,
                    activeOffensiveWarsByNation,
                    activeDefensiveWarsByNation,
                    previousActiveWar,
                    nextActiveWar,
                    -1,
                    nextOffensiveWarByNation,
                    nextDefensiveWarByNation,
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
            clearWarIncidenceIndex();
            Arrays.fill(activeOffensiveWarsByNation, 0);
            Arrays.fill(activeDefensiveWarsByNation, 0);
            Arrays.fill(previousActiveWar, -1);
            Arrays.fill(nextActiveWar, -1);
            firstActiveWar = -1;
            openingEdgeCount = edgeCount;
            warCount = edgeCount;
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                attackerNationIndex[edgeIndex] = edges.attackerIndex(edgeIndex);
                defenderNationIndex[edgeIndex] = state.attackerCount + edges.defenderIndex(edgeIndex);
                linkWarToNationIndexes(edgeIndex);
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
                    Arrays.copyOf(activeOffensiveWarsByNation, activeOffensiveWarsByNation.length),
                    Arrays.copyOf(activeDefensiveWarsByNation, activeDefensiveWarsByNation.length),
                    Arrays.copyOf(previousActiveWar, previousActiveWar.length),
                    Arrays.copyOf(nextActiveWar, nextActiveWar.length),
                    firstActiveWar,
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
            System.arraycopy(checkpoint.activeOffensiveWarsByNation(), 0, activeOffensiveWarsByNation, 0, checkpoint.activeOffensiveWarsByNation().length);
            System.arraycopy(checkpoint.activeDefensiveWarsByNation(), 0, activeDefensiveWarsByNation, 0, checkpoint.activeDefensiveWarsByNation().length);
            System.arraycopy(checkpoint.previousActiveWar(), 0, previousActiveWar, 0, checkpoint.previousActiveWar().length);
            System.arraycopy(checkpoint.nextActiveWar(), 0, nextActiveWar, 0, checkpoint.nextActiveWar().length);
            firstActiveWar = checkpoint.firstActiveWar();
            openingEdgeCount = checkpoint.openingEdgeCount();
            warCount = checkpoint.warCount();
            rebuildWarIncidenceIndex();
        }

        void applyOpeningAssignment(boolean[] edgeAssigned) {
            for (int edgeIndex = 0; edgeIndex < openingEdgeCount; edgeIndex++) {
                active[edgeIndex] = edgeAssigned[edgeIndex];
                if (edgeAssigned[edgeIndex]) {
                    activePairs[pairIndex(attackerNationIndex[edgeIndex], defenderNationIndex[edgeIndex], nationCount)] = true;
                    incrementActiveWarCounts(edgeIndex);
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
            clearWarIncidenceIndex();
            Arrays.fill(activeOffensiveWarsByNation, 0);
            Arrays.fill(activeDefensiveWarsByNation, 0);
            Arrays.fill(previousActiveWar, -1);
            Arrays.fill(nextActiveWar, -1);
            firstActiveWar = -1;
            openingEdgeCount = edgeCount;
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                attackerNationIndex[edgeIndex] = edges.attackerIndex(edgeIndex);
                defenderNationIndex[edgeIndex] = state.attackerCount + edges.defenderIndex(edgeIndex);
                linkWarToNationIndexes(edgeIndex);
                active[edgeIndex] = edgeAssigned[edgeIndex];
                if (edgeAssigned[edgeIndex]) {
                    activePairs[pairIndex(attackerNationIndex[edgeIndex], defenderNationIndex[edgeIndex], nationCount)] = true;
                    incrementActiveWarCounts(edgeIndex);
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
            linkWarToNationIndexes(index);
            active[index] = true;
            incrementActiveWarCounts(index);
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
            decrementActiveWarCounts(warIndex);
            unlinkActiveWar(warIndex);
            activePairs[pairIndex(attackerNationIndex[warIndex], defenderNationIndex[warIndex], nationCount)] = false;
            pairUnlockTurn[lockoutPairIndex(attackerNationIndex[warIndex], defenderNationIndex[warIndex], nationCount)]
                    = currentTurn + WarSlotRules.sameOpponentLockoutTurns();
        }

        private void incrementActiveWarCounts(int warIndex) {
            activeOffensiveWarsByNation[attackerNationIndex[warIndex]]++;
            activeDefensiveWarsByNation[defenderNationIndex[warIndex]]++;
            linkActiveWar(warIndex);
        }

        private void decrementActiveWarCounts(int warIndex) {
            activeOffensiveWarsByNation[attackerNationIndex[warIndex]]--;
            activeDefensiveWarsByNation[defenderNationIndex[warIndex]]--;
        }

        private void linkActiveWar(int warIndex) {
            previousActiveWar[warIndex] = -1;
            nextActiveWar[warIndex] = firstActiveWar;
            if (firstActiveWar >= 0) {
                previousActiveWar[firstActiveWar] = warIndex;
            }
            firstActiveWar = warIndex;
        }

        private void unlinkActiveWar(int warIndex) {
            int previousWarIndex = previousActiveWar[warIndex];
            int nextWarIndex = nextActiveWar[warIndex];
            if (previousWarIndex >= 0) {
                nextActiveWar[previousWarIndex] = nextWarIndex;
            } else {
                firstActiveWar = nextWarIndex;
            }
            if (nextWarIndex >= 0) {
                previousActiveWar[nextWarIndex] = previousWarIndex;
            }
            previousActiveWar[warIndex] = -1;
            nextActiveWar[warIndex] = -1;
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
            int previousActiveWarCapacity = previousActiveWar.length;
            previousActiveWar = Arrays.copyOf(previousActiveWar, nextCapacity);
            nextActiveWar = Arrays.copyOf(nextActiveWar, nextCapacity);
            Arrays.fill(previousActiveWar, previousActiveWarCapacity, nextCapacity, -1);
            Arrays.fill(nextActiveWar, previousActiveWarCapacity, nextCapacity, -1);
            int previousCapacity = nextOffensiveWarByNation.length;
            nextOffensiveWarByNation = Arrays.copyOf(nextOffensiveWarByNation, nextCapacity);
            nextDefensiveWarByNation = Arrays.copyOf(nextDefensiveWarByNation, nextCapacity);
            Arrays.fill(nextOffensiveWarByNation, previousCapacity, nextCapacity, -1);
            Arrays.fill(nextDefensiveWarByNation, previousCapacity, nextCapacity, -1);
        }

        int firstActiveWar() {
            return firstActiveWar;
        }

        int nextActiveWar(int warIndex) {
            return nextActiveWar[warIndex];
        }

        int firstOffensiveWarForNation(int nationIndex) {
            return firstOffensiveWarByNation[nationIndex];
        }

        int nextOffensiveWarForNation(int warIndex) {
            return nextOffensiveWarByNation[warIndex];
        }

        int firstDefensiveWarForNation(int nationIndex) {
            return firstDefensiveWarByNation[nationIndex];
        }

        int nextDefensiveWarForNation(int warIndex) {
            return nextDefensiveWarByNation[warIndex];
        }

        int activeOffensiveWarCount(int nationIndex) {
            return activeOffensiveWarsByNation[nationIndex];
        }

        int activeDefensiveWarCount(int nationIndex) {
            return activeDefensiveWarsByNation[nationIndex];
        }

        private void clearWarIncidenceIndex() {
            Arrays.fill(firstOffensiveWarByNation, -1);
            Arrays.fill(firstDefensiveWarByNation, -1);
            Arrays.fill(nextOffensiveWarByNation, 0, Math.max(0, warCount), -1);
            Arrays.fill(nextDefensiveWarByNation, 0, Math.max(0, warCount), -1);
        }

        private void rebuildWarIncidenceIndex() {
            clearWarIncidenceIndex();
            for (int warIndex = 0; warIndex < warCount; warIndex++) {
                linkWarToNationIndexes(warIndex);
            }
        }

        private void linkWarToNationIndexes(int warIndex) {
            int attackerIndex = attackerNationIndex[warIndex];
            nextOffensiveWarByNation[warIndex] = firstOffensiveWarByNation[attackerIndex];
            firstOffensiveWarByNation[attackerIndex] = warIndex;

            int defenderIndex = defenderNationIndex[warIndex];
            nextDefensiveWarByNation[warIndex] = firstDefensiveWarByNation[defenderIndex];
            firstDefensiveWarByNation[defenderIndex] = warIndex;
        }

        void fillActiveWarsByNation(boolean[] activeWarsByNation) {
            for (int nationIndex = 0; nationIndex < nationCount; nationIndex++) {
                activeWarsByNation[nationIndex] = activeOffensiveWarsByNation[nationIndex] > 0
                        || activeDefensiveWarsByNation[nationIndex] > 0;
            }
        }

        void fillActiveWarCounts(int[] activeOffensiveWarsByNation, int[] activeDefensiveWarsByNation) {
            System.arraycopy(this.activeOffensiveWarsByNation, 0, activeOffensiveWarsByNation, 0, nationCount);
            System.arraycopy(this.activeDefensiveWarsByNation, 0, activeDefensiveWarsByNation, 0, nationCount);
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
        private ProjectionState state;
        private DenseWarState warState;
        private int warIndex;

        private DenseWarContext() {
        }

        private DenseWarContext bind(ProjectionState state, DenseWarState warState) {
            this.state = state;
            this.warState = warState;
            return this;
        }

        void setWarIndex(int warIndex) {
            this.warIndex = warIndex;
        }

        @Override
        public boolean isActive() {
            return warState.active[warIndex];
        }

        @Override
        public int groundSuperiorityNationId() {
            return controlNationId(warState.groundSuperiorityOwner[warIndex]);
        }

        @Override
        public int airSuperiorityNationId() {
            return controlNationId(warState.airSuperiorityOwner[warIndex]);
        }

        @Override
        public int blockadeNationId() {
            return controlNationId(warState.blockadeOwner[warIndex]);
        }

        @Override
        public void setgroundSuperiorityNationId(int nationId) {
            warState.groundSuperiorityOwner[warIndex] = ownerCode(nationId);
        }

        @Override
        public void setAirSuperiorityNationId(int nationId) {
            warState.airSuperiorityOwner[warIndex] = ownerCode(nationId);
        }

        @Override
        public void setBlockadeNationId(int nationId) {
            warState.blockadeOwner[warIndex] = ownerCode(nationId);
        }

        private int controlNationId(int ownerCode) {
            return switch (ownerCode) {
                case DenseWarState.OWNER_ATTACKER -> state.nationIds[warState.attackerNationIndex[warIndex]];
                case DenseWarState.OWNER_DEFENDER -> state.nationIds[warState.defenderNationIndex[warIndex]];
                default -> WarControlRules.MutableWarControlState.NO_NATION_ID;
            };
        }

        private int ownerCode(int nationId) {
            if (nationId == WarControlRules.MutableWarControlState.NO_NATION_ID) {
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
            int[] activeOpponentsByNation = new int[state.nationIds.length];
            int[] seededOffensiveWarsByNation = new int[state.nationIds.length];
            int[] projectedOffensiveWarsByNation = new int[state.nationIds.length];
            int[] seededDefensiveWarsByNation = new int[state.nationIds.length];
            int[] projectedDefensiveWarsByNation = new int[state.nationIds.length];
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                int attackerNationIndex = warState.attackerNationIndex[warIndex];
                int defenderNationIndex = warState.defenderNationIndex[warIndex];
                activeOpponentsByNation[attackerNationIndex]++;
                activeOpponentsByNation[defenderNationIndex]++;
                if (warState.seededCurrentWar[warIndex]) {
                    seededOffensiveWarsByNation[attackerNationIndex]++;
                    seededDefensiveWarsByNation[defenderNationIndex]++;
                } else {
                    projectedOffensiveWarsByNation[attackerNationIndex]++;
                    projectedDefensiveWarsByNation[defenderNationIndex]++;
                }
            }
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                int attackerNationIndex = warState.attackerNationIndex[warIndex];
                int defenderNationIndex = warState.defenderNationIndex[warIndex];
                DBNationSnapshot attackerSnapshot = snapshot(attackerNationIndex);
                DBNationSnapshot defenderSnapshot = snapshot(defenderNationIndex);
                double attackerSlotPressure = offensiveSlotPressure(
                        attackerSnapshot,
                        seededOffensiveWarsByNation[attackerNationIndex],
                        projectedOffensiveWarsByNation[attackerNationIndex]
                );
                double defenderSlotPressure = defensiveSlotPressure(
                        defenderSnapshot,
                        seededDefensiveWarsByNation[defenderNationIndex],
                        projectedDefensiveWarsByNation[defenderNationIndex]
                );
                int attackerOpponents = activeOpponentCount(attackerSnapshot, activeOpponentsByNation[attackerNationIndex]);
                int defenderOpponents = activeOpponentCount(defenderSnapshot, activeOpponentsByNation[defenderNationIndex]);
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
            return state.teamIds[nationIndex];
        }

        private double offensiveSlotPressure(DBNationSnapshot snapshot, int seededOffensiveWars, int projectedOffensiveWars) {
            int maxOffensiveSlots = Math.max(1, snapshot.maxOff());
            return effectiveWarCount(snapshot.currentOffensiveWars(), seededOffensiveWars, projectedOffensiveWars)
                    / (double) maxOffensiveSlots;
        }

        private double defensiveSlotPressure(DBNationSnapshot snapshot, int seededDefensiveWars, int projectedDefensiveWars) {
            return effectiveWarCount(snapshot.currentDefensiveWars(), seededDefensiveWars, projectedDefensiveWars)
                    / (double) WarSlotRules.defensiveSlotCap();
        }

        private int activeOpponentCount(DBNationSnapshot snapshot, int activeOpponents) {
            return Math.max(activeOpponents, snapshot.activeOpponentNationIds().size());
        }

        private int effectiveWarCount(int baselineWars, int seededWars, int projectedWars) {
            return Math.max(Math.max(0, baselineWars), seededWars) + projectedWars;
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
                    state.turnsNoControl,
                    saturatedInt(profiledCounterDeclarations),
                    saturatedInt(profiledRedeclarations),
                    saturatedInt(profiledCounterDeclarationsThrottled)
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
                double[] scoreBaseline,
                double[] scoreMid
        ) {
            if (index < 0 || index >= strengthBase.length) {
                return 1d;
            }
            double strengthRatio = strengthBase[index] > 0d
                    ? strengthMid[index] / strengthBase[index]
                    : 1d;
                double scoreRatio = scoreBaseline[index] > 0d
                    ? scoreMid[index] / scoreBaseline[index]
                    : 1d;
            // Geometric mean weights strength + score equally so a heavily damaged attacker drops
            // both immediate harm AND future-war leverage proportionally.
            double combined = Math.sqrt(Math.max(0d, strengthRatio) * Math.max(0d, scoreRatio));
            return Math.max(0.01d, Math.min(1d, combined));
        }
    }

    record AttackerMidHorizonSnapshot(
            double[] attackerStrengthsBaseline,
            double[] attackerStrengthsMid,
            double[] attackerScoresBaseline,
            double[] attackerScoresMid
    ) {
        double attackerEdgeFactor(int attackerIndex) {
            return MidHorizonSnapshot.clampedRatio(
                    attackerStrengthsBaseline,
                    attackerStrengthsMid,
                    attackerIndex,
                    attackerScoresBaseline,
                    attackerScoresMid
            );
        }
    }

            private record MidHorizonBaseline(
                double[] attackerStrengthsBaseline,
                double[] defenderStrengthsBaseline,
                double[] attackerScoresBaseline,
                double[] defenderScoresBaseline
            ) {
            }

            private record AttackerMidHorizonBaseline(
                double[] attackerStrengthsBaseline,
                double[] attackerScoresBaseline
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

        record ProjectedAttackerFeedbackEvaluation(
            ProjectedEvaluation projectedEvaluation,
            AttackerMidHorizonSnapshot attackerMidHorizonSnapshot
        ) {
        }

    private record ProjectedDeclarationSnapshotState(
            int[] seededOffensiveWars,
            int[] projectedOffensiveWars,
            int[] seededDefensiveWars,
            int[] projectedDefensiveWars,
            IntOpenHashSet[] activeOpponentsByNation
    ) {
        int effectiveOffensiveWars(int nationIndex, int baselineOffensiveWars) {
            return Math.max(Math.max(0, baselineOffensiveWars), seededOffensiveWars[nationIndex])
                    + projectedOffensiveWars[nationIndex];
        }

        int effectiveDefensiveWars(int nationIndex, int baselineDefensiveWars) {
            return Math.max(Math.max(0, baselineDefensiveWars), seededDefensiveWars[nationIndex])
                    + projectedDefensiveWars[nationIndex];
        }

        Set<Integer> activeOpponentNationIds(int nationIndex) {
            return activeOpponentsByNation[nationIndex];
        }
    }

    private record ActiveWarProfileKey(
            long[] activeWords,
            int hash
    ) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ActiveWarProfileKey other)) {
                return false;
            }
            return Arrays.equals(activeWords, other.activeWords);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    static final class PreparedProjectionCaches {
        private final Map<ActiveWarProfileKey, ProjectionStateCheckpoint> stateCheckpoints = new HashMap<>();
        private CandidateEdgeTable warTemplateEdges;
        private DenseWarStateCheckpoint warTemplateCheckpoint;

        private DenseWarStateCheckpoint warTemplateCheckpointFor(CandidateEdgeTable edges) {
            if (warTemplateEdges == null || warTemplateCheckpoint == null) {
                return null;
            }
            return edges.sameProjectionTopology(warTemplateEdges) ? warTemplateCheckpoint : null;
        }

        private void rememberWarTemplate(CandidateEdgeTable edges, DenseWarStateCheckpoint checkpoint) {
            this.warTemplateEdges = edges;
            this.warTemplateCheckpoint = checkpoint;
        }
    }

    private record ProjectionStateCheckpoint(
            int[] unitsFlat,
            int[] unitsBoughtTodayFlat,
            int[] pendingBuysFlat,
            double[] resourcesFlat,
            double[] cityInfraFlat,
            double[] scores,
            boolean[] scoreDirty,
            int[] beigeTurns,
            int[] maxInfraCityIndexByNation,
            int[] runnerUpInfraCityIndexByNation
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
            int[] activeOffensiveWarsByNation,
            int[] activeDefensiveWarsByNation,
                int[] previousActiveWar,
                int[] nextActiveWar,
                int firstActiveWar,
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
            int turnsNoControl,
                int respondingSideLaterDeclarations,
                int openingSideLaterDeclarations,
                int respondingSideLaterDeclarationsThrottled
    ) {
    }

    private static int saturatedInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

            private record ProjectedLaterDeclarationInputs(
                CompiledScenario scenario,
                CandidateEdgeTable edges,
                int[] declarerCaps,
                int[] targetCaps,
                int[] declarerNationIds,
                int[] targetNationIds,
                Int2IntOpenHashMap declarerOverallIndexesByNationId,
                    Int2IntOpenHashMap targetOverallIndexesByNationId,
                    Long2IntOpenHashMap edgeIndexByPair
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

                private record ProjectedAssignedDeclarationCandidate(
                    int declarerNationId,
                    int targetNationId,
                    int declarerNationIndex,
                    int targetNationIndex,
                    int edgeIndex,
                    float scalarScore,
                    int selectionOrder
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
