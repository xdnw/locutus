package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.OverrideSet.ActiveOverride;
import link.locutus.discord.sim.planners.AdHocPlan;
import link.locutus.discord.sim.planners.AdHocSimulationOptions;
import link.locutus.discord.sim.planners.AdHocTargetPlanner;
import link.locutus.discord.sim.planners.AdHocTargetRecommendation;
import link.locutus.discord.sim.planners.AvailabilityWindow;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzFixedEdge;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.ScheduledAttacker;
import link.locutus.discord.sim.planners.SnapshotActivityProvider;
import link.locutus.discord.sim.planners.ScheduledTargetPlan;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.planners.ScheduledTargetPlanner;
import link.locutus.discord.sim.planners.ScoreSummary;
import link.locutus.discord.sim.planners.PlannerReplayProjector;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.sim.planners.providers.CompositeBlitzActivityModel;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.BlitzAssignedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzAssignedWarSource;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzExistingWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzLegalEdge;
import link.locutus.discord.web.commands.binding.value_types.BlitzNationRow;
import link.locutus.discord.web.commands.binding.value_types.BlitzObjectiveSummary;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayConcludedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayDeclaredWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayDelta;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayFrame;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;
import link.locutus.discord.web.commands.binding.value_types.BlitzNationReplayState;
import link.locutus.discord.web.commands.binding.value_types.BlitzWarReplayState;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocPlan;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocTarget;
import link.locutus.discord.web.commands.binding.value_types.WebTarget;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.battle.BlitzDraftNation;
import link.locutus.discord.util.battle.BlitzValidator;
import link.locutus.discord.util.battle.BlitzWarning;
import link.locutus.discord.util.battle.BlitzWarningCode;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SimEndpoints {

    @Command(desc = "Run the blitz planner on a fixed attacker and defender set", viewable = true)
    @ReturnType(value = BlitzAssignment.class, cache = CacheType.SessionStorage, duration = 30)
    public BlitzAssignment simBlitz(
            @Default("*") Set<DBNation> attackers,
            @Default("*") Set<DBNation> defenders,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attackers == null || attackers.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one attacker");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }
        int currentTurn = currentWeekTurnUtc();
        SnapshotActivityProvider activityProvider = activityProviderFor(attackers, defenders, currentTurn, stochasticSeed);
        List<DBNationSnapshot> attackerSnapshots = snapshots(attackers, false);
        List<DBNationSnapshot> defenderSnapshots = snapshots(defenders, false);
        return assignBlitz(
                attackerSnapshots,
                defenderSnapshots,
                currentTurn,
                List.of(),
                TreatyProvider.NONE,
                activityProvider,
            OverrideSet.EMPTY,
                tuningFor(stochastic, stochasticSamples, stochasticSeed)
        );
    }

    @Command(desc = "Plan, validate, or run blitz assignment", viewable = true)
    @ReturnType(value = BlitzPlanResponse.class, cache = CacheType.SessionStorage, duration = 30)
    public BlitzPlanResponse blitzPlan(ValueStore store, WarDB warDB, BlitzPlanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Please provide a blitz plan request");
        }
        Set<DBNation> attackers = PWBindings.nations(store, null, request.attackers());
        Set<DBNation> defenders = PWBindings.nations(store, null, request.defenders());
        if (request.runAssignment()) {
            return runBlitzPlan(warDB, request, attackers, defenders);
        }
        return previewBlitzPlan(warDB, request, attackers, defenders);
    }

    static BlitzPlanResponse previewBlitzPlan(
            WarDB warDB,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Please provide a blitz plan request");
        }
        if (request.captureTrace()) {
            throw new IllegalArgumentException("Blitz replay trace capture is not available until the replay slice");
        }
        if (request.runAssignment()) {
            throw new IllegalArgumentException("Use runBlitzPlan for runAssignment=true");
        }
        BlitzPlanContext context = buildBlitzPlanContext(warDB, request, attackers, defenders);
        return responseFromContext(context, new BlitzAssignedWar[0], new link.locutus.discord.sim.planners.PlannerDiagnostic[0], null, null);
    }

    static BlitzPlanResponse runBlitzPlan(
            WarDB warDB,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Please provide a blitz plan request");
        }
        if (!request.runAssignment()) {
            throw new IllegalArgumentException("Use previewBlitzPlan for runAssignment=false");
        }
        BlitzPlanContext context = buildBlitzPlanContext(warDB, request, attackers, defenders);
        List<BlitzFixedEdge> fixedEdges = fixedEdges(context.acceptedPlannedWars());
        BlitzRunInput runInput = runInput(context, fixedEdges);
        BlitzAssignment assignment = assignBlitz(
                runInput.declarers(),
                runInput.targets(),
                context.currentTurn(),
                fixedEdges,
                runInput.treatyProvider(),
                context.activityModel().snapshotProvider(),
                context.overrides(),
                SimTuning.defaults().withStochasticSeed(request.stochasticSeed())
        );
        BlitzAssignedWar[] assignedWars = assignedWars(assignment, context.acceptedPlannedWars());
        BlitzObjectiveSummary objective = objectiveSummary(assignment.objectiveSummary());
        BlitzReplayTrace trace = request.captureTrace()
            ? replayTrace(context, assignment.assignment())
            : null;
        return responseFromContext(
                context,
                assignedWars,
                assignment.diagnostics().toArray(link.locutus.discord.sim.planners.PlannerDiagnostic[]::new),
            objective,
            trace
        );
    }

    private static BlitzPlanContext buildBlitzPlanContext(
            WarDB warDB,
            BlitzPlanRequest request,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders
    ) {
        if (request.horizonTurns() < 1 || request.horizonTurns() > 12) {
            throw new IllegalArgumentException("horizonTurns must be between 1 and 12");
        }
        BlitzSideMode sideMode = enumAt(BlitzSideMode.values(), request.sideModeOrdinal(), "sideModeOrdinal");
        enumAt(BlitzRebuyMode.values(), request.rebuyModeOrdinal(), "rebuyModeOrdinal");
        if (attackers.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one attacker");
        }
        if (defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }

        Map<Integer, DBNation> attackerById = byNationId(attackers);
        Map<Integer, DBNation> defenderById = byNationId(defenders);
        Set<Integer> overlap = new LinkedHashSet<>(attackerById.keySet());
        overlap.retainAll(defenderById.keySet());
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Nations cannot be on both blitz sides: " + overlap);
        }

        Map<Integer, BlitzDraftEdit> editsByNationId = editsByNationId(request, attackerById, defenderById);
        OverrideSet overrides = plannerOverrides(editsByNationId);
        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.addAll(attackers);
        allNations.addAll(defenders);
        Map<Integer, Map<MilitaryUnit, Integer>> currentBuys = switch (BlitzRebuyMode.values()[request.rebuyModeOrdinal()]) {
            case CURRENT_BUYS -> Locutus.imp().getNationDB().getSimUnitBuysToday(allNations);
            case FULL_REBUYS -> Map.of();
            case NO_REBUYS -> noRebuyUnitBuys(allNations, request.assume5553Buildings());
        };
        List<DBNationSnapshot> snapshots = draftSnapshots(allNations, editsByNationId, currentBuys);
        Map<Integer, DBNationSnapshot> snapshotsByNationId = snapshotsByNationId(snapshots);
        List<DBNationSnapshot> attackerSnapshots = snapshotsFor(attackers, snapshotsByNationId);
        List<DBNationSnapshot> defenderSnapshots = snapshotsFor(defenders, snapshotsByNationId);

        List<BlitzWarning> warnings = new ArrayList<>();
        List<BlitzLegalEdge> legalEdges = new ArrayList<>();
        Set<String> activePairs = request.includeExistingWars() ? activePairKeys(warDB, allNations) : Set.of();
        buildLegalEdges(sideMode, attackers, defenders, activePairs, legalEdges, warnings);
        List<BlitzPlannedWar> acceptedPlannedWars = validatePlannedWars(request, sideMode, attackerById, defenderById, legalEdges, warnings);

        BlitzExistingWar[] existingWars = request.includeExistingWars()
                ? existingWars(warDB, allNations)
                : new BlitzExistingWar[0];
        int currentTurn = request.currentTurnOverride() == null ? currentWeekTurnUtc() : request.currentTurnOverride();
        CompositeBlitzActivityModel activityModel = CompositeBlitzActivityModel.build(
            allNations,
            warDB,
            currentTurn,
            request.stochasticSeed()
        );

        return new BlitzPlanContext(
                request,
                currentTurn,
                request.horizonTurns(),
                attackers,
                defenders,
                attackerSnapshots,
                defenderSnapshots,
                nationIds(attackers),
                nationIds(defenders),
                overrides,
                activityModel,
                nationRows(allNations, editsByNationId, currentBuys, request.assume5553Buildings(), activityModel),
                existingWars,
                legalEdges.toArray(BlitzLegalEdge[]::new),
                warnings.toArray(BlitzWarning[]::new),
                acceptedPlannedWars
        );
    }

    private static OverrideSet plannerOverrides(Map<Integer, BlitzDraftEdit> editsByNationId) {
        OverrideSet.Builder builder = OverrideSet.builder();
        for (BlitzDraftEdit edit : editsByNationId.values()) {
            if (edit.forceActive() != null) {
                builder.active(edit.nationId(), edit.forceActive() ? ActiveOverride.TRUE : ActiveOverride.FALSE);
            }
        }
        return builder.build();
    }

    private static BlitzPlanResponse responseFromContext(
            BlitzPlanContext context,
            BlitzAssignedWar[] assignments,
            link.locutus.discord.sim.planners.PlannerDiagnostic[] diagnostics,
            BlitzObjectiveSummary objective,
            BlitzReplayTrace trace
    ) {
        return new BlitzPlanResponse(
                context.currentTurn(),
                context.horizonTurns(),
                context.attackerNationIds(),
                context.defenderNationIds(),
                context.nations(),
                context.existingWars(),
                context.legalEdges(),
                assignments,
                context.warnings(),
                diagnostics,
                objective,
                trace
        );
    }

    private static BlitzReplayTrace replayTrace(BlitzPlanContext context, Map<Integer, List<Integer>> assignment) {
        link.locutus.discord.sim.planners.PlannerReplayTrace trace = PlannerReplayProjector.capture(
                SimTuning.defaults().withStochasticSeed(context.request().stochasticSeed()),
                context.overrides(),
                combinedSnapshots(context.attackerSnapshots(), context.defenderSnapshots()),
                assignment,
                context.currentTurn(),
                context.horizonTurns(),
                false
        );
        return new BlitzReplayTrace(
                replayFrame(trace.initialFrame()),
                replayDeltas(trace.deltas()),
                context.warnings()
        );
    }

    private static BlitzReplayFrame replayFrame(link.locutus.discord.sim.planners.PlannerReplayTrace.Frame frame) {
        return new BlitzReplayFrame(frame.currentTurn(), replayNationStates(frame.nations()), replayWarStates(frame.wars()));
    }

    private static BlitzReplayDelta[] replayDeltas(link.locutus.discord.sim.planners.PlannerReplayTrace.Delta[] deltas) {
        BlitzReplayDelta[] result = new BlitzReplayDelta[deltas.length];
        for (int i = 0; i < deltas.length; i++) {
            link.locutus.discord.sim.planners.PlannerReplayTrace.Delta delta = deltas[i];
            result[i] = new BlitzReplayDelta(
                    delta.turn(),
                    replayNationStates(delta.nations()),
                    replayWarStates(delta.wars()),
                    replayDeclaredWars(delta.declaredWars()),
                    replayConcludedWars(delta.concludedWars())
            );
        }
        return result;
    }

    private static BlitzNationReplayState[] replayNationStates(link.locutus.discord.sim.planners.PlannerReplayTrace.NationState[] states) {
        BlitzNationReplayState[] result = new BlitzNationReplayState[states.length];
        for (int i = 0; i < states.length; i++) {
            link.locutus.discord.sim.planners.PlannerReplayTrace.NationState state = states[i];
            result[i] = new BlitzNationReplayState(
                    state.nationId(),
                    state.unitsByMilitaryUnitOrdinal(),
                    state.cityInfra(),
                    state.score(),
                    state.beigeTurns(),
                    state.resources()
            );
        }
        return result;
    }

    private static BlitzWarReplayState[] replayWarStates(link.locutus.discord.sim.planners.PlannerReplayTrace.WarState[] states) {
        BlitzWarReplayState[] result = new BlitzWarReplayState[states.length];
        for (int i = 0; i < states.length; i++) {
            link.locutus.discord.sim.planners.PlannerReplayTrace.WarState state = states[i];
            result[i] = new BlitzWarReplayState(
                    state.declarerNationId(),
                    state.targetNationId(),
                    state.warTypeOrdinal(),
                    state.startTurn(),
                    state.statusOrdinal(),
                    state.attackerMaps(),
                    state.defenderMaps(),
                    state.attackerResistance(),
                    state.defenderResistance(),
                    state.groundControlOwnerOrdinal(),
                    state.airSuperiorityOwnerOrdinal(),
                    state.blockadeOwnerOrdinal(),
                    state.attackerFortified(),
                    state.defenderFortified()
            );
        }
        return result;
    }

    private static BlitzReplayDeclaredWar[] replayDeclaredWars(link.locutus.discord.sim.planners.PlannerReplayTrace.DeclaredWar[] wars) {
        BlitzReplayDeclaredWar[] result = new BlitzReplayDeclaredWar[wars.length];
        for (int i = 0; i < wars.length; i++) {
            link.locutus.discord.sim.planners.PlannerReplayTrace.DeclaredWar war = wars[i];
            result[i] = new BlitzReplayDeclaredWar(
                    war.declarerNationId(),
                    war.targetNationId(),
                    war.warTypeOrdinal(),
                    war.startTurn()
            );
        }
        return result;
    }

    private static BlitzReplayConcludedWar[] replayConcludedWars(link.locutus.discord.sim.planners.PlannerReplayTrace.ConcludedWar[] wars) {
        BlitzReplayConcludedWar[] result = new BlitzReplayConcludedWar[wars.length];
        for (int i = 0; i < wars.length; i++) {
            link.locutus.discord.sim.planners.PlannerReplayTrace.ConcludedWar war = wars[i];
            result[i] = new BlitzReplayConcludedWar(
                    war.declarerNationId(),
                    war.targetNationId(),
                    war.endStatusOrdinal()
            );
        }
        return result;
    }

    private static List<DBNationSnapshot> combinedSnapshots(
            List<DBNationSnapshot> attackers,
            List<DBNationSnapshot> defenders
    ) {
        List<DBNationSnapshot> combined = new ArrayList<>(attackers.size() + defenders.size());
        combined.addAll(attackers);
        combined.addAll(defenders);
        return combined;
    }

    static List<DBNationSnapshot> draftSnapshots(
            Collection<DBNation> nations,
            Map<Integer, BlitzDraftEdit> editsByNationId,
            Map<Integer, Map<MilitaryUnit, Integer>> currentBuys
    ) {
        List<DBNationSnapshot> baseSnapshots = DBNationSnapshot.of(nations, currentBuys);
        List<DBNationSnapshot> snapshots = new ArrayList<>(baseSnapshots.size());
        for (DBNationSnapshot snapshot : baseSnapshots) {
            BlitzDraftEdit edit = editsByNationId.get(snapshot.nationId());
            if (edit == null) {
                snapshots.add(snapshot);
                continue;
            }
            DBNationSnapshot.Builder builder = snapshot.toBuilder();
            if (edit.unitCountsByMilitaryUnitOrdinal() != null) {
                MilitaryUnit[] units = MilitaryUnit.values();
                for (MilitaryUnit unit : units) {
                    builder.unit(unit, edit.unitCountsByMilitaryUnitOrdinal()[unit.ordinal()]);
                }
            }
            if (edit.unitsBoughtTodayByMilitaryUnitOrdinal() != null) {
                for (MilitaryUnit unit : MilitaryUnit.values()) {
                    builder.unitBoughtToday(unit, edit.unitsBoughtTodayByMilitaryUnitOrdinal()[unit.ordinal()]);
                }
            }
            long projectBits = (snapshot.projectBits() | edit.projectBitsSet()) & ~edit.projectBitsClear();
            builder.projectBits(projectBits);
            builder.researchBits((snapshot.researchBits() | edit.researchBitsSet()) & ~edit.researchBitsClear());
            if (edit.policyOrdinal() != null) {
                builder.warPolicy(link.locutus.discord.apiv1.enums.WarPolicy.values()[edit.policyOrdinal()]);
            }
            if (edit.resetHour() != null) {
                builder.resetHourUtc((byte) Math.max(0, Math.min(23, edit.resetHour())));
                builder.resetHourUtcFallback(false);
            }
            if (edit.avgInfraCents() != null) {
                builder.cityInfra(expandAverageInfra(snapshot, edit.avgInfraCents() / 100d));
            }
            snapshots.add(builder.build());
        }
        return snapshots.stream()
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static double[] expandAverageInfra(DBNationSnapshot snapshot, double targetAverageInfra) {
        int cityCount = Math.max(0, snapshot.cities());
        int infraCount = snapshot.cityInfraCount();
        if (cityCount == 0 && infraCount == 0) {
            return new double[0];
        }
        double[] current = snapshot.cityInfra();
        if (infraCount == 0) {
            double[] synthetic = new double[cityCount];
            java.util.Arrays.fill(synthetic, targetAverageInfra);
            return synthetic;
        }
        double currentAverage = 0d;
        for (double infra : current) {
            currentAverage += infra;
        }
        currentAverage /= current.length;
        if (currentAverage <= 0d) {
            java.util.Arrays.fill(current, targetAverageInfra);
            return current;
        }
        double scale = targetAverageInfra / currentAverage;
        for (int i = 0; i < current.length; i++) {
            current[i] = current[i] * scale;
        }
        return current;
    }

    @Command(desc = "Rank ad-hoc targets for one attacker over a short deterministic horizon", viewable = true)
    @ReturnType(value = WebSimAdHocPlan.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSimAdHocPlan simAdhoc(
            @Me @Default DBNation me,
            @Default DBNation attacker,
            @Default("*") Set<DBNation> defenders,
            @Default("8") int numResults,
            @Default("6") int horizonTurns,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attacker == null) {
            attacker = me;
        }
        if (attacker == null) {
            throw new IllegalArgumentException("Please sign in or provide an attacker nation");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }

        int currentTurn = currentWeekTurnUtc();
        SnapshotActivityProvider activityProvider = activityProviderFor(attacker, defenders, currentTurn, stochasticSeed);
        DBNationSnapshot attackerSnapshot = snapshots(java.util.List.of(attacker), false).get(0);
        List<DBNationSnapshot> defenderSnapshots = snapshots(defenders, false);
        AdHocTargetPlanner planner = new AdHocTargetPlanner(
                tuningFor(stochastic, stochasticSamples, stochasticSeed),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                activityProvider
        );
        AdHocPlan plan = planner.rankTargets(
            attackerSnapshot,
            defenderSnapshots,
                horizonTurns,
                numResults,
                currentTurn,
                AdHocSimulationOptions.DEFAULT
        );

        Map<Integer, DBNation> defenderById = new LinkedHashMap<>();
        for (DBNation defender : defenders) {
            defenderById.put(defender.getNation_id(), defender);
        }

        Activity activity = attacker.getActivity(14 * 12);
        double currentChance = activity.loginChance(currentTurn, Math.max(1, horizonTurns), true);
        int suggestedWaitTurns = 0;
        double futureChance = currentChance;
        double currentObjectiveScore = plan.bestObjectiveScore();
        double futureObjectiveScore = currentObjectiveScore;
        for (int delay = 1; delay <= 12; delay++) {
            double candidateChance = activity.loginChance(currentTurn + delay, Math.max(1, horizonTurns), true);
            AdHocPlan delayedPlan = planner.rankTargets(
                    attackerSnapshot,
                    defenderSnapshots,
                    horizonTurns,
                    1,
                    currentTurn + delay,
                    AdHocSimulationOptions.DEFAULT
            );
            double candidateScore = delayedPlan.bestObjectiveScore();
            if (candidateScore > futureObjectiveScore + 1e-9) {
                futureObjectiveScore = candidateScore;
                futureChance = candidateChance;
                suggestedWaitTurns = delay;
            }
        }

        List<WebSimAdHocTarget> targets = new ArrayList<>();
        boolean worthWaiting = suggestedWaitTurns > 0 && futureObjectiveScore > currentObjectiveScore + 1e-9;
        for (AdHocTargetRecommendation recommendation : plan.recommendations()) {
            DBNation defender = defenderById.get(recommendation.defenderId());
            if (defender == null) {
                continue;
            }
            double lootEstimate = defender.lootTotal(null);
            targets.add(new WebSimAdHocTarget(
                    new WebTarget(defender, recommendation.objectiveScore(), lootEstimate, recommendation.counterRisk()),
                    recommendation.objectiveScore(),
                    recommendation.counterRisk(),
                    lootEstimate,
                    recommendation.scoreSummary()
            ));
        }
        return new WebSimAdHocPlan(
                attacker.getNation_id(),
                horizonTurns,
                worthWaiting,
                suggestedWaitTurns,
                currentChance,
                futureChance,
                currentObjectiveScore,
                futureObjectiveScore,
                targets,
                plan.diagnostics(),
                plan.metadata()
        );
    }

    @Command(desc = "Plan rolling scheduled blitz buckets from per-attacker availability windows", viewable = true)
    @ReturnType(value = ScheduledTargetPlan.class, cache = CacheType.SessionStorage, duration = 30)
    public ScheduledTargetPlan simSchedule(
            @Default("*") Set<DBNation> attackers,
            @Default("*") Set<DBNation> defenders,
            @Default("") String availability,
            @Default("6") int bucketSizeTurns,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attackers == null || attackers.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one attacker");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }
        Map<Integer, DBNation> attackerById = new LinkedHashMap<>();
        for (DBNation attacker : attackers) {
            attackerById.put(attacker.getNation_id(), attacker);
        }

        int currentTurn = currentWeekTurnUtc();
        SnapshotActivityProvider activityProvider = activityProviderFor(attackers, defenders, currentTurn, stochasticSeed);

        List<ScheduledAttacker> scheduledAttackers = parseAvailability(attackerById, availability, bucketSizeTurns);
        return new ScheduledTargetPlanner(tuningFor(stochastic, stochasticSamples, stochasticSeed), TreatyProvider.NONE, OverrideSet.EMPTY,
                new DamageObjective(), activityProvider)
            .assign(scheduledAttackers, snapshots(defenders, false), bucketSizeTurns);
    }

    private static <E> E enumAt(E[] values, int ordinal, String fieldName) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + ordinal);
        }
        return values[ordinal];
    }

    private static Map<Integer, DBNation> byNationId(Collection<DBNation> nations) {
        Map<Integer, DBNation> result = new LinkedHashMap<>();
        for (DBNation nation : nations) {
            result.put(nation.getNation_id(), nation);
        }
        return result;
    }

    private static Map<Integer, BlitzDraftEdit> editsByNationId(
            BlitzPlanRequest request,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById
    ) {
        Map<Integer, BlitzDraftEdit> edits = new LinkedHashMap<>();
        for (BlitzDraftEdit edit : request.edits()) {
            if (!attackerById.containsKey(edit.nationId()) && !defenderById.containsKey(edit.nationId())) {
                throw new IllegalArgumentException("Edit references unknown nationId=" + edit.nationId());
            }
            if (edit.policyOrdinal() != null) {
                enumAt(link.locutus.discord.apiv1.enums.WarPolicy.values(), edit.policyOrdinal(), "policyOrdinal");
            }
            validateOrdinalArray(edit.unitCountsByMilitaryUnitOrdinal(), "unitCountsByMilitaryUnitOrdinal", edit.nationId());
            validateOrdinalArray(edit.unitsBoughtTodayByMilitaryUnitOrdinal(), "unitsBoughtTodayByMilitaryUnitOrdinal", edit.nationId());
            edits.put(edit.nationId(), edit);
        }
        return edits;
    }

    private static void validateOrdinalArray(int[] values, String fieldName, int nationId) {
        if (values != null && values.length != MilitaryUnit.values().length) {
            throw new IllegalArgumentException(fieldName + " for nationId=" + nationId + " must have length " + MilitaryUnit.values().length);
        }
    }

    private static int[] nationIds(Collection<DBNation> nations) {
        return nations.stream().mapToInt(DBNation::getNation_id).sorted().toArray();
    }

    private static Map<Integer, Map<MilitaryUnit, Integer>> noRebuyUnitBuys(Collection<DBNation> nations, boolean assume5553Buildings) {
        Map<Integer, Map<MilitaryUnit, Integer>> result = new LinkedHashMap<>();
        for (DBNation nation : nations) {
            Map<MilitaryUnit, Integer> buys = new EnumMap<>(MilitaryUnit.class);
            for (MilitaryUnit unit : MilitaryUnit.values()) {
                buys.put(unit, unitCap(nation, unit, assume5553Buildings));
            }
            result.put(nation.getNation_id(), buys);
        }
        return result;
    }

    private static BlitzNationRow[] nationRows(
            Collection<DBNation> nations,
            Map<Integer, BlitzDraftEdit> editsByNationId,
            Map<Integer, Map<MilitaryUnit, Integer>> currentBuys,
            boolean assume5553Buildings,
            CompositeBlitzActivityModel activityModel
    ) {
        return nations.stream()
                .sorted(Comparator.comparingInt(DBNation::getNation_id))
                .map(nation -> nationRow(
                        nation,
                        editsByNationId.get(nation.getNation_id()),
                        currentBuys.getOrDefault(nation.getNation_id(), Map.of()),
                assume5553Buildings,
                activityModel))
                .toArray(BlitzNationRow[]::new);
    }

    private static BlitzNationRow nationRow(
            DBNation nation,
            BlitzDraftEdit edit,
            Map<MilitaryUnit, Integer> currentBuys,
            boolean assume5553Buildings,
            CompositeBlitzActivityModel activityModel
    ) {
        MilitaryUnit[] units = MilitaryUnit.values();
        int[] unitCounts = new int[units.length];
        int[] unitCaps = new int[units.length];
        int[] unitsBoughtToday = new int[units.length];
        for (MilitaryUnit unit : units) {
            int ordinal = unit.ordinal();
            unitCounts[ordinal] = edit != null && edit.unitCountsByMilitaryUnitOrdinal() != null
                    ? edit.unitCountsByMilitaryUnitOrdinal()[ordinal]
                    : nation.getUnits(unit);
            unitCaps[ordinal] = unitCap(nation, unit, assume5553Buildings && (edit == null || edit.unitCountsByMilitaryUnitOrdinal() == null));
            unitsBoughtToday[ordinal] = edit != null && edit.unitsBoughtTodayByMilitaryUnitOrdinal() != null
                    ? edit.unitsBoughtTodayByMilitaryUnitOrdinal()[ordinal]
                    : currentBuys.getOrDefault(unit, 0);
        }
        int activeOrdinal = edit == null || edit.forceActive() == null
                ? ActiveOverride.AUTO.ordinal()
                : (edit.forceActive() ? ActiveOverride.TRUE : ActiveOverride.FALSE).ordinal();
        long projectBits = Math.max(0, nation.getProjectBitMask());
        int researchBits = nation.getResearchBits(null);
        if (edit != null) {
            projectBits = (projectBits | edit.projectBitsSet()) & ~edit.projectBitsClear();
            researchBits = (researchBits | edit.researchBitsSet()) & ~edit.researchBitsClear();
        }
        return new BlitzNationRow(
                nation.getNation_id(),
                nation.getAlliance_id(),
                nation.getCities(),
                unitCounts,
                unitCaps,
                unitsBoughtToday,
                edit != null && edit.avgInfraCents() != null ? edit.avgInfraCents() : (int) Math.round(nation.getAvg_infra() * 100),
                nation.getBeigeTurns(),
                nation.getVm_turns(),
                nation.active_m(),
                activityModel.activityBasisPoints(nation.getNation_id()),
                nation.getFreeOffensiveSlots(),
                nation.getFreeDefensiveSlots(),
                nation.getMaxOff(),
                edit != null && edit.policyOrdinal() != null ? edit.policyOrdinal() : (nation.getWarPolicy() == null ? -1 : nation.getWarPolicy().ordinal()),
                projectBits,
                researchBits,
                activeOrdinal
        );
    }

    private static int unitCap(DBNation nation, MilitaryUnit unit, boolean assume5553Buildings) {
        try {
            return nation.getUnitCap(unit, !assume5553Buildings);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void buildLegalEdges(
            BlitzSideMode sideMode,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders,
            Set<String> activePairs,
            List<BlitzLegalEdge> legalEdges,
            List<BlitzWarning> warnings
    ) {
        if (sideMode == BlitzSideMode.ATTACKERS_ONLY || sideMode == BlitzSideMode.BOTH) {
            buildLegalEdgesOneWay(attackers, defenders, activePairs, legalEdges, warnings);
        }
        if (sideMode == BlitzSideMode.DEFENDERS_ONLY || sideMode == BlitzSideMode.BOTH) {
            buildLegalEdgesOneWay(defenders, attackers, activePairs, legalEdges, warnings);
        }
    }

    private static void buildLegalEdgesOneWay(
            Collection<DBNation> declarers,
            Collection<DBNation> targets,
            Set<String> activePairs,
            List<BlitzLegalEdge> legalEdges,
            List<BlitzWarning> warnings
    ) {
        BlitzValidator.Rules rules = new BlitzValidator.Rules(0.75, PW.WAR_RANGE_MAX_MODIFIER, true, true, false);
        for (DBNation declarer : declarers) {
            for (DBNation target : targets) {
                List<BlitzWarning> edgeWarnings = new ArrayList<>(BlitzValidator.validatePair(
                        BlitzDraftNation.of(declarer),
                        BlitzDraftNation.of(target),
                        rules
                ));
                if (activePairs.contains(pairKey(declarer.getNation_id(), target.getNation_id()))) {
                    edgeWarnings.add(new BlitzWarning(
                            BlitzWarningCode.ACTIVE_PAIR_CONFLICT,
                            declarer.getNation_id(),
                            target.getNation_id(),
                            0,
                            "Nation pair is already in an active war"
                    ));
                }
                warnings.addAll(edgeWarnings);
                legalEdges.add(new BlitzLegalEdge(
                        declarer.getNation_id(),
                        target.getNation_id(),
                        edgeWarnings.isEmpty(),
                        edgeWarnings.stream().mapToInt(warning -> warning.code().ordinal()).toArray()
                ));
            }
        }
    }

    private static List<BlitzPlannedWar> validatePlannedWars(
            BlitzPlanRequest request,
            BlitzSideMode sideMode,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            List<BlitzLegalEdge> legalEdges,
            List<BlitzWarning> warnings
    ) {
        Map<String, BlitzLegalEdge> legalEdgeByPair = new LinkedHashMap<>();
        for (BlitzLegalEdge edge : legalEdges) {
            legalEdgeByPair.put(pairKey(edge.declarerNationId(), edge.targetNationId()), edge);
        }
        List<BlitzPlannedWar> accepted = new ArrayList<>();
        Set<String> acceptedUnorderedPairs = sideMode == BlitzSideMode.BOTH ? new LinkedHashSet<>() : Set.of();
        for (BlitzPlannedWar plannedWar : request.plannedWars()) {
            enumAt(WarType.values(), plannedWar.warTypeOrdinal(), "warTypeOrdinal");
            boolean forward = attackerById.containsKey(plannedWar.declarerNationId())
                    && defenderById.containsKey(plannedWar.targetNationId())
                    && (sideMode == BlitzSideMode.ATTACKERS_ONLY || sideMode == BlitzSideMode.BOTH);
            boolean reverse = defenderById.containsKey(plannedWar.declarerNationId())
                    && attackerById.containsKey(plannedWar.targetNationId())
                    && (sideMode == BlitzSideMode.DEFENDERS_ONLY || sideMode == BlitzSideMode.BOTH);
            if (!forward && !reverse) {
                warnings.add(new BlitzWarning(
                        BlitzWarningCode.MANUAL_DECLARATION_REJECTED,
                        plannedWar.declarerNationId(),
                        plannedWar.targetNationId(),
                        0,
                        "Manual declaration is not between allowed blitz sides"
                ));
                continue;
            }
            BlitzLegalEdge edge = legalEdgeByPair.get(pairKey(plannedWar.declarerNationId(), plannedWar.targetNationId()));
            if (edge == null || !edge.legal()) {
                warnings.add(new BlitzWarning(
                        BlitzWarningCode.MANUAL_DECLARATION_REJECTED,
                        plannedWar.declarerNationId(),
                        plannedWar.targetNationId(),
                        0,
                        "Manual declaration is not legal in the preview graph"
                ));
                continue;
            }
            if (sideMode == BlitzSideMode.BOTH
                    && !acceptedUnorderedPairs.add(unorderedPairKey(plannedWar.declarerNationId(), plannedWar.targetNationId()))) {
                warnings.add(new BlitzWarning(
                        BlitzWarningCode.MANUAL_DECLARATION_REJECTED,
                        plannedWar.declarerNationId(),
                        plannedWar.targetNationId(),
                        0,
                        "Manual declaration duplicates an existing BOTH-mode nation pair"
                ));
                continue;
            }
            accepted.add(plannedWar);
        }
        return accepted;
    }

    private static Map<Integer, DBNationSnapshot> snapshotsByNationId(Collection<DBNationSnapshot> snapshots) {
        Map<Integer, DBNationSnapshot> result = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            result.put(snapshot.nationId(), snapshot);
        }
        return result;
    }

    private static List<DBNationSnapshot> snapshotsFor(
            Collection<DBNation> nations,
            Map<Integer, DBNationSnapshot> snapshotsByNationId
    ) {
        return nations.stream()
                .map(nation -> snapshotsByNationId.get(nation.getNation_id()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static List<BlitzFixedEdge> fixedEdges(List<BlitzPlannedWar> plannedWars) {
        return plannedWars.stream()
                .map(war -> new BlitzFixedEdge(war.declarerNationId(), war.targetNationId()))
                .toList();
    }

    private static BlitzRunInput runInput(BlitzPlanContext context, List<BlitzFixedEdge> fixedEdges) {
        BlitzSideMode sideMode = enumAt(BlitzSideMode.values(), context.request().sideModeOrdinal(), "sideModeOrdinal");
        return switch (sideMode) {
            case ATTACKERS_ONLY -> new BlitzRunInput(
                    context.attackerSnapshots(),
                    context.defenderSnapshots(),
                    TreatyProvider.NONE
            );
            case DEFENDERS_ONLY -> new BlitzRunInput(
                    context.defenderSnapshots(),
                    context.attackerSnapshots(),
                    TreatyProvider.NONE
            );
            case BOTH -> {
                List<DBNationSnapshot> combined = new ArrayList<>(context.attackerSnapshots().size() + context.defenderSnapshots().size());
                combined.addAll(context.attackerSnapshots());
                combined.addAll(context.defenderSnapshots());
                yield new BlitzRunInput(
                        combined,
                        combined,
                        oppositeSideTreaty(context.attackerNationIds(), context.defenderNationIds(), fixedEdges)
                );
            }
        };
    }

    private static TreatyProvider oppositeSideTreaty(int[] attackerNationIds, int[] defenderNationIds, List<BlitzFixedEdge> fixedEdges) {
        Set<Integer> attackerIds = intSet(attackerNationIds);
        Set<Integer> defenderIds = intSet(defenderNationIds);
        Set<String> fixedPairs = new LinkedHashSet<>();
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            fixedPairs.add(pairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
        }
        return (declarerId, targetId) -> {
            if (fixedPairs.contains(pairKey(declarerId, targetId))) {
                return false;
            }
            if (declarerId == targetId) {
                return true;
            }
            boolean declarerIsAttacker = attackerIds.contains(declarerId);
            boolean targetIsAttacker = attackerIds.contains(targetId);
            boolean declarerIsDefender = defenderIds.contains(declarerId);
            boolean targetIsDefender = defenderIds.contains(targetId);
            return !((declarerIsAttacker && targetIsDefender) || (declarerIsDefender && targetIsAttacker));
        };
    }

    private static Set<Integer> intSet(int[] values) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private static BlitzAssignedWar[] assignedWars(BlitzAssignment assignment, List<BlitzPlannedWar> acceptedPlannedWars) {
        List<BlitzAssignedWar> result = new ArrayList<>();
        Set<String> fixedPairs = new LinkedHashSet<>();
        for (BlitzPlannedWar plannedWar : acceptedPlannedWars) {
            fixedPairs.add(pairKey(plannedWar.declarerNationId(), plannedWar.targetNationId()));
            result.add(new BlitzAssignedWar(
                    plannedWar.declarerNationId(),
                    plannedWar.targetNationId(),
                    plannedWar.warTypeOrdinal(),
                    BlitzAssignedWarSource.USER_PINNED.ordinal()
            ));
        }
        assignment.assignment().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().stream()
                        .sorted()
                        .filter(targetId -> !fixedPairs.contains(pairKey(entry.getKey(), targetId)))
                        .forEach(targetId -> result.add(new BlitzAssignedWar(
                                entry.getKey(),
                                targetId,
                                WarType.ORD.ordinal(),
                                BlitzAssignedWarSource.PLANNER.ordinal()
                        ))));
        return result.toArray(BlitzAssignedWar[]::new);
    }

    private static BlitzObjectiveSummary objectiveSummary(ScoreSummary summary) {
        return new BlitzObjectiveSummary(
                summary.mean(),
                summary.p10(),
                summary.p50(),
                summary.p90(),
                summary.sampleCount()
        );
    }

    private static BlitzAssignment assignBlitz(
            List<DBNationSnapshot> declarers,
            List<DBNationSnapshot> targets,
            int currentTurn,
            List<BlitzFixedEdge> fixedEdges,
            TreatyProvider treatyProvider,
            SnapshotActivityProvider activityProvider,
            OverrideSet overrides,
            SimTuning tuning
    ) {
        return new BlitzPlanner(
                tuning,
                treatyProvider,
            overrides,
                new DamageObjective(),
                activityProvider
        ).assign(declarers, targets, currentTurn, fixedEdges);
    }

    private static Set<String> activePairKeys(WarDB warDB, Collection<DBNation> nations) {
        Set<Integer> nationIds = new LinkedHashSet<>(nationIds(nations).length);
        for (DBNation nation : nations) {
            nationIds.add(nation.getNation_id());
        }
        Set<String> pairs = new LinkedHashSet<>();
        for (DBWar war : warDB.getActiveWars(nationIds::contains, null)) {
            pairs.add(pairKey(war.getAttacker_id(), war.getDefender_id()));
            pairs.add(pairKey(war.getDefender_id(), war.getAttacker_id()));
        }
        return pairs;
    }

    private static String pairKey(int attackerId, int defenderId) {
        return attackerId + ":" + defenderId;
    }

    private static String unorderedPairKey(int nationIdOne, int nationIdTwo) {
        return Math.min(nationIdOne, nationIdTwo) + ":" + Math.max(nationIdOne, nationIdTwo);
    }

    private static BlitzExistingWar[] existingWars(WarDB warDB, Collection<DBNation> nations) {
        Set<Integer> nationIds = new LinkedHashSet<>();
        for (DBNation nation : nations) {
            nationIds.add(nation.getNation_id());
        }
        return warDB.getActiveWars(nationIds::contains, null).stream()
                .sorted(Comparator.comparingInt(DBWar::getWarId))
                .map(SimEndpoints::existingWar)
                .toArray(BlitzExistingWar[]::new);
    }

    private static BlitzExistingWar existingWar(DBWar war) {
        List<link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor> attacks = war.getAttacks3();
        Map.Entry<Integer, Integer> maps = war.getMap(attacks);
        Map.Entry<Integer, Integer> resistance = war.getResistance(attacks);
        return new BlitzExistingWar(
                war.getWarId(),
                war.getAttacker_id(),
                war.getDefender_id(),
                war.getWarType().ordinal(),
                war.getStatus().ordinal(),
                maps.getKey(),
                maps.getValue(),
                resistance.getKey(),
                resistance.getValue(),
                war.getTurnsLeft()
        );
    }

    private static SimTuning tuningFor(boolean stochastic, int stochasticSamples, long stochasticSeed) {
        if (!stochastic) {
            return SimTuning.defaults();
        }
        return SimTuning.defaults()
                .withStateResolutionMode(ResolutionMode.STOCHASTIC)
                .withStochasticSampleCount(stochasticSamples)
                .withStochasticSeed(stochasticSeed);
    }

    private static int currentWeekTurnUtc() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.getHour() / 2 + now.getDayOfWeek().ordinal() * 12;
    }

    private static List<DBNationSnapshot> snapshots(Collection<DBNation> nations, boolean includeCurrentDayUnitBuys) {
        Map<Integer, Map<link.locutus.discord.apiv1.enums.MilitaryUnit, Integer>> unitBuysTodayByNationId =
                includeCurrentDayUnitBuys
                        ? Locutus.imp().getNationDB().getSimUnitBuysToday(nations)
                        : Map.of();
        return DBNationSnapshot.of(nations, unitBuysTodayByNationId).stream()
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static SnapshotActivityProvider activityProviderFor(Collection<DBNation> attackers, Collection<DBNation> defenders, int currentWeekTurn, long tieBreakSeed) {
        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.addAll(attackers);
        allNations.addAll(defenders);
        return activityProviderFor(allNations, currentWeekTurn, tieBreakSeed);
    }

    private static SnapshotActivityProvider activityProviderFor(DBNation attacker, Collection<DBNation> defenders, int currentWeekTurn, long tieBreakSeed) {
        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.add(attacker);
        allNations.addAll(defenders);
        return activityProviderFor(allNations, currentWeekTurn, tieBreakSeed);
    }

    private static SnapshotActivityProvider activityProviderFor(Collection<DBNation> nations, int currentWeekTurn, long tieBreakSeed) {
        return CompositeBlitzActivityModel.build(nations, Locutus.imp().getWarDb(), currentWeekTurn, tieBreakSeed).snapshotProvider();
    }

    private static List<ScheduledAttacker> parseAvailability(
            Map<Integer, DBNation> attackerById,
            String availability,
            int bucketSizeTurns
    ) {
        if (availability == null || availability.isBlank()) {
            return attackerById.values().stream()
                    .map(DBNationSnapshot::of)
                    .map(snapshot -> new ScheduledAttacker(snapshot, List.of(new AvailabilityWindow(0, bucketSizeTurns - 1))))
                    .sorted(Comparator.comparingInt(entry -> entry.attacker().nationId()))
                    .toList();
        }

        Map<Integer, List<AvailabilityWindow>> windowsByNationId = new LinkedHashMap<>();
        String[] nationEntries = availability.split(";");
        for (String nationEntry : nationEntries) {
            if (nationEntry.isBlank()) {
                continue;
            }
            String[] parts = nationEntry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid availability entry: " + nationEntry);
            }
            int nationId = Integer.parseInt(parts[0].trim());
            DBNation attackerNation = attackerById.get(nationId);
            if (attackerNation == null) {
                throw new IllegalArgumentException("Availability references unknown attacker nationId=" + nationId);
            }
            List<AvailabilityWindow> windows = windowsByNationId.computeIfAbsent(nationId, ignored -> new ArrayList<>());
            for (String range : parts[1].split("\\|")) {
                String[] bounds = range.trim().split("-", 2);
                if (bounds.length != 2) {
                    throw new IllegalArgumentException("Invalid availability range: " + range);
                }
                windows.add(new AvailabilityWindow(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim())));
            }
        }

        Set<Integer> remainingNationIds = new LinkedHashSet<>(attackerById.keySet());
        remainingNationIds.removeAll(windowsByNationId.keySet());
        for (Integer nationId : remainingNationIds) {
            windowsByNationId.put(nationId, List.of(new AvailabilityWindow(0, bucketSizeTurns - 1)));
        }

        return windowsByNationId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ScheduledAttacker(
                        snapshots(java.util.List.of(attackerById.get(entry.getKey())), false).get(0),
                        entry.getValue()
                ))
                .toList();
    }

    private record BlitzPlanContext(
            BlitzPlanRequest request,
            int currentTurn,
            int horizonTurns,
            Collection<DBNation> attackers,
            Collection<DBNation> defenders,
            List<DBNationSnapshot> attackerSnapshots,
            List<DBNationSnapshot> defenderSnapshots,
            int[] attackerNationIds,
            int[] defenderNationIds,
            OverrideSet overrides,
            CompositeBlitzActivityModel activityModel,
            BlitzNationRow[] nations,
            BlitzExistingWar[] existingWars,
            BlitzLegalEdge[] legalEdges,
            BlitzWarning[] warnings,
            List<BlitzPlannedWar> acceptedPlannedWars
    ) {
    }

    private record BlitzRunInput(
            List<DBNationSnapshot> declarers,
            List<DBNationSnapshot> targets,
            TreatyProvider treatyProvider
    ) {
    }
}
