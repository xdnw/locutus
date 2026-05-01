package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
import link.locutus.discord.sim.BlitzObjective;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.TeamScoreObjective;
import link.locutus.discord.sim.Turn1DeclarePolicy;
import link.locutus.discord.sim.WarSlotRules;
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
import link.locutus.discord.web.commands.binding.value_types.BlitzAssignedWarSource;
import link.locutus.discord.web.commands.binding.value_types.BlitzDraftEdit;
import link.locutus.discord.web.commands.binding.value_types.BlitzMilitaryRules;
import link.locutus.discord.web.commands.binding.value_types.BlitzObjectiveSummary;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanRequest;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlanResponse;
import link.locutus.discord.web.commands.binding.value_types.BlitzPlannedWar;
import link.locutus.discord.web.commands.binding.value_types.BlitzRebuyMode;
import link.locutus.discord.web.commands.binding.value_types.BlitzReplayTrace;
import link.locutus.discord.web.commands.binding.value_types.BlitzSideMode;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocPlan;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocTarget;
import link.locutus.discord.web.commands.binding.value_types.WebTarget;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
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
    private static final int BLITZ_PLAN_MIN_HORIZON_TURNS = 1;
    private static final int BLITZ_PLAN_MAX_HORIZON_TURNS = 720;

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
                tuningFor(stochastic, stochasticSamples, stochasticSeed),
                BlitzObjective.defaultObjective().objective(),
                1
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

    @Command(desc = "Blitz planner static rules", viewable = true)
    @ReturnType(value = BlitzMilitaryRules.class, cache = CacheType.SessionStorage, duration = 3600)
    public BlitzMilitaryRules blitzRules() {
        return BlitzMilitaryRules.instance();
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
        return responseFromContext(context, null, new link.locutus.discord.sim.planners.PlannerDiagnostic[0], null, null);
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
                tuningForRequest(request),
                objectiveForRequest(request),
                context.horizonTurns()
        );
        BlitzObjectiveSummary objective = objectiveSummary(assignment.objectiveSummary());
        BlitzReplayTrace trace = request.captureTrace()
            ? replayTrace(context, assignment)
            : null;
        return responseFromContext(
                context,
                assignment,
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
        if (request.horizonTurns() < BLITZ_PLAN_MIN_HORIZON_TURNS || request.horizonTurns() > BLITZ_PLAN_MAX_HORIZON_TURNS) {
            throw new IllegalArgumentException("horizonTurns must be between 1 and 720");
        }
        BlitzSideMode sideMode = enumAt(BlitzSideMode.values(), request.sideModeOrdinal(), "sideModeOrdinal");
        enumAt(BlitzRebuyMode.values(), request.rebuyModeOrdinal(), "rebuyModeOrdinal");
        validateObjectiveOrdinal(request);
        validateTurn1DeclarePolicyOrdinal(request);
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
            throw new IllegalArgumentException(overlapNationError(attackerById, defenderById, overlap));
        }

        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.addAll(attackers);
        allNations.addAll(defenders);
        Map<Integer, BlitzDraftEdit> editsByNationId = editsByNationId(request, attackerById, defenderById);
        OverrideSet overrides = plannerOverrides(editsByNationId, allNations, request.includeExistingWars());
        Map<Integer, Map<MilitaryUnit, Integer>> currentBuys = switch (BlitzRebuyMode.values()[request.rebuyModeOrdinal()]) {
            case CURRENT_BUYS -> Locutus.imp().getNationDB().getSimUnitBuysToday(allNations);
            case FULL_REBUYS -> Map.of();
            case NO_REBUYS -> noRebuyUnitBuys(allNations, request.assume5553Buildings());
        };
        List<DBNationSnapshot> snapshots = draftSnapshots(allNations, editsByNationId, currentBuys);
        Map<Integer, DBNationSnapshot> snapshotsByNationId = snapshotsByNationId(snapshots);
        List<DBNationSnapshot> attackerSnapshots = snapshotsFor(attackers, snapshotsByNationId);
        List<DBNationSnapshot> defenderSnapshots = snapshotsFor(defenders, snapshotsByNationId);

        Set<Integer> excludedWarIds = excludedWarIds(request);
        int currentTurn = request.currentTurnOverride() == null ? currentWeekTurnUtc() : request.currentTurnOverride();
        Map<Integer, DBNation> plannerNationById = new LinkedHashMap<>(attackerById);
        defenderById.forEach(plannerNationById::putIfAbsent);
        int[] plannerNationIds = nationIds(allNations);
        BlitzWarContext warContext = buildWarContext(
            warDB,
            plannerNationById,
            attackerById,
            defenderById,
            sideMode,
            currentTurn,
            excludedWarIds,
            request.includeExistingWars(),
            plannerNationIds
        );
        if (request.includeExistingWars()) {
            snapshots = applyExistingWarSlotContext(snapshots, allNations, warContext.activeWars(), currentTurn);
            snapshotsByNationId = snapshotsByNationId(snapshots);
            attackerSnapshots = snapshotsFor(attackers, snapshotsByNationId);
            defenderSnapshots = snapshotsFor(defenders, snapshotsByNationId);
        }

        List<BlitzWarning> warnings = new ArrayList<>();
        List<BlitzPlannedWar> acceptedPlannedWars = validatePlannedWarsDirect(
            request,
            sideMode,
            attackerById,
            defenderById,
            snapshotsByNationId,
            editsByNationId,
            request.includeExistingWars(),
            warContext.pairLockoutByPair(),
            warnings
        );

        CompositeBlitzActivityModel activityModel = CompositeBlitzActivityModel.build(
            allNations,
            warDB,
            currentTurn,
            request.stochasticSeed()
        );

        CompactPlannerNationLanes plannerLanes = plannerNationLanes(
            plannerNationIds,
            attackerById,
            defenderById,
            editsByNationId,
            currentBuys,
            snapshotsByNationId,
            request.assume5553Buildings(),
            request.includeExistingWars(),
            activityModel
        );

        return new BlitzPlanContext(
                request,
                currentTurn,
                request.horizonTurns(),
            activityModel,
                attackerSnapshots,
                defenderSnapshots,
                nationIds(attackers),
                nationIds(defenders),
                overrides,
            plannerNationIds.length,
            warContext.allianceIds(),
            warContext.allianceNames(),
            warContext.participantIds(),
            warContext.participantNames(),
            warContext.participantAllianceIndexes(),
            warContext.participantIndexByNationId(),
            plannerLanes.scalarLanes(),
            plannerLanes.bitLanes(),
            plannerLanes.unitLanes(),
            warContext.existingWarPairs(),
            warContext.existingWarLanes(),
            warContext.pairLockoutPairs(),
            warContext.pairLockoutLanes(),
            warContext.pairLockoutByPair(),
            warningLanes(warnings, warContext.participantIndexByNationId()),
                acceptedPlannedWars
        );
    }

    private static String overlapNationError(
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            Set<Integer> overlap
    ) {
        List<String> labels = new ArrayList<>();
        for (int nationId : overlap) {
            DBNation nation = attackerById.get(nationId);
            if (nation == null) {
                nation = defenderById.get(nationId);
            }
            String nationName = nation == null ? null : nation.getNation();
            nationName = nationName == null ? null : nationName.trim();
            labels.add(nationName == null || nationName.isEmpty() ? "#" + nationId : nationName + " (#" + nationId + ")");
        }
        return "Nations cannot be on both blitz sides: " + String.join(", ", labels);
    }

    private static OverrideSet plannerOverrides(
            Map<Integer, BlitzDraftEdit> editsByNationId,
            Collection<DBNation> allNations,
            boolean includeExistingWars
    ) {
        OverrideSet.Builder builder = OverrideSet.builder();
        for (BlitzDraftEdit edit : editsByNationId.values()) {
            if (edit.forceActive() != null) {
                builder.active(edit.nationId(), edit.forceActive() ? ActiveOverride.TRUE : ActiveOverride.FALSE);
            }
        }
        if (!includeExistingWars) {
            for (DBNation nation : allNations) {
                builder.forceFreeOff(nation.getNation_id(), nation.getMaxOff());
                builder.forceFreeDefSlots(nation.getNation_id(), WarSlotRules.defensiveSlotCap());
            }
        }
        return builder.build();
    }

    private static BlitzPlanResponse responseFromContext(
            BlitzPlanContext context,
            BlitzAssignment assignment,
            link.locutus.discord.sim.planners.PlannerDiagnostic[] diagnostics,
            BlitzObjectiveSummary objective,
            BlitzReplayTrace trace
    ) {
        CompactAssignmentData assignmentData = assignment == null
                ? CompactAssignmentData.EMPTY
                : assignmentData(assignment, context.acceptedPlannedWars(), context.participantIndexByNationId());
        return new BlitzPlanResponse(
                context.currentTurn(),
                context.horizonTurns(),
                context.plannerNationCount(),
                context.allianceIds(),
                context.allianceNames(),
                context.participantIds(),
                context.participantNames(),
                context.participantAllianceIndexes(),
                context.plannerScalarLanes(),
                context.plannerBitLanes(),
                context.plannerUnitLanes(),
                context.existingWarPairs(),
                context.existingWarLanes(),
                context.pairLockoutPairs(),
                context.pairLockoutLanes(),
                assignmentData.assignmentPairs(),
                assignmentData.assignmentLanes(),
                context.warningLanes(),
                diagnosticLanes(diagnostics, context.participantIndexByNationId()),
                objective,
                trace
        );
    }

    private static BlitzReplayTrace replayTrace(BlitzPlanContext context, BlitzAssignment assignment) {
        BlitzSideMode sideMode = enumAt(BlitzSideMode.values(), context.request().sideModeOrdinal(), "sideModeOrdinal");
        return PlannerReplayProjector.capture(
                tuningForRequest(context.request()),
                context.overrides(),
                combinedSnapshots(context.attackerSnapshots(), context.defenderSnapshots()),
                context.attackerNationIds(),
                context.defenderNationIds(),
                assignment.assignment(),
                assignment.initialWarTypeOrdinalsByPair(),
                counterDeclarers(context, sideMode),
                counterTargets(context, sideMode),
                objectiveForRequest(context.request()),
                context.participantIds(),
                context.existingWarPairs(),
                context.currentTurn(),
                context.horizonTurns()
        );
    }

    private static List<DBNationSnapshot> counterDeclarers(BlitzPlanContext context, BlitzSideMode sideMode) {
        return switch (sideMode) {
            case ATTACKERS_ONLY -> context.defenderSnapshots();
            case DEFENDERS_ONLY -> context.attackerSnapshots();
            case BOTH -> List.of();
        };
    }

    private static List<DBNationSnapshot> counterTargets(BlitzPlanContext context, BlitzSideMode sideMode) {
        return switch (sideMode) {
            case ATTACKERS_ONLY -> context.attackerSnapshots();
            case DEFENDERS_ONLY -> context.defenderSnapshots();
            case BOTH -> List.of();
        };
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
            if (Boolean.TRUE.equals(edit.clearBeige())) {
                builder.beigeTurns(0);
            }
            if (Boolean.TRUE.equals(edit.clearVacationMode())) {
                builder.vmTurns(0);
            }
            snapshots.add(builder.build());
        }
        return snapshots.stream()
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static BlitzWarContext buildWarContext(
            WarDB warDB,
            Map<Integer, DBNation> plannerNationById,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            BlitzSideMode sideMode,
            int currentTurn,
            Set<Integer> excludedWarIds,
            boolean includeExistingWars,
            int[] plannerNationIds
    ) {
        Set<Integer> plannerNationIdSet = new LinkedHashSet<>(plannerNationIds.length);
        for (int plannerNationId : plannerNationIds) {
            plannerNationIdSet.add(plannerNationId);
        }
        long minBlockedStartTurn = currentTurn - WarSlotRules.sameOpponentLockoutTurns();
        Collection<DBWar> wars = warDB.getWarsForNationOrAlliance(plannerNationIdSet::contains, null, war -> {
            if (excludedWarIds.contains(war.getWarId())) {
                return false;
            }
            if (war.isActive()) {
                return true;
            }
            return TimeUtil.getTurn(war.getDate()) > minBlockedStartTurn;
        }).values();

        Long2IntOpenHashMap pairLockoutByPair = new Long2IntOpenHashMap();
        pairLockoutByPair.defaultReturnValue(0);
        List<ActiveWarContext> activeWars = new ArrayList<>();
        Set<Integer> outsiderIds = new LinkedHashSet<>();

        wars.stream()
                .sorted(Comparator.comparingInt(DBWar::getWarId))
                .forEach(war -> {
                    int attackerNationId = war.getAttacker_id();
                    int defenderNationId = war.getDefender_id();
                    boolean active = war.isActive();
                        int warStartTurn = (int) TimeUtil.getTurn(war.getDate());
                    int lockoutValue = active
                            ? -1
                            : Math.max(0, WarSlotRules.sameOpponentLockoutTurns() - (currentTurn - warStartTurn));
                    if (lockoutValue != 0) {
                        if (isCandidatePair(sideMode, attackerById, defenderById, attackerNationId, defenderNationId)) {
                            putPairLockout(pairLockoutByPair, attackerNationId, defenderNationId, lockoutValue);
                        }
                        if (isCandidatePair(sideMode, attackerById, defenderById, defenderNationId, attackerNationId)) {
                            putPairLockout(pairLockoutByPair, defenderNationId, attackerNationId, lockoutValue);
                        }
                    }
                    if (active && includeExistingWars) {
                        activeWars.add(activeWarContext(war));
                        if (!plannerNationById.containsKey(attackerNationId)) {
                            outsiderIds.add(attackerNationId);
                        }
                        if (!plannerNationById.containsKey(defenderNationId)) {
                            outsiderIds.add(defenderNationId);
                        }
                    }
                });

        IntArrayList participantIds = new IntArrayList(plannerNationIds.length + outsiderIds.size());
        for (int plannerNationId : plannerNationIds) {
            participantIds.add(plannerNationId);
        }
        if (includeExistingWars && !outsiderIds.isEmpty()) {
            outsiderIds.stream().sorted().forEach(participantIds::add);
        }

        Int2IntOpenHashMap participantIndexByNationId = new Int2IntOpenHashMap(Math.max(16, participantIds.size() * 2));
        participantIndexByNationId.defaultReturnValue(-1);
        for (int index = 0; index < participantIds.size(); index++) {
            participantIndexByNationId.put(participantIds.getInt(index), index);
        }

        Map<Integer, NationIdentity> identitiesByNationId = new LinkedHashMap<>();
        for (int index = 0; index < participantIds.size(); index++) {
            int nationId = participantIds.getInt(index);
            identitiesByNationId.put(nationId, nationIdentity(nationId, plannerNationById));
        }

        IntArrayList allianceIds = new IntArrayList();
        for (NationIdentity identity : identitiesByNationId.values()) {
            if (identity.allianceId() > 0) {
                allianceIds.add(identity.allianceId());
            }
        }
        int[] allianceIdArray = allianceIds.intStream().distinct().sorted().toArray();
        String[] allianceNames = new String[allianceIdArray.length];
        Int2IntOpenHashMap allianceIndexByAllianceId = new Int2IntOpenHashMap(Math.max(16, allianceIdArray.length * 2));
        allianceIndexByAllianceId.defaultReturnValue(-1);
        for (int index = 0; index < allianceIdArray.length; index++) {
            allianceIndexByAllianceId.put(allianceIdArray[index], index);
            allianceNames[index] = allianceName(allianceIdArray[index], identitiesByNationId.values());
        }

        String[] participantNames = new String[participantIds.size()];
        int[] participantAllianceIndexes = new int[participantIds.size()];
        for (int index = 0; index < participantIds.size(); index++) {
            NationIdentity identity = identitiesByNationId.get(participantIds.getInt(index));
            participantNames[index] = identity == null ? "" : identity.nationName();
            participantAllianceIndexes[index] = identity == null || identity.allianceId() <= 0
                    ? -1
                    : allianceIndexByAllianceId.get(identity.allianceId());
        }

        IntArrayList existingWarPairs = new IntArrayList(activeWars.size() * 2);
        IntArrayList existingWarLanes = new IntArrayList(activeWars.size() * 5);
        for (ActiveWarContext war : activeWars) {
            int attackerIndex = participantIndexByNationId.get(war.attackerNationId());
            int defenderIndex = participantIndexByNationId.get(war.defenderNationId());
            if (attackerIndex < 0 || defenderIndex < 0) {
                continue;
            }
            existingWarPairs.add(attackerIndex);
            existingWarPairs.add(defenderIndex);
            existingWarLanes.add(war.warId());
            existingWarLanes.add(war.startTurn());
            existingWarLanes.add(war.turnsLeft());
            existingWarLanes.add(war.packedCombatState());
            existingWarLanes.add(war.packedFlags());
        }

        List<Long> pairKeys = new ArrayList<>(pairLockoutByPair.keySet());
        pairKeys.sort(Comparator
            .comparingInt((Long key) -> participantIndexByNationId.get(leftNationId(key)))
            .thenComparingInt(key -> participantIndexByNationId.get(rightNationId(key))));
        IntArrayList pairLockoutPairs = new IntArrayList(pairKeys.size() * 2);
        IntArrayList pairLockoutLanes = new IntArrayList(pairKeys.size());
        for (long pairKey : pairKeys) {
            int declarerIndex = participantIndexByNationId.get(leftNationId(pairKey));
            int targetIndex = participantIndexByNationId.get(rightNationId(pairKey));
            if (declarerIndex < 0 || targetIndex < 0) {
                continue;
            }
            pairLockoutPairs.add(declarerIndex);
            pairLockoutPairs.add(targetIndex);
            pairLockoutLanes.add(pairLockoutByPair.get(pairKey));
        }

        return new BlitzWarContext(
                allianceIdArray,
                allianceNames,
                participantIds.toIntArray(),
                participantNames,
                participantAllianceIndexes,
                participantIndexByNationId,
                existingWarPairs.toIntArray(),
                existingWarLanes.toIntArray(),
                pairLockoutPairs.toIntArray(),
                pairLockoutLanes.toIntArray(),
                pairLockoutByPair,
                activeWars.toArray(ActiveWarContext[]::new)
        );
    }

    private static CompactPlannerNationLanes plannerNationLanes(
            int[] plannerNationIds,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            Map<Integer, BlitzDraftEdit> editsByNationId,
            Map<Integer, Map<MilitaryUnit, Integer>> currentBuys,
            Map<Integer, DBNationSnapshot> snapshotsByNationId,
            boolean assume5553Buildings,
            boolean includeExistingWars,
            CompositeBlitzActivityModel activityModel
    ) {
        MilitaryUnit[] units = MilitaryUnit.values();
        int[] scalarLanes = new int[plannerNationIds.length * 17];
        int[] bitLanes = new int[plannerNationIds.length * 3];
        int[] unitLanes = new int[plannerNationIds.length * 3 * units.length];
        for (int nationIndex = 0; nationIndex < plannerNationIds.length; nationIndex++) {
            int nationId = plannerNationIds[nationIndex];
            DBNation nation = attackerById.get(nationId);
            int sideOrdinal = 0;
            if (nation == null) {
                nation = defenderById.get(nationId);
                sideOrdinal = 1;
            }
            BlitzDraftEdit edit = editsByNationId.get(nationId);
            DBNationSnapshot snapshot = snapshotsByNationId.get(nationId);
            fillPlannerNationLanes(
                    scalarLanes,
                    bitLanes,
                    unitLanes,
                    nationIndex,
                    nation,
                    sideOrdinal,
                    edit,
                    currentBuys.getOrDefault(nationId, Map.of()),
                    snapshot,
                    assume5553Buildings,
                    includeExistingWars,
                    activityModel,
                    units
            );
        }
        return new CompactPlannerNationLanes(scalarLanes, bitLanes, unitLanes);
    }

    private static void fillPlannerNationLanes(
            int[] scalarLanes,
            int[] bitLanes,
            int[] unitLanes,
            int nationIndex,
            DBNation nation,
            int sideOrdinal,
            BlitzDraftEdit edit,
            Map<MilitaryUnit, Integer> currentBuys,
            DBNationSnapshot snapshot,
            boolean assume5553Buildings,
            boolean includeExistingWars,
            CompositeBlitzActivityModel activityModel,
            MilitaryUnit[] units
    ) {
        int scalarOffset = nationIndex * 17;
        int bitOffset = nationIndex * 3;
        int unitOffset = nationIndex * 3 * units.length;
        int activeOrdinal = edit == null || edit.forceActive() == null
                ? ActiveOverride.AUTO.ordinal()
                : (edit.forceActive() ? ActiveOverride.TRUE : ActiveOverride.FALSE).ordinal();
        int resetHourUtc = snapshot == null ? 0 : snapshot.resetHourUtc();
        boolean resetHourUtcFallback = snapshot != null && snapshot.resetHourUtcFallback();
        scalarLanes[scalarOffset] = sideOrdinal;
        scalarLanes[scalarOffset + 1] = snapshot == null ? nation.getCities() : snapshot.cities();
        scalarLanes[scalarOffset + 2] = averageInfraCents(snapshot, nation, edit);
        scalarLanes[scalarOffset + 3] = snapshot == null ? nation.getBeigeTurns() : snapshot.beigeTurns();
        scalarLanes[scalarOffset + 4] = snapshot == null ? nation.getVm_turns() : snapshot.vmTurns();
        scalarLanes[scalarOffset + 5] = nation.active_m();
        scalarLanes[scalarOffset + 6] = activityModel.activityBasisPoints(nation.getNation_id());
        scalarLanes[scalarOffset + 7] = clampBp(nation.login_daychange());
        scalarLanes[scalarOffset + 8] = clampBp(nation.avg_daily_login_week());
        scalarLanes[scalarOffset + 9] = includeExistingWars && snapshot != null ? snapshot.rawFreeOff() : nation.getMaxOff();
        scalarLanes[scalarOffset + 10] = includeExistingWars && snapshot != null ? snapshot.rawFreeDef() : WarSlotRules.defensiveSlotCap();
        scalarLanes[scalarOffset + 11] = nation.getMaxOff();
        scalarLanes[scalarOffset + 12] = snapshot == null || snapshot.warPolicy() == null ? -1 : snapshot.warPolicy().ordinal();
        scalarLanes[scalarOffset + 13] = activeOrdinal;
        scalarLanes[scalarOffset + 14] = resetHourUtc;
        scalarLanes[scalarOffset + 15] = nation.getColor() == null ? -1 : nation.getColor().ordinal();
        scalarLanes[scalarOffset + 16] = resetHourUtcFallback ? 0x1 : 0;

        long projectBits = Math.max(0, nation.getProjectBitMask());
        int researchBits = nation.getResearchBits(null);
        if (edit != null) {
            projectBits = (projectBits | edit.projectBitsSet()) & ~edit.projectBitsClear();
            researchBits = (researchBits | edit.researchBitsSet()) & ~edit.researchBitsClear();
        }
        bitLanes[bitOffset] = (int) projectBits;
        bitLanes[bitOffset + 1] = (int) (projectBits >>> 32);
        bitLanes[bitOffset + 2] = researchBits;

        for (int unitIndex = 0; unitIndex < units.length; unitIndex++) {
            MilitaryUnit unit = units[unitIndex];
            unitLanes[unitOffset + unitIndex] = snapshot == null ? nation.getUnits(unit) : snapshot.unit(unit);
            unitLanes[unitOffset + units.length + unitIndex] = unitCap(
                    nation,
                    unit,
                    assume5553Buildings && (edit == null || edit.unitCountsByMilitaryUnitOrdinal() == null)
            );
            unitLanes[unitOffset + (2 * units.length) + unitIndex] = snapshot == null
                    ? currentBuys.getOrDefault(unit, 0)
                    : snapshot.unitsBoughtToday(unit);
        }
    }

    private static int averageInfraCents(DBNationSnapshot snapshot, DBNation nation, BlitzDraftEdit edit) {
        if (edit != null && edit.avgInfraCents() != null) {
            return edit.avgInfraCents();
        }
        if (snapshot == null || snapshot.cityInfraCount() == 0) {
            return (int) Math.round(nation.getAvg_infra() * 100d);
        }
        double total = 0d;
        for (double infra : snapshot.cityInfra()) {
            total += infra;
        }
        return (int) Math.round((total / snapshot.cityInfraCount()) * 100d);
    }

    private static List<BlitzPlannedWar> validatePlannedWarsDirect(
            BlitzPlanRequest request,
            BlitzSideMode sideMode,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            Map<Integer, DBNationSnapshot> snapshotsByNationId,
            Map<Integer, BlitzDraftEdit> editsByNationId,
            boolean includeExistingWars,
            Long2IntOpenHashMap pairLockoutByPair,
            List<BlitzWarning> warnings
    ) {
        List<BlitzPlannedWar> accepted = new ArrayList<>();
        Set<Long> acceptedUnorderedPairs = sideMode == BlitzSideMode.BOTH ? new LinkedHashSet<>() : Set.of();
        for (BlitzPlannedWar plannedWar : request.plannedWars()) {
            enumAt(WarType.values(), plannedWar.warTypeOrdinal(), "warTypeOrdinal");
            boolean forward = attackerById.containsKey(plannedWar.declarerNationId())
                    && defenderById.containsKey(plannedWar.targetNationId())
                    && (sideMode == BlitzSideMode.ATTACKERS_ONLY || sideMode == BlitzSideMode.BOTH);
            boolean reverse = defenderById.containsKey(plannedWar.declarerNationId())
                    && attackerById.containsKey(plannedWar.targetNationId())
                    && (sideMode == BlitzSideMode.DEFENDERS_ONLY || sideMode == BlitzSideMode.BOTH);
            if (!forward && !reverse) {
                warnings.add(manualDeclarationRejected(
                        plannedWar.declarerNationId(),
                        plannedWar.targetNationId(),
                        "Manual declaration is not between allowed blitz sides"
                ));
                continue;
            }
            String failure = submittedWarValidationFailure(
                    plannedWar.declarerNationId(),
                    plannedWar.targetNationId(),
                    attackerById,
                    defenderById,
                    snapshotsByNationId,
                    editsByNationId,
                    includeExistingWars,
                    pairLockoutByPair
            );
            if (failure != null) {
                warnings.add(manualDeclarationRejected(plannedWar.declarerNationId(), plannedWar.targetNationId(), failure));
                continue;
            }
            if (sideMode == BlitzSideMode.BOTH
                    && !acceptedUnorderedPairs.add(unorderedPackedPairKey(plannedWar.declarerNationId(), plannedWar.targetNationId()))) {
                warnings.add(manualDeclarationRejected(
                        plannedWar.declarerNationId(),
                        plannedWar.targetNationId(),
                        "Manual declaration duplicates an existing BOTH-mode nation pair"
                ));
                continue;
            }
            accepted.add(plannedWar);
        }
        return accepted;
    }

    private static String submittedWarValidationFailure(
            int declarerNationId,
            int targetNationId,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            Map<Integer, DBNationSnapshot> snapshotsByNationId,
            Map<Integer, BlitzDraftEdit> editsByNationId,
            boolean includeExistingWars,
            Long2IntOpenHashMap pairLockoutByPair
    ) {
        int pairLockout = pairLockoutByPair.get(packedPairKey(declarerNationId, targetNationId));
        if (pairLockout == -1) {
            return "Manual declaration conflicts with an active nation pair";
        }
        if (pairLockout > 0) {
            return "Manual declaration is blocked by a same-opponent cooldown for " + pairLockout + " turns";
        }
        DBNation declarer = attackerById.get(declarerNationId);
        if (declarer == null) {
            declarer = defenderById.get(declarerNationId);
        }
        DBNation target = attackerById.get(targetNationId);
        if (target == null) {
            target = defenderById.get(targetNationId);
        }
        if (declarer == null || target == null) {
            return "Manual declaration references an unknown nation";
        }
        BlitzDraftEdit declarerEdit = editsByNationId.get(declarerNationId);
        BlitzDraftEdit targetEdit = editsByNationId.get(targetNationId);
        BlitzDraftNation draftDeclarer = BlitzDraftNation.of(
                declarer,
                snapshotsByNationId.get(declarerNationId),
                declarerEdit == null ? null : declarerEdit.forceActive()
        );
        BlitzDraftNation draftTarget = BlitzDraftNation.of(
                target,
                snapshotsByNationId.get(targetNationId),
                targetEdit == null ? null : targetEdit.forceActive()
        );
        BlitzValidator.Rules rules = new BlitzValidator.Rules(PW.WAR_RANGE_MIN_MODIFIER, PW.WAR_RANGE_MAX_MODIFIER, true, includeExistingWars, false);
        if (!draftStateWarnings(draftDeclarer, draftTarget).isEmpty()) {
            return "Manual declaration is not legal in the current draft state";
        }
        if (!BlitzValidator.validatePair(draftDeclarer, draftTarget, rules).isEmpty()) {
            return "Manual declaration is not legal in the current draft state";
        }
        return null;
    }

    private static BlitzWarning manualDeclarationRejected(int declarerNationId, int targetNationId, String detail) {
        return new BlitzWarning(
                BlitzWarningCode.MANUAL_DECLARATION_REJECTED,
                declarerNationId,
                targetNationId,
                0,
                detail
        );
    }

    private static int[] warningLanes(List<BlitzWarning> warnings, Int2IntOpenHashMap participantIndexByNationId) {
        IntArrayList lanes = new IntArrayList(warnings.size() * 4);
        for (BlitzWarning warning : warnings) {
            lanes.add(warning.codeOrdinal());
            lanes.add(participantIndex(participantIndexByNationId, warning.attackerNationId()));
            lanes.add(participantIndex(participantIndexByNationId, warning.defenderNationId()));
            lanes.add(warning.warId());
        }
        return lanes.toIntArray();
    }

    private static int[] diagnosticLanes(
            link.locutus.discord.sim.planners.PlannerDiagnostic[] diagnostics,
            Int2IntOpenHashMap participantIndexByNationId
    ) {
        IntArrayList lanes = new IntArrayList(diagnostics.length * 4);
        for (link.locutus.discord.sim.planners.PlannerDiagnostic diagnostic : diagnostics) {
            lanes.add(diagnostic.codeOrdinal());
            lanes.add(diagnostic.severityOrdinal());
            lanes.add(diagnostic.nationRoleOrdinal());
            lanes.add(participantIndex(participantIndexByNationId, diagnostic.nationId()));
        }
        return lanes.toIntArray();
    }

    private static CompactAssignmentData assignmentData(
            BlitzAssignment assignment,
            List<BlitzPlannedWar> acceptedPlannedWars,
            Int2IntOpenHashMap participantIndexByNationId
    ) {
        IntArrayList pairs = new IntArrayList();
        IntArrayList lanes = new IntArrayList();
        Set<Long> fixedPairs = new LinkedHashSet<>();
        for (BlitzPlannedWar plannedWar : acceptedPlannedWars) {
            long pairKey = packedPairKey(plannedWar.declarerNationId(), plannedWar.targetNationId());
            fixedPairs.add(pairKey);
            appendAssignment(
                    pairs,
                    lanes,
                    participantIndexByNationId,
                    plannedWar.declarerNationId(),
                    plannedWar.targetNationId(),
                    plannedWar.warTypeOrdinal(),
                    BlitzAssignedWarSource.USER_PINNED.ordinal(),
                    assignment.initialAttackTypeOrdinal(plannedWar.declarerNationId(), plannedWar.targetNationId())
            );
        }
        assignment.assignment().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entry.getValue().stream()
                        .sorted()
                        .filter(targetNationId -> !fixedPairs.contains(packedPairKey(entry.getKey(), targetNationId)))
                        .forEach(targetNationId -> appendAssignment(
                                pairs,
                                lanes,
                                participantIndexByNationId,
                                entry.getKey(),
                                targetNationId,
                                assignment.initialWarTypeOrdinal(entry.getKey(), targetNationId),
                                BlitzAssignedWarSource.PLANNER.ordinal(),
                                assignment.initialAttackTypeOrdinal(entry.getKey(), targetNationId)
                        )));
        return new CompactAssignmentData(pairs.toIntArray(), lanes.toIntArray());
    }

    private static void appendAssignment(
            IntArrayList pairs,
            IntArrayList lanes,
            Int2IntOpenHashMap participantIndexByNationId,
            int declarerNationId,
            int targetNationId,
            int warTypeOrdinal,
            int sourceOrdinal,
            int initialAttackTypeOrdinal
    ) {
        int declarerIndex = participantIndex(participantIndexByNationId, declarerNationId);
        int targetIndex = participantIndex(participantIndexByNationId, targetNationId);
        if (declarerIndex < 0 || targetIndex < 0) {
            return;
        }
        pairs.add(declarerIndex);
        pairs.add(targetIndex);
        lanes.add(warTypeOrdinal);
        lanes.add(sourceOrdinal);
        lanes.add(initialAttackTypeOrdinal);
    }

    private static int participantIndex(Int2IntOpenHashMap participantIndexByNationId, int nationId) {
        return nationId <= 0 ? -1 : participantIndexByNationId.get(nationId);
    }

    private static boolean isCandidatePair(
            BlitzSideMode sideMode,
            Map<Integer, DBNation> attackerById,
            Map<Integer, DBNation> defenderById,
            int declarerNationId,
            int targetNationId
    ) {
        return switch (sideMode) {
            case ATTACKERS_ONLY -> attackerById.containsKey(declarerNationId) && defenderById.containsKey(targetNationId);
            case DEFENDERS_ONLY -> defenderById.containsKey(declarerNationId) && attackerById.containsKey(targetNationId);
            case BOTH -> (attackerById.containsKey(declarerNationId) && defenderById.containsKey(targetNationId))
                    || (defenderById.containsKey(declarerNationId) && attackerById.containsKey(targetNationId));
        };
    }

    private static void putPairLockout(Long2IntOpenHashMap pairLockoutByPair, int declarerNationId, int targetNationId, int lockoutValue) {
        long pairKey = packedPairKey(declarerNationId, targetNationId);
        int existing = pairLockoutByPair.get(pairKey);
        if (existing == -1) {
            return;
        }
        if (lockoutValue == -1 || lockoutValue > existing) {
            pairLockoutByPair.put(pairKey, lockoutValue);
        }
    }

    private static long packedPairKey(int leftNationId, int rightNationId) {
        return ((long) leftNationId << 32) ^ (rightNationId & 0xffffffffL);
    }

    private static long unorderedPackedPairKey(int nationIdOne, int nationIdTwo) {
        return packedPairKey(Math.min(nationIdOne, nationIdTwo), Math.max(nationIdOne, nationIdTwo));
    }

    private static int leftNationId(long pairKey) {
        return (int) (pairKey >> 32);
    }

    private static int rightNationId(long pairKey) {
        return (int) pairKey;
    }

    private static ActiveWarContext activeWarContext(DBWar war) {
        List<link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor> attacks = war.getAttacks3();
        Map.Entry<Integer, Integer> maps = war.getMap(attacks);
        Map.Entry<Integer, Integer> resistance = war.getResistance(attacks);
        Map.Entry<Boolean, Boolean> fortified = war.getFortified(attacks);
        int packedCombatState = packCombatState(maps.getKey(), maps.getValue(), resistance.getKey(), resistance.getValue());
        int packedFlags = packWarFlags(
                war.getWarType().ordinal(),
                war.getStatus().ordinal(),
                controlOwnerOrdinal(war.getGroundControl(), war),
                controlOwnerOrdinal(war.getAirControl(), war),
                controlOwnerOrdinal(war.getBlockader(), war),
                fortified.getKey(),
                fortified.getValue()
        );
        return new ActiveWarContext(
                war.getWarId(),
                war.getAttacker_id(),
                war.getDefender_id(),
            (int) TimeUtil.getTurn(war.getDate()),
                war.getTurnsLeft(),
                maps.getKey(),
                maps.getValue(),
                resistance.getKey(),
                resistance.getValue(),
                packedCombatState,
                packedFlags
        );
    }

    private static int packCombatState(int attackerMaps, int defenderMaps, int attackerResistance, int defenderResistance) {
        return (attackerMaps & 0xF)
                | ((defenderMaps & 0xF) << 4)
                | ((attackerResistance & 0x7F) << 8)
                | ((defenderResistance & 0x7F) << 15);
    }

    private static int packWarFlags(
            int warTypeOrdinal,
            int warStatusOrdinal,
            int groundControlOwnerOrdinal,
            int airSuperiorityOwnerOrdinal,
            int blockadeOwnerOrdinal,
            boolean attackerFortified,
            boolean defenderFortified
    ) {
        int flags = (warTypeOrdinal & 0x3F)
                | ((warStatusOrdinal & 0x1F) << 6)
                | ((groundControlOwnerOrdinal & 0x3) << 11)
                | ((airSuperiorityOwnerOrdinal & 0x3) << 13)
                | ((blockadeOwnerOrdinal & 0x3) << 15);
        if (attackerFortified) {
            flags |= (1 << 17);
        }
        if (defenderFortified) {
            flags |= (1 << 18);
        }
        return flags;
    }

    private static int controlOwnerOrdinal(int nationId, DBWar war) {
        if (nationId == war.getAttacker_id()) {
            return 1;
        }
        if (nationId == war.getDefender_id()) {
            return 2;
        }
        return 0;
    }

    private static NationIdentity nationIdentity(int nationId, Map<Integer, DBNation> plannerNationById) {
        DBNation nation = plannerNationById.get(nationId);
        if (nation == null) {
            nation = Locutus.imp().getNationDB().getNationById(nationId);
        }
        if (nation == null) {
            return new NationIdentity("", 0, "");
        }
        String nationName = nation.getNation();
        String allianceName = nation.getAllianceName();
        return new NationIdentity(
                nationName == null ? "" : nationName,
                nation.getAlliance_id(),
                allianceName == null ? "" : allianceName
        );
    }

    private static String allianceName(int allianceId, Collection<NationIdentity> identities) {
        for (NationIdentity identity : identities) {
            if (identity.allianceId() == allianceId) {
                return identity.allianceName();
            }
        }
        return "";
    }

    private static List<DBNationSnapshot> applyExistingWarSlotContext(
            List<DBNationSnapshot> snapshots,
            Collection<DBNation> plannerNations,
            ActiveWarContext[] existingWars,
            int currentTurn
    ) {
        if (snapshots.isEmpty()) {
            return snapshots;
        }

        Set<Integer> plannerIds = new LinkedHashSet<>();
        for (DBNation nation : plannerNations) {
            plannerIds.add(nation.getNation_id());
        }
        Map<Integer, ExistingWarSlotContext> contextsByNationId = new LinkedHashMap<>();
        for (ActiveWarContext war : existingWars) {
            int releaseTurn = currentTurn + slotOnlyReleaseTurnsLeft(war);
            applyExistingWarSlotContext(contextsByNationId, plannerIds, war.attackerNationId(), war.defenderNationId(), true, releaseTurn);
            applyExistingWarSlotContext(contextsByNationId, plannerIds, war.defenderNationId(), war.attackerNationId(), false, releaseTurn);
        }

        List<DBNationSnapshot> result = new ArrayList<>(snapshots.size());
        for (DBNationSnapshot snapshot : snapshots) {
            ExistingWarSlotContext context = contextsByNationId.get(snapshot.nationId());
            if (context == null) {
                result.add(snapshot.toBuilder()
                        .currentOffensiveWars(0)
                        .currentDefensiveWars(0)
                        .activeOpponentNationIds(Set.of())
                        .slotOnlyOffensiveWarReleaseTurns(new int[0])
                        .slotOnlyDefensiveWarReleaseTurns(new int[0])
                        .build());
                continue;
            }
            result.add(snapshot.toBuilder()
                    .currentOffensiveWars(context.offensiveWars)
                    .currentDefensiveWars(context.defensiveWars)
                    .activeOpponentNationIds(context.activeOpponentNationIds)
                    .slotOnlyOffensiveWarReleaseTurns(toIntArray(context.slotOnlyOffensiveReleaseTurns))
                    .slotOnlyDefensiveWarReleaseTurns(toIntArray(context.slotOnlyDefensiveReleaseTurns))
                    .build());
        }
        return result;
    }

    private static void applyExistingWarSlotContext(
            Map<Integer, ExistingWarSlotContext> contextsByNationId,
            Set<Integer> plannerIds,
            int nationId,
            int opponentNationId,
            boolean offensiveSide,
            int releaseTurn
    ) {
        if (!plannerIds.contains(nationId)) {
            return;
        }
        ExistingWarSlotContext context = contextsByNationId.computeIfAbsent(nationId, ignored -> new ExistingWarSlotContext());
        context.activeOpponentNationIds.add(opponentNationId);
        boolean slotOnly = !plannerIds.contains(opponentNationId);
        if (offensiveSide) {
            context.offensiveWars++;
            if (slotOnly) {
                context.slotOnlyOffensiveReleaseTurns.add(releaseTurn);
            }
        } else {
            context.defensiveWars++;
            if (slotOnly) {
                context.slotOnlyDefensiveReleaseTurns.add(releaseTurn);
            }
        }
    }

    private static int slotOnlyReleaseTurnsLeft(ActiveWarContext war) {
        int attackerPath = turnsToZeroResistance(war.attackerMap(), war.defenderResistance());
        int defenderPath = turnsToZeroResistance(war.defenderMap(), war.attackerResistance());
        int resistancePath = Math.min(attackerPath, defenderPath);
        return Math.max(1, Math.min(60, resistancePath));
    }

    private static int turnsToZeroResistance(int maps, int targetResistance) {
        if (targetResistance <= 0) {
            return 1;
        }
        int attacksNeeded = (targetResistance + 11) / 12;
        int attacksAvailableNow = Math.max(0, maps) / 3;
        if (attacksNeeded <= attacksAvailableNow) {
            return 1;
        }
        return 1 + ((attacksNeeded - attacksAvailableNow) * 3);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static final class ExistingWarSlotContext {
        private int offensiveWars;
        private int defensiveWars;
        private final Set<Integer> activeOpponentNationIds = new LinkedHashSet<>();
        private final List<Integer> slotOnlyOffensiveReleaseTurns = new ArrayList<>();
        private final List<Integer> slotOnlyDefensiveReleaseTurns = new ArrayList<>();
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

    private static TeamScoreObjective objectiveForRequest(BlitzPlanRequest request) {
        validateObjectiveOrdinal(request);
        Integer ordinal = request.objectiveOrdinal();
        if (ordinal == null) {
            return BlitzObjective.defaultObjective().objective();
        }
        return enumAt(BlitzObjective.values(), ordinal, "objectiveOrdinal").objective();
    }

    private static void validateObjectiveOrdinal(BlitzPlanRequest request) {
        Integer ordinal = request.objectiveOrdinal();
        if (ordinal != null) {
            enumAt(BlitzObjective.values(), ordinal, "objectiveOrdinal");
        }
    }

    private static SimTuning tuningForRequest(BlitzPlanRequest request) {
        return SimTuning.defaults()
                .withStochasticSeed(request.stochasticSeed())
                .withTurn1DeclarePolicy(turn1DeclarePolicyForRequest(request));
    }

    private static Turn1DeclarePolicy turn1DeclarePolicyForRequest(BlitzPlanRequest request) {
        Integer ordinal = request.turn1DeclarePolicyOrdinal();
        if (ordinal == null) {
            return SimTuning.DEFAULT_TURN1_DECLARE_POLICY;
        }
        return enumAt(Turn1DeclarePolicy.values(), ordinal, "turn1DeclarePolicyOrdinal");
    }

    private static void validateTurn1DeclarePolicyOrdinal(BlitzPlanRequest request) {
        Integer ordinal = request.turn1DeclarePolicyOrdinal();
        if (ordinal != null) {
            enumAt(Turn1DeclarePolicy.values(), ordinal, "turn1DeclarePolicyOrdinal");
        }
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

    private static int unitCap(DBNation nation, MilitaryUnit unit, boolean assume5553Buildings) {
        try {
            return nation.getUnitCap(unit, !assume5553Buildings);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static List<BlitzWarning> draftStateWarnings(BlitzDraftNation declarer, BlitzDraftNation target) {
        List<BlitzWarning> result = new ArrayList<>(4);
        result.addAll(BlitzValidator.validateAttacker(declarer));
        result.addAll(BlitzValidator.validateDefender(target, ignored -> true));
        return result;
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
                .map(war -> new BlitzFixedEdge(war.declarerNationId(), war.targetNationId(), war.warTypeOrdinal()))
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
        Set<Long> fixedPairs = new LinkedHashSet<>();
        for (BlitzFixedEdge fixedEdge : fixedEdges) {
            fixedPairs.add(packedPairKey(fixedEdge.attackerNationId(), fixedEdge.defenderNationId()));
        }
        return (declarerId, targetId) -> {
            if (fixedPairs.contains(packedPairKey(declarerId, targetId))) {
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
            SimTuning tuning,
            TeamScoreObjective objective,
            int horizonTurns
    ) {
        return new BlitzPlanner(
                tuning,
                treatyProvider,
            overrides,
                objective,
                activityProvider
        ).assign(declarers, targets, currentTurn, fixedEdges, horizonTurns);
    }

    private static Set<Integer> excludedWarIds(BlitzPlanRequest request) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (int warId : request.excludedWarIds()) {
            if (warId > 0) {
                ids.add(warId);
            }
        }
        return ids;
    }


    private static int clampBp(double fraction) {
        if (Double.isNaN(fraction)) return 0;
        long bp = Math.round(fraction * 10000d);
        if (bp < 0) return 0;
        if (bp > 10000) return 10000;
        return (int) bp;
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
            CompositeBlitzActivityModel activityModel,
            List<DBNationSnapshot> attackerSnapshots,
            List<DBNationSnapshot> defenderSnapshots,
            int[] attackerNationIds,
            int[] defenderNationIds,
            OverrideSet overrides,
            int plannerNationCount,
            int[] allianceIds,
            String[] allianceNames,
            int[] participantIds,
            String[] participantNames,
            int[] participantAllianceIndexes,
            Int2IntOpenHashMap participantIndexByNationId,
            int[] plannerScalarLanes,
            int[] plannerBitLanes,
            int[] plannerUnitLanes,
            int[] existingWarPairs,
            int[] existingWarLanes,
            int[] pairLockoutPairs,
            int[] pairLockoutLanes,
            Long2IntOpenHashMap pairLockoutByPair,
            int[] warningLanes,
            List<BlitzPlannedWar> acceptedPlannedWars
    ) {
    }

        private record BlitzWarContext(
            int[] allianceIds,
            String[] allianceNames,
            int[] participantIds,
            String[] participantNames,
            int[] participantAllianceIndexes,
            Int2IntOpenHashMap participantIndexByNationId,
            int[] existingWarPairs,
            int[] existingWarLanes,
            int[] pairLockoutPairs,
            int[] pairLockoutLanes,
            Long2IntOpenHashMap pairLockoutByPair,
            ActiveWarContext[] activeWars
        ) {
        }

        private record CompactPlannerNationLanes(
            int[] scalarLanes,
            int[] bitLanes,
            int[] unitLanes
        ) {
        }

        private record CompactAssignmentData(
            int[] assignmentPairs,
            int[] assignmentLanes
        ) {
        private static final CompactAssignmentData EMPTY = new CompactAssignmentData(new int[0], new int[0]);
        }

        private record NationIdentity(
            String nationName,
            int allianceId,
            String allianceName
        ) {
        }

        private record ActiveWarContext(
            int warId,
            int attackerNationId,
            int defenderNationId,
            int startTurn,
            int turnsLeft,
            int attackerMap,
            int defenderMap,
            int attackerResistance,
            int defenderResistance,
            int packedCombatState,
            int packedFlags
        ) {
        }

    private record BlitzRunInput(
            List<DBNationSnapshot> declarers,
            List<DBNationSnapshot> targets,
            TreatyProvider treatyProvider
    ) {
    }
}
