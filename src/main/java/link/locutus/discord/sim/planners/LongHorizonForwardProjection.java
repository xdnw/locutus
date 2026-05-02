package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.sim.SimUnits;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicAssetValue;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.TeamWarControlView;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.sim.combat.AttackScratch;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.ControlFlagDelta;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.combat.WarControlRules;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.planners.compile.CompiledScenario;
import link.locutus.discord.util.PW;

import java.util.Arrays;
import java.util.Map;

/**
 * Primitive forward-projection surface for long-horizon blitz assignment scoring.
 *
 * <p>This is intentionally not a replay engine. It owns dense planner-local arrays that can price
 * terminal score, active-war control metrics, unit rebuy capacity, and expected counter exposure
 * without constructing local conflict worlds for each assignment candidate.</p>
 */
final class LongHorizonForwardProjection {
    private static final double COUNTER_OPPORTUNITY_COST_WEIGHT = 0.32d;
    private static final int INITIAL_WAR_MAPS = 6;
    private static final int INITIAL_RESISTANCE = 100;
    private static final int MAP_CAP = 12;
    private static final int WAR_EXPIRATION_TURN = 60;
    private static final int PROJECTED_COUNTER_START_TURN = 1;
    private static final double PROJECTED_COUNTER_MIN_SCORE = 8d;
    private static final int PROJECTED_REDECLARE_START_TURN = WAR_EXPIRATION_TURN;
    private static final double PROJECTED_REDECLARE_MIN_SCORE = 8d;
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
    private final double[] attackerCounterPressures;
    private final double[] attackerCounterPenaltyScales;
    private final int[] attackerCaps;

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
            double[] attackerCounterPressures,
            double[] attackerCounterPenaltyScales,
            int[] attackerCaps
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
        this.attackerCounterPressures = attackerCounterPressures;
        this.attackerCounterPenaltyScales = attackerCounterPenaltyScales;
        this.attackerCaps = attackerCaps;
    }

    static LongHorizonForwardProjection create(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
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

        double[] attackerCounterPressures = counterPressures(scenario, attackerCombatStrengths, defenderCombatStrengths);
        double[] attackerCounterPenaltyScales = counterPenaltyScales(
                attackerInitialScores,
                attackerProjectedBuyScore,
                attackerCombatStrengths,
                attackerCounterPressures
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
                attackerCounterPressures,
                attackerCounterPenaltyScales,
                Arrays.copyOf(attackerCaps, attackerCaps.length)
        );
    }

    double attackerCounterOpportunityMarginalScore(int attackerIndex, int assignedBefore) {
        if (attackerIndex < 0 || attackerIndex >= attackerCounterPenaltyScales.length || assignedBefore < 0) {
            return 0d;
        }
        double scale = attackerCounterPenaltyScales[attackerIndex];
        if (!(scale > 0d)) {
            return 0d;
        }
        int usefulCapacity = Math.max(1, Math.min(3, attackerCaps[attackerIndex]));
        double slotPressure = Math.min(1.75d, (assignedBefore + 1d) / usefulCapacity);
        return -horizonFactor * COUNTER_OPPORTUNITY_COST_WEIGHT * scale * slotPressure;
    }

    double counterOpportunityScore(int[] attackerCounts) {
        double score = 0d;
        for (int attackerIndex = 0; attackerIndex < attackerCounts.length; attackerIndex++) {
            for (int assignedBefore = 0; assignedBefore < attackerCounts[attackerIndex]; assignedBefore++) {
                score += attackerCounterOpportunityMarginalScore(attackerIndex, assignedBefore);
            }
        }
        return score;
    }

    double projectedObjectiveScore(
            TeamScoreObjective objective,
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
            TeamScoreObjective objective,
            int teamId,
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts
    ) {
        int[] counterIncidence = new int[scenario.attackerCount()];
        ProjectionView view = project(edgeAssigned, attackerCounts, defenderCounts, counterIncidence);
        return new ProjectedEvaluation(objective.scoreTerminal(view, teamId), counterIncidence);
    }

    private ProjectionView project(
            boolean[] edgeAssigned,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut
    ) {
        ProjectionState state = ProjectionState.from(scenario);
        DenseWarState warState = DenseWarState.from(edges, edgeAssigned, state, projectedExtraDeclareCapacity(attackerCounts));
        simulateProjectedWars(state, warState, attackerCounts, defenderCounts, counterIncidenceOut);
        double[] warTargetPressures = new double[warState.warCount];
        double[] warFutureWarLeverages = new double[warState.warCount];
        boolean[] warMetricPresent = new boolean[warState.warCount];
        for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
            if (warState.active[warIndex]) {
                warMetricPresent[warIndex] = true;
                warTargetPressures[warIndex] = state.targetPressure(warState.attackerNationIndex[warIndex], warState.defenderNationIndex[warIndex]);
                warFutureWarLeverages[warIndex] = state.futureWarLeverage(
                        warState.attackerNationIndex[warIndex],
                        warState.defenderNationIndex[warIndex],
                        warState.attackerHasAirControl(warIndex),
                        warState.defenderHasAirControl(warIndex),
                        warState.defenderResistance[warIndex]
                );
            } else if (warIndex < edges.edgeCount() && edgeAssigned[warIndex]) {
                warMetricPresent[warIndex] = true;
                warTargetPressures[warIndex] = positiveControlLeverage(warIndex);
                warFutureWarLeverages[warIndex] = positiveFutureWarLeverage(warIndex);
            }
        }

        double[] attackerScores = state.attackerScores();
        double[] defenderScores = state.defenderScores();
        double[] attackerValues = state.attackerStrategicValues(warState);
        double[] defenderValues = state.defenderStrategicValues(warState);
        return new ProjectionView(
                attackerScores,
                defenderScores,
                attackerValues,
                defenderValues,
                Arrays.copyOf(warState.active, warState.warCount),
                Arrays.copyOf(warState.attackerNationIndex, warState.warCount),
                Arrays.copyOf(warState.defenderNationIndex, warState.warCount),
                Arrays.copyOf(warState.groundControlOwner, warState.warCount),
                Arrays.copyOf(warState.airSuperiorityOwner, warState.warCount),
                Arrays.copyOf(warState.blockadeOwner, warState.warCount),
                Arrays.copyOf(warState.attackerResistance, warState.warCount),
                Arrays.copyOf(warState.defenderResistance, warState.warCount),
                warMetricPresent,
                warTargetPressures,
                warFutureWarLeverages
        );
    }

    /**
     * Runs forward projection and returns the per-opening-attacker count of projected counter
     * declarations against each attacker. Allocates only the output array, the projection state,
     * and the war state used to advance combat.
     */
    int[] realizedCounterIncidence(boolean[] edgeAssigned, int[] attackerCounts, int[] defenderCounts) {
        int[] counterIncidence = new int[scenario.attackerCount()];
        ProjectionState state = ProjectionState.from(scenario);
        DenseWarState warState = DenseWarState.from(edges, edgeAssigned, state, projectedExtraDeclareCapacity(attackerCounts));
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
        ProjectionState state = ProjectionState.from(scenario);
        DenseWarState warState = DenseWarState.from(edges, edgeAssigned, state, projectedExtraDeclareCapacity(attackerCounts));

        // Apply the same start-of-projection buy/pending-resolution as simulateProjectedWars so
        // baseline strengths and scores are measured at the same point in time as mid-horizon
        // values. This prevents projected new unit purchases from producing a baseline that is
        // strictly smaller than the mid-horizon snapshot for an undamaged attacker.
        state.materializePendingBuys();
        boolean[] activeWarsByNation = new boolean[state.nationIds.length];
        warState.fillActiveWarsByNation(activeWarsByNation);
        state.applyDailyBuys(false, activeWarsByNation);

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

        // Now run the dense forward projection against the same state the baseline was measured
        // from. Skip the redundant initial materialize+applyDailyBuys since we already did them.
        runProjectionTurns(state, warState, activeWarsByNation, attackerCounts, defenderCounts, counterIncidence, turns);

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
                attackerBaselineStrengths,
                attackerStrengths,
                defenderBaselineStrengths,
                defenderStrengths,
                attackerBaselineScores,
                attackerScores,
                defenderBaselineScores,
                defenderScores,
                counterIncidence
        );
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

    private void simulateProjectedWarsForTurns(
            ProjectionState state,
            DenseWarState warState,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut,
            int turnsToRun
    ) {
        state.materializePendingBuys();
        boolean[] activeWarsByNation = new boolean[state.nationIds.length];
        warState.fillActiveWarsByNation(activeWarsByNation);
        state.applyDailyBuys(false, activeWarsByNation);
        runProjectionTurns(state, warState, activeWarsByNation, attackerCounts, defenderCounts, counterIncidenceOut, turnsToRun);
    }

    private void runProjectionTurns(
            ProjectionState state,
            DenseWarState warState,
            boolean[] activeWarsByNation,
            int[] attackerCounts,
            int[] defenderCounts,
            int[] counterIncidenceOut,
            int turnsToRun
    ) {
        DenseWarContext context = new DenseWarContext(state, warState);
        AttackScratch scratch = new AttackScratch();
        MutableAttackResult result = new MutableAttackResult();
        int bound = Math.max(0, Math.min(horizonTurns, turnsToRun));
        for (int turn = 0; turn < bound; turn++) {
            if (turn > 0) {
                advanceTurn(state, warState, turn, activeWarsByNation);
            }
            if (shouldDeclareProjectedCounters(turn, attackerCounts, defenderCounts)) {
                declareProjectedCounters(state, warState, attackerCounts, turn, counterIncidenceOut);
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            if (shouldDeclareProjectedRedeclares(turn)) {
                declareProjectedAttackerRedeclares(state, warState, turn);
                warState.fillActiveWarsByNation(activeWarsByNation);
            }
            for (int warIndex = 0; warIndex < warState.warCount; warIndex++) {
                if (!warState.active[warIndex]) {
                    continue;
                }
                context.setWarIndex(warIndex);
                simulateAdaptiveAttacks(state, warState, context, scratch, result);
            }
        }
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
        int[] activeOffensiveWarsByNation = new int[state.nationIds.length];
        int[] activeDefensiveWarsByNation = new int[state.nationIds.length];
        warState.fillActiveWarCounts(activeOffensiveWarsByNation, activeDefensiveWarsByNation);
        int[] remainingCounterOffensiveSlots = projectedCounterOffensiveSlots(state, activeOffensiveWarsByNation);
        int[] remainingTargetDefensiveSlots = projectedCounterTargetDefensiveSlots(state, activeDefensiveWarsByNation, attackerCounts);
        boolean[] declaredPairs = new boolean[Math.max(1, scenario.defenderCount() * Math.max(1, scenario.attackerCount()))];
        while (true) {
            int bestDefenderIndex = -1;
            int bestAttackerIndex = -1;
            double bestScore = PROJECTED_COUNTER_MIN_SCORE;
            for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
                if (remainingCounterOffensiveSlots[defenderIndex] <= 0) {
                    continue;
                }
                int counterNationIndex = state.attackerCount + defenderIndex;
                for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                    if (remainingTargetDefensiveSlots[attackerIndex] <= 0 || attackerCounts[attackerIndex] <= 0) {
                        continue;
                    }
                    int pairIndex = defenderIndex * scenario.attackerCount() + attackerIndex;
                    if (declaredPairs[pairIndex]) {
                        continue;
                    }
                    int targetNationIndex = attackerIndex;
                    if (warState.hasActivePair(counterNationIndex, targetNationIndex)) {
                        continue;
                    }
                    double score = projectedCounterScore(state, counterNationIndex, targetNationIndex, defenderIndex, remainingTargetDefensiveSlots[attackerIndex]);
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
            declaredPairs[bestDefenderIndex * scenario.attackerCount() + bestAttackerIndex] = true;
            remainingCounterOffensiveSlots[bestDefenderIndex]--;
            remainingTargetDefensiveSlots[bestAttackerIndex]--;
            warState.addWar(state.attackerCount + bestDefenderIndex, bestAttackerIndex, turn);
            if (counterIncidenceOut != null) {
                counterIncidenceOut[bestAttackerIndex]++;
            }
        }
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

    private boolean shouldDeclareProjectedRedeclares(int turn) {
        if (horizonTurns <= PROJECTED_REDECLARE_START_TURN || turn < PROJECTED_REDECLARE_START_TURN) {
            return false;
        }
        return edges.edgeCount() > 0;
    }

    private void declareProjectedAttackerRedeclares(
            ProjectionState state,
            DenseWarState warState,
            int turn
    ) {
        int attackerCount = scenario.attackerCount();
        int defenderCount = scenario.defenderCount();
        int[] activeOff = new int[state.nationIds.length];
        int[] activeDef = new int[state.nationIds.length];
        warState.fillActiveWarCounts(activeOff, activeDef);

        int[] remainingAttackerSlots = new int[attackerCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            if (state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            int rawFreeOff = scenario.attacker(attackerIndex).rawFreeOff();
            remainingAttackerSlots[attackerIndex] = Math.max(0,
                    Math.min(attackerCaps[attackerIndex], rawFreeOff) - activeOff[attackerIndex]);
        }
        int[] remainingDefenderSlots = new int[defenderCount];
        for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
            int defenderNationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[defenderNationIndex] > 0 || state.combatStrength(defenderNationIndex) <= 0d) {
                continue;
            }
            remainingDefenderSlots[defenderIndex] = Math.max(0,
                    scenario.defender(defenderIndex).rawFreeDef() - activeDef[defenderNationIndex]);
        }
        if (!hasAnyAvailable(remainingAttackerSlots) || !hasAnyAvailable(remainingDefenderSlots)) {
            return;
        }

        int edgeCount = edges.edgeCount();
        boolean[] edgeUsed = new boolean[edgeCount];
        while (true) {
            int bestEdge = -1;
            double bestScore = PROJECTED_REDECLARE_MIN_SCORE;
            for (int edgeIndex = 0; edgeIndex < edgeCount; edgeIndex++) {
                if (edgeUsed[edgeIndex]) {
                    continue;
                }
                int attackerIndex = edges.attackerIndex(edgeIndex);
                int defenderIndex = edges.defenderIndex(edgeIndex);
                if (remainingAttackerSlots[attackerIndex] <= 0
                        || remainingDefenderSlots[defenderIndex] <= 0) {
                    continue;
                }
                int defenderNationIndex = state.attackerCount + defenderIndex;
                if (warState.hasActivePair(attackerIndex, defenderNationIndex)) {
                    continue;
                }
                if (!canRedeclareProjected(state, attackerIndex, defenderNationIndex)) {
                    continue;
                }
                double score = projectedRedeclareScore(state, attackerIndex, defenderIndex,
                        defenderNationIndex, remainingAttackerSlots[attackerIndex],
                        edges.scalarScore(edgeIndex));
                if (score > bestScore) {
                    bestScore = score;
                    bestEdge = edgeIndex;
                }
            }
            if (bestEdge < 0) {
                return;
            }
            edgeUsed[bestEdge] = true;
            int attackerIndex = edges.attackerIndex(bestEdge);
            int defenderIndex = edges.defenderIndex(bestEdge);
            int defenderNationIndex = state.attackerCount + defenderIndex;
            remainingAttackerSlots[attackerIndex]--;
            remainingDefenderSlots[defenderIndex]--;
            warState.addWar(attackerIndex, defenderNationIndex, turn, warTypeFromEdge(edges, bestEdge));
        }
    }

    private static boolean hasAnyAvailable(int[] slots) {
        for (int slot : slots) {
            if (slot > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean canRedeclareProjected(ProjectionState state, int attackerNationIndex, int targetNationIndex) {
        if (state.nationIds[attackerNationIndex] == state.nationIds[targetNationIndex]) {
            return false;
        }
        double attackerScore = state.score(attackerNationIndex);
        double targetScore = state.score(targetNationIndex);
        double minScore = attackerScore * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = attackerScore * PW.WAR_RANGE_MAX_MODIFIER;
        return targetScore >= minScore && targetScore <= maxScore;
    }

    private double projectedRedeclareScore(
            ProjectionState state,
            int attackerIndex,
            int defenderIndex,
            int defenderNationIndex,
            int remainingAttackerSlots,
            double baseEdgeScore
    ) {
        double attackerStrength = state.combatStrength(attackerIndex);
        double defenderStrength = state.combatStrength(defenderNationIndex);
        if (!(attackerStrength > 0d) || !(defenderStrength > 0d)) {
            return 0d;
        }
        double activity = Math.max(0d, Math.min(1d, scenario.attackerActivityWeight(attackerIndex)));
        double strengthRatio = attackerStrength / Math.max(1d, defenderStrength);
        double slotPressure = 1d / Math.max(1, remainingAttackerSlots);
        return Math.max(0d, baseEdgeScore) * activity * Math.min(2.0d, strengthRatio) * slotPressure;
    }

    private int[] projectedCounterOffensiveSlots(ProjectionState state, int[] activeOffensiveWarsByNation) {
        int[] slots = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < slots.length; defenderIndex++) {
            int nationIndex = state.attackerCount + defenderIndex;
            if (state.beigeTurns[nationIndex] > 0 || state.combatStrength(nationIndex) <= 0d) {
                continue;
            }
            slots[defenderIndex] = Math.max(0,
                    scenario.defender(defenderIndex).rawFreeOff() - activeOffensiveWarsByNation[nationIndex]);
        }
        return slots;
    }

    private int[] projectedCounterTargetDefensiveSlots(ProjectionState state, int[] activeDefensiveWarsByNation, int[] attackerCounts) {
        int[] slots = new int[scenario.attackerCount()];
        for (int attackerIndex = 0; attackerIndex < slots.length; attackerIndex++) {
            if (attackerCounts[attackerIndex] <= 0 || state.beigeTurns[attackerIndex] > 0 || state.combatStrength(attackerIndex) <= 0d) {
                continue;
            }
            slots[attackerIndex] = Math.max(0,
                    scenario.attacker(attackerIndex).rawFreeDef() - activeDefensiveWarsByNation[attackerIndex]);
        }
        return slots;
    }

    private double projectedCounterScore(
            ProjectionState state,
            int counterNationIndex,
            int targetNationIndex,
            int defenderIndex,
            int targetFreeDefensiveSlots
    ) {
        if (state.beigeTurns[counterNationIndex] > 0 || state.beigeTurns[targetNationIndex] > 0) {
            return 0d;
        }
        if (!canCounterProjected(state, counterNationIndex, targetNationIndex)) {
            return 0d;
        }
        double counterStrength = state.combatStrength(counterNationIndex);
        double targetStrength = state.combatStrength(targetNationIndex);
        if (!(counterStrength > 0d) || !(targetStrength > 0d)) {
            return 0d;
        }
        double activity = Math.max(0d, Math.min(1d, scenario.defenderActivityWeight(defenderIndex)));
        double strengthRatio = counterStrength / Math.max(1d, targetStrength);
        double targetValue = Math.max(50d, state.strategicValue(targetNationIndex) * 0.10d);
        return activity * targetValue * Math.min(2.0d, strengthRatio) / Math.max(1, targetFreeDefensiveSlots);
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
                warState.deactivateWar(edgeIndex);
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
        AttackType bestAttackType = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        CombatKernel.NationState attacker = state.nationViews[warState.attackerNationIndex[context.warIndex()]];
        int mapsAvailable = warState.attackerMaps[context.warIndex()];
        for (AttackType attackType : ADAPTIVE_ATTACK_TYPES) {
            int mapCost = attackType.getMapUsed();
            if (mapCost <= 0 || mapCost > mapsAvailable || !CombatKernel.canUseAttackType(attacker, attackType)) {
                continue;
            }
            CombatKernel.resolveInto(context, attackType, ResolutionMode.MOST_LIKELY, scratch, result);
            double score = attackResultScore(state, warState, context, result);
            if (bestAttackType == null
                    || score > bestScore
                    || (score == bestScore && attackType.ordinal() < bestAttackType.ordinal())) {
                bestAttackType = attackType;
                bestScore = score;
            }
        }
        return bestScore > 0d ? bestAttackType : null;
    }

    private static double attackResultScore(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            MutableAttackResult result
    ) {
        int attackerNationIndex = warState.attackerNationIndex[context.warIndex()];
        int defenderNationIndex = warState.defenderNationIndex[context.warIndex()];
        double defenderUnitDamage = state.unitLossValue(defenderNationIndex, result.defenderLosses());
        double attackerUnitDamage = state.unitLossValue(attackerNationIndex, result.attackerLosses());
        double controlScore = 0d;
        ControlFlagDelta controlDelta = result.controlDelta();
        controlScore += Math.max(0, controlDelta.groundControl()) * 18d;
        controlScore += Math.max(0, controlDelta.airSuperiority()) * 20d;
        controlScore += Math.max(0, controlDelta.blockade()) * 14d;
        controlScore += controlDelta.clearGroundControl() ? 8d : 0d;
        controlScore += controlDelta.clearAirSuperiority() ? 8d : 0d;
        controlScore += controlDelta.clearBlockade() ? 6d : 0d;
        return defenderUnitDamage
                + (-result.defenderResistanceDelta() * 5d)
                + controlScore
                - attackerUnitDamage * 0.35d;
    }

    private void resolveAttack(
            ProjectionState state,
            DenseWarState warState,
            DenseWarContext context,
            AttackType attackType,
            AttackScratch scratch,
            MutableAttackResult result
    ) {
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
        warState.deactivateWar(edgeIndex);
        int winnerIndex = defenderLost ? warState.attackerNationIndex[edgeIndex] : warState.defenderNationIndex[edgeIndex];
        int loserIndex = defenderLost ? warState.defenderNationIndex[edgeIndex] : warState.attackerNationIndex[edgeIndex];
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
            clearControlIfUnable(state, warState, edgeIndex, warState.groundControlOwner, MilitaryUnit.SOLDIER, MilitaryUnit.TANK);
            clearControlIfUnable(state, warState, edgeIndex, warState.airSuperiorityOwner, MilitaryUnit.AIRCRAFT, null);
            clearControlIfUnable(state, warState, edgeIndex, warState.blockadeOwner, MilitaryUnit.SHIP, null);
        }
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

    private static double[] counterPressures(
            CompiledScenario scenario,
            double[] attackerCombatStrengths,
            double[] defenderCombatStrengths
    ) {
        double[] pressures = new double[scenario.attackerCount()];
        for (int defenderIndex = 0; defenderIndex < scenario.defenderCount(); defenderIndex++) {
            DBNationSnapshot counterDeclarer = scenario.defender(defenderIndex);
            int counterSlots = Math.max(0, counterDeclarer.rawFreeOff());
            if (counterSlots == 0 || defenderCombatStrengths[defenderIndex] <= 0d) {
                continue;
            }
            double[] bestScores = new double[Math.min(counterSlots, Math.max(1, scenario.attackerCount()))];
            int[] bestAttackerIndexes = new int[bestScores.length];
            Arrays.fill(bestAttackerIndexes, -1);
            for (int attackerIndex = 0; attackerIndex < scenario.attackerCount(); attackerIndex++) {
                if (!canCounter(counterDeclarer, scenario.attacker(attackerIndex))) {
                    continue;
                }
                double pressure = counterPressure(
                        counterDeclarer,
                        scenario.attacker(attackerIndex),
                        defenderCombatStrengths[defenderIndex],
                        attackerCombatStrengths[attackerIndex],
                        scenario.defenderActivityWeight(defenderIndex)
                );
                insertBest(bestScores, bestAttackerIndexes, pressure, attackerIndex);
            }
            for (int index = 0; index < bestScores.length; index++) {
                double pressure = bestScores[index];
                if (!(pressure > 0d)) {
                    continue;
                }
                int bestAttackerIndex = bestAttackerIndexes[index];
                if (bestAttackerIndex >= 0) {
                    pressures[bestAttackerIndex] += pressure;
                }
            }
        }
        return pressures;
    }

    private static void insertBest(double[] bestScores, int[] bestAttackerIndexes, double pressure, int attackerIndex) {
        if (!(pressure > 0d)) {
            return;
        }
        for (int index = 0; index < bestScores.length; index++) {
            if (pressure > bestScores[index]) {
                for (int shift = bestScores.length - 1; shift > index; shift--) {
                    bestScores[shift] = bestScores[shift - 1];
                    bestAttackerIndexes[shift] = bestAttackerIndexes[shift - 1];
                }
                bestScores[index] = pressure;
                bestAttackerIndexes[index] = attackerIndex;
                return;
            }
        }
    }

    private static double counterPressure(
            DBNationSnapshot counterDeclarer,
            DBNationSnapshot target,
            double counterStrength,
            double targetStrength,
            float activityWeight
    ) {
        double targetDefSlots = Math.max(1, WarSlotRules.freeDefensiveSlots(target.currentDefensiveWars()));
        double strengthRatio = counterStrength / Math.max(1d, targetStrength);
        double scoreValue = Math.max(50d, strategicAssetValue(target) * 0.10d);
        double activity = Math.max(0d, Math.min(1d, activityWeight));
        return activity * scoreValue * Math.min(2.0d, strengthRatio) / targetDefSlots;
    }

    private static boolean canCounter(DBNationSnapshot counterDeclarer, DBNationSnapshot target) {
        if (counterDeclarer.nationId() == target.nationId()) {
            return false;
        }
        double minScore = counterDeclarer.score() * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = counterDeclarer.score() * PW.WAR_RANGE_MAX_MODIFIER;
        return target.score() >= minScore && target.score() <= maxScore;
    }

    private static double[] counterPenaltyScales(
            double[] attackerInitialScores,
            double[] attackerProjectedBuyScore,
            double[] attackerCombatStrengths,
            double[] attackerCounterPressures
    ) {
        double[] scales = new double[attackerInitialScores.length];
        for (int attackerIndex = 0; attackerIndex < scales.length; attackerIndex++) {
                double resilience = Math.sqrt(Math.max(1d, attackerCombatStrengths[attackerIndex]))
                    + Math.sqrt(Math.max(0d, attackerProjectedBuyScore[attackerIndex]));
            double scoreValue = Math.max(50d, attackerInitialScores[attackerIndex] * 0.10d);
            double normalizedPressure = attackerCounterPressures[attackerIndex] / Math.max(1d, resilience / 20d);
            scales[attackerIndex] = Math.min(scoreValue, normalizedPressure);
        }
        return scales;
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
        StrategicAssetValue.StrategicRelevance relevance = attackerSide
                ? StrategicAssetValue.relevanceForWarRange(
                        snapshot.cities(),
                        snapshot.score(),
                        snapshot.activeOpponentNationIds().size(),
                        scenario.defenderCount(),
                        index -> scenario.defender(index).score()
                )
                : StrategicAssetValue.relevanceForWarRange(
                        snapshot.cities(),
                        snapshot.score(),
                        snapshot.activeOpponentNationIds().size(),
                        scenario.attackerCount(),
                        index -> scenario.attacker(index).score()
                );
        return StrategicAssetValue.contextualMilitaryValue(
                snapshot::unit,
                snapshot::pendingBuysNextTurn,
                snapshot::unitsBoughtToday,
                snapshot::dailyBuyCap,
                snapshot.researchBits(),
                activeWarContext(snapshot),
                relevance
        ).totalValue();
    }

    private static double strategicAssetValue(DBNationSnapshot snapshot) {
        StrategicAssetValue.StrategicRelevance relevance = new StrategicAssetValue.StrategicRelevance(
                snapshot.cities(),
                0,
                0,
                snapshot.activeOpponentNationIds().size()
        );
        return StrategicAssetValue.contextualMilitaryValue(
                snapshot::unit,
                snapshot::pendingBuysNextTurn,
                snapshot::unitsBoughtToday,
                snapshot::dailyBuyCap,
                snapshot.researchBits(),
                activeWarContext(snapshot),
                relevance
        ).totalValue();
    }

    private static StrategicAssetValue.ActiveWarContext activeWarContext(DBNationSnapshot snapshot) {
        return StrategicAssetValue.ActiveWarContext.fromSlots(
                snapshot.currentOffensiveWars(),
                snapshot.maxOff(),
                snapshot.currentDefensiveWars(),
                snapshot.activeOpponentNationIds().size()
        );
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
            double remaining = amount;
            while (remaining > 0d) {
                int maxCityIndex = 0;
                for (int cityIndex = 1; cityIndex < cityCount; cityIndex++) {
                    if (cityInfraFlat[cityBase + cityIndex] > cityInfraFlat[cityBase + maxCityIndex]) {
                        maxCityIndex = cityIndex;
                    }
                }
                int globalCityIndex = cityBase + maxCityIndex;
                double current = cityInfraFlat[globalCityIndex];
                if (current <= 0d) {
                    break;
                }
                double removed = Math.min(current, remaining);
                cityInfraFlat[globalCityIndex] = current - removed;
                remaining -= removed;
            }
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

        double strategicValue(int nationIndex, DenseWarState warState) {
            StrategicAssetValue.StrategicRelevance relevance = strategicRelevance(nationIndex);
            StrategicAssetValue.ActiveWarContext activeWarContext = activeWarContext(nationIndex, warState);
            boolean hasActiveWars = baseHasActiveWars[nationIndex] || activeWarContext.hasActiveWars();
            return StrategicAssetValue.contextualMilitaryValue(
                    unit -> unitsFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                    unit -> pendingBuysFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                    unit -> unitsBoughtTodayFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                    unit -> dailyBuyCap(nationIndex, unit, hasActiveWars),
                    researchBits[nationIndex],
                    activeWarContext,
                    relevance
            ).totalValue();
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

        double unitLossValue(int nationIndex, int[] losses) {
            StrategicAssetValue.StrategicRelevance relevance = strategicRelevance(nationIndex);
            return StrategicAssetValue.contextualLossValue(
                    unit -> unitsFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                    unit -> losses[unit.ordinal()],
                    unit -> unitsBoughtTodayFlat[unitBaseOffsets[nationIndex] + unit.ordinal()],
                    unit -> dailyBuyCap(nationIndex, unit, baseHasActiveWars[nationIndex]),
                    researchBits[nationIndex],
                    baseHasActiveWars[nationIndex],
                    relevance
            );
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
                ownControls += denseControlCount(
                        ownOwnerCode,
                        warState.groundControlOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
                enemyControls += denseControlCount(
                        enemyOwnerCode,
                        warState.groundControlOwner[warIndex],
                        warState.airSuperiorityOwner[warIndex],
                        warState.blockadeOwner[warIndex]
                );
            }
            if (activeOpponents == 0) {
                return StrategicAssetValue.ActiveWarContext.basic(baseHasActiveWars[nationIndex]);
            }
            double slotPressure = Math.max(offensiveWars / 3.0d, defensiveWars / 3.0d);
            return StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
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

        private static int denseControlCount(int ownerCode, int groundOwner, int airOwner, int blockadeOwner) {
            int count = 0;
            if (groundOwner == ownerCode) {
                count++;
            }
            if (airOwner == ownerCode) {
                count++;
            }
            if (blockadeOwner == ownerCode) {
                count++;
            }
            return count;
        }

        double[] attackerStrategicValues(DenseWarState warState) {
            double[] values = new double[attackerCount];
            for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
                values[attackerIndex] = strategicValue(attackerIndex, warState);
            }
            return values;
        }

        double[] defenderStrategicValues(DenseWarState warState) {
            double[] values = new double[defenderCount];
            for (int defenderIndex = 0; defenderIndex < defenderCount; defenderIndex++) {
                values[defenderIndex] = strategicValue(attackerCount + defenderIndex, warState);
            }
            return values;
        }

        double futureWarLeverage(
                int attackerNationIndex,
                int defenderNationIndex,
                boolean attackerHasAirControl,
                boolean defenderHasAirControl,
                int defenderResistance
        ) {
            return OpeningMetricSummary.futureWarLeverage(
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
                    unit(defenderNationIndex, MilitaryUnit.SHIP),
                    defenderResistance
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

        double[] attackerScores() {
            return Arrays.copyOf(scores, attackerCount);
        }

        double[] defenderScores() {
            return Arrays.copyOfRange(scores, attackerCount, attackerCount + defenderCount);
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
        private int[] groundControlOwner;
        private int[] airSuperiorityOwner;
        private int[] blockadeOwner;
        private final boolean[] activePairs;
        private final int nationCount;
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
                int[] groundControlOwner,
                int[] airSuperiorityOwner,
                int[] blockadeOwner,
                boolean[] activePairs,
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
            this.groundControlOwner = groundControlOwner;
            this.airSuperiorityOwner = airSuperiorityOwner;
            this.blockadeOwner = blockadeOwner;
            this.activePairs = activePairs;
            this.nationCount = nationCount;
            this.warCount = warCount;
        }

        static DenseWarState from(CandidateEdgeTable edges, boolean[] edgeAssigned, ProjectionState state, int counterCapacity) {
            int edgeCount = edges.edgeCount();
            int capacity = Math.max(edgeCount, edgeCount + Math.max(0, counterCapacity));
            int[] attackerNationIndex = new int[capacity];
            int[] defenderNationIndex = new int[capacity];
            boolean[] active = new boolean[capacity];
            int[] attackerMaps = new int[capacity];
            int[] defenderMaps = new int[capacity];
            int[] startTurn = new int[capacity];
            int[] attackerResistance = new int[capacity];
            int[] defenderResistance = new int[capacity];
            WarType[] warTypes = new WarType[capacity];
            int[] groundControlOwner = new int[capacity];
            int[] airSuperiorityOwner = new int[capacity];
            int[] blockadeOwner = new int[capacity];
            int nationCount = state.nationIds.length;
            boolean[] activePairs = new boolean[nationCount * nationCount];
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
            }
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
                    groundControlOwner,
                    airSuperiorityOwner,
                    blockadeOwner,
                    activePairs,
                    nationCount,
                    edgeCount
            );
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
            attackerMaps[index] = INITIAL_WAR_MAPS;
            defenderMaps[index] = INITIAL_WAR_MAPS;
            startTurn[index] = turn;
            attackerResistance[index] = INITIAL_RESISTANCE;
            defenderResistance[index] = INITIAL_RESISTANCE;
            warTypes[index] = warType == null ? WarType.ORD : warType;
            groundControlOwner[index] = OWNER_NONE;
            airSuperiorityOwner[index] = OWNER_NONE;
            blockadeOwner[index] = OWNER_NONE;
            return index;
        }

        void deactivateWar(int warIndex) {
            if (!active[warIndex]) {
                return;
            }
            active[warIndex] = false;
            activePairs[pairIndex(attackerNationIndex[warIndex], defenderNationIndex[warIndex], nationCount)] = false;
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
            groundControlOwner = Arrays.copyOf(groundControlOwner, nextCapacity);
            airSuperiorityOwner = Arrays.copyOf(airSuperiorityOwner, nextCapacity);
            blockadeOwner = Arrays.copyOf(blockadeOwner, nextCapacity);
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

        private static int pairIndex(int attackerIndex, int defenderIndex, int nationCount) {
            return attackerIndex * nationCount + defenderIndex;
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
        public boolean attackerHasGroundControl(int warIndex) {
            return groundControlOwner[warIndex] == OWNER_ATTACKER;
        }

        @Override
        public boolean defenderHasGroundControl(int warIndex) {
            return groundControlOwner[warIndex] == OWNER_DEFENDER;
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
        public Integer groundControlNationId() {
            return controlNationId(warState.groundControlOwner[warIndex]);
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
        public void setGroundControlNationId(Integer nationId) {
            warState.groundControlOwner[warIndex] = ownerCode(nationId);
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
        private final double[] attackerScores;
        private final double[] defenderScores;
        private final double[] attackerValues;
        private final double[] defenderValues;
        private final boolean[] edgeActive;
        private final int[] warAttackerNationIndex;
        private final int[] warDefenderNationIndex;
        private final int[] groundControlOwner;
        private final int[] airSuperiorityOwner;
        private final int[] blockadeOwner;
        private final int[] attackerResistance;
        private final int[] defenderResistance;
        private final boolean[] edgeMetricPresent;
        private final double[] edgeTargetPressures;
        private final double[] edgeFutureWarLeverages;

        private ProjectionView(
                double[] attackerScores,
                double[] defenderScores,
                double[] attackerValues,
                double[] defenderValues,
                boolean[] edgeActive,
                int[] warAttackerNationIndex,
                int[] warDefenderNationIndex,
                int[] groundControlOwner,
                int[] airSuperiorityOwner,
                int[] blockadeOwner,
                int[] attackerResistance,
                int[] defenderResistance,
                boolean[] edgeMetricPresent,
                double[] edgeTargetPressures,
                double[] edgeFutureWarLeverages
        ) {
            this.attackerScores = attackerScores;
            this.defenderScores = defenderScores;
            this.attackerValues = attackerValues;
            this.defenderValues = defenderValues;
            this.edgeActive = edgeActive;
            this.warAttackerNationIndex = warAttackerNationIndex;
            this.warDefenderNationIndex = warDefenderNationIndex;
            this.groundControlOwner = groundControlOwner;
            this.airSuperiorityOwner = airSuperiorityOwner;
            this.blockadeOwner = blockadeOwner;
            this.attackerResistance = attackerResistance;
            this.defenderResistance = defenderResistance;
            this.edgeMetricPresent = edgeMetricPresent;
            this.edgeTargetPressures = edgeTargetPressures;
            this.edgeFutureWarLeverages = edgeFutureWarLeverages;
        }

        @Override
        public void forEachNation(NationScoreConsumer consumer) {
            for (int attackerIndex = 0; attackerIndex < attackerScores.length; attackerIndex++) {
                consumer.accept(scenario.attackerNationId(attackerIndex), scenario.attacker(attackerIndex).teamId(), attackerScores[attackerIndex]);
            }
            for (int defenderIndex = 0; defenderIndex < defenderScores.length; defenderIndex++) {
                consumer.accept(scenario.defenderNationId(defenderIndex), scenario.defender(defenderIndex).teamId(), defenderScores[defenderIndex]);
            }
        }

        @Override
        public void forEachNationStrategicValue(NationValueConsumer consumer) {
            for (int attackerIndex = 0; attackerIndex < attackerValues.length; attackerIndex++) {
                consumer.accept(scenario.attackerNationId(attackerIndex), scenario.attacker(attackerIndex).teamId(), attackerValues[attackerIndex]);
            }
            for (int defenderIndex = 0; defenderIndex < defenderValues.length; defenderIndex++) {
                consumer.accept(scenario.defenderNationId(defenderIndex), scenario.defender(defenderIndex).teamId(), defenderValues[defenderIndex]);
            }
        }

        @Override
        public void forEachWarControl(WarControlConsumer consumer) {
            for (int edgeIndex = 0; edgeIndex < edgeActive.length; edgeIndex++) {
                if (!edgeMetricPresent[edgeIndex]) {
                    continue;
                }
                int warAttackerTeamId = stateTeamId(warAttackerNationIndex[edgeIndex]);
                int warDefenderTeamId = stateTeamId(warDefenderNationIndex[edgeIndex]);
                consumer.accept(
                        warAttackerTeamId,
                        warDefenderTeamId,
                        controlTeamId(edgeIndex, groundControlOwner[edgeIndex]),
                        controlTeamId(edgeIndex, airSuperiorityOwner[edgeIndex]),
                        controlTeamId(edgeIndex, blockadeOwner[edgeIndex]),
                        attackerResistance[edgeIndex],
                        defenderResistance[edgeIndex]
                );
            }
        }

        @Override
        public void forEachActiveWarMetric(ActiveWarMetricConsumer consumer) {
            for (int edgeIndex = 0; edgeIndex < edgeActive.length; edgeIndex++) {
                if (!edgeActive[edgeIndex]) {
                    continue;
                }
                consumer.accept(
                        stateTeamId(warAttackerNationIndex[edgeIndex]),
                        stateTeamId(warDefenderNationIndex[edgeIndex]),
                        edgeTargetPressures[edgeIndex],
                        edgeFutureWarLeverages[edgeIndex]
                );
            }
        }

        private int controlTeamId(int edgeIndex, int ownerCode) {
            return switch (ownerCode) {
                case DenseWarState.OWNER_ATTACKER -> stateTeamId(warAttackerNationIndex[edgeIndex]);
                case DenseWarState.OWNER_DEFENDER -> stateTeamId(warDefenderNationIndex[edgeIndex]);
                default -> Integer.MIN_VALUE;
            };
        }

        private int stateTeamId(int nationIndex) {
            return nationIndex < scenario.attackerCount()
                    ? scenario.attacker(nationIndex).teamId()
                    : scenario.defender(nationIndex - scenario.attackerCount()).teamId();
        }

        int horizonTurns() {
            return horizonTurns;
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

    record ProjectedEvaluation(
            double objectiveScore,
            int[] realizedCounterIncidence
    ) {
    }
}
