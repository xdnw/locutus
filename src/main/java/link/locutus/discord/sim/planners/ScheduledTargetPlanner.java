package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.StrategicObjective;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Rolling bucket scheduler that re-plans each bucket against planner-owned carried state.
 */
public final class ScheduledTargetPlanner {
    enum CarryStateMode {
        DIRECT_CONFLICT,
        PROJECTION_STATE
    }

    private final SimTuning tuning;
    private final TreatyProvider treatyProvider;
    private final OverrideSet overrides;
    private final StrategicObjective objective;
    private final SnapshotActivityProvider snapshotActivityProvider;
    private final PlannerTransitionSemantics transitionSemantics;
    private final CarryStateMode carryStateMode;

    public ScheduledTargetPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, StrategicObjective objective) {
        this(
                tuning,
                treatyProvider,
                overrides,
                objective,
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE,
                CarryStateMode.PROJECTION_STATE
        );
    }

    public ScheduledTargetPlanner(SimTuning tuning, TreatyProvider treatyProvider, OverrideSet overrides, StrategicObjective objective, SnapshotActivityProvider activityProvider) {
        this(
                tuning,
                treatyProvider,
                overrides,
                objective,
                activityProvider,
                PlannerTransitionSemantics.NONE,
                CarryStateMode.PROJECTION_STATE
        );
    }

    public ScheduledTargetPlanner(
            SimTuning tuning,
            TreatyProvider treatyProvider,
            OverrideSet overrides,
            StrategicObjective objective,
            SnapshotActivityProvider activityProvider,
            PlannerTransitionSemantics transitionSemantics
    ) {
        this(tuning, treatyProvider, overrides, objective, activityProvider, transitionSemantics, CarryStateMode.PROJECTION_STATE);
    }

    ScheduledTargetPlanner(
            SimTuning tuning,
            TreatyProvider treatyProvider,
            OverrideSet overrides,
            StrategicObjective objective,
            SnapshotActivityProvider activityProvider,
            PlannerTransitionSemantics transitionSemantics,
            CarryStateMode carryStateMode
    ) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
        this.treatyProvider = Objects.requireNonNull(treatyProvider, "treatyProvider");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
        this.objective = Objects.requireNonNull(objective, "objective");
        this.snapshotActivityProvider = Objects.requireNonNull(activityProvider, "activityProvider");
        this.transitionSemantics = transitionSemantics == null ? PlannerTransitionSemantics.NONE : transitionSemantics;
        this.carryStateMode = carryStateMode == null ? CarryStateMode.PROJECTION_STATE : carryStateMode;
    }

    public ScheduledTargetPlanner(SimTuning tuning) {
        this(
                tuning,
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                SnapshotActivityProvider.BASELINE,
                PlannerTransitionSemantics.NONE
        );
    }

    public ScheduledTargetPlan assign(
            Collection<ScheduledAttacker> attackers,
            Collection<DBNationSnapshot> defenders,
            int bucketSizeTurns
    ) {
        try (PlannerProfiler.ScopeToken ignored = PlannerProfiler.enter(PlannerProfiler.Scope.SCHEDULED_ASSIGN)) {
            Objects.requireNonNull(attackers, "attackers");
            Objects.requireNonNull(defenders, "defenders");
            if (bucketSizeTurns <= 0) {
                throw new IllegalArgumentException("bucketSizeTurns must be > 0");
            }

            List<ScheduledAttacker> attackerList = List.copyOf(attackers);
            List<DBNationSnapshot> defenderList = List.copyOf(defenders);
            PlannerProfiler.addCounter(PlannerProfiler.Scope.SCHEDULED_ASSIGN, "attackers", attackerList.size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.SCHEDULED_ASSIGN, "defenders", defenderList.size());
            PlannerProfiler.addCounter(PlannerProfiler.Scope.SCHEDULED_ASSIGN, "bucketSizeTurns", bucketSizeTurns);
            List<PlannerDiagnostic> diagnostics = new ArrayList<>();
            if (attackerList.isEmpty() || defenderList.isEmpty()) {
                return new ScheduledTargetPlan(bucketSizeTurns, List.of(), List.of(), diagnostics);
            }

            Map<Integer, DBNationSnapshot> seedById = new Int2ObjectLinkedOpenHashMap<>();
            List<DBNationSnapshot> attackerSeeds = new ArrayList<>(attackerList.size());
            for (ScheduledAttacker attacker : attackerList) {
                attackerSeeds.add(attacker.attacker());
                seedById.put(attacker.attacker().nationId(), attacker.attacker());
            }
            for (DBNationSnapshot defender : defenderList) {
                seedById.putIfAbsent(defender.nationId(), defender);
            }

            int minTurn = attackerList.stream()
                    .flatMap(attacker -> attacker.windows().stream())
                    .mapToInt(AvailabilityWindow::startTurn)
                    .min()
                    .orElse(0);
            int maxTurnExclusive = attackerList.stream()
                    .flatMap(attacker -> attacker.windows().stream())
                    .mapToInt(window -> window.endTurnInclusive() + 1)
                    .max()
                    .orElse(minTurn);

            PlannerProjectionState rollingProjectionState = carryStateMode == CarryStateMode.PROJECTION_STATE
                    ? PlannerProjectionState.seed(overrides, seedById.values(), List.of(), minTurn)
                    : null;
            PlannerLocalConflict rollingConflict = carryStateMode == CarryStateMode.DIRECT_CONFLICT
                    ? PlannerLocalConflict.createWithActiveWars(
                            overrides,
                            seedById.values(),
                            List.of(),
                            minTurn,
                            tuning,
                            transitionSemantics
                    )
                    : null;
            PlannerSimSupport.collectResetDiagnostics(attackerSeeds, defenderList, diagnostics);

            List<Integer> defenderIds = defenderList.stream().map(DBNationSnapshot::nationId).toList();
            List<ScheduledBucketAssignment> buckets = new ArrayList<>();

            for (int bucketStart = minTurn; bucketStart < maxTurnExclusive; bucketStart += bucketSizeTurns) {
                int bucketEndExclusive = Math.min(maxTurnExclusive, bucketStart + bucketSizeTurns);
                Set<Integer> availableIds = new IntLinkedOpenHashSet();
                for (ScheduledAttacker attacker : attackerList) {
                    if (attacker.isAvailable(bucketStart, bucketEndExclusive)) {
                        availableIds.add(attacker.attacker().nationId());
                    }
                }

                List<Integer> availableList = availableIds.stream().sorted().toList();
                List<Integer> eligibleList = List.of();
                BlitzAssignment assignment = new BlitzAssignment(Map.of(), List.of(), 0.0);

                if (!availableList.isEmpty()) {
                    List<DBNationSnapshot> currentAttackers = currentSnapshots(rollingConflict, rollingProjectionState, availableList);
                    currentAttackers = currentAttackers.stream()
                            .filter(snapshot -> overrides.effectiveFreeOff(snapshot) > 0)
                            .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                            .toList();

                    List<DBNationSnapshot> currentDefenders = currentSnapshots(rollingConflict, rollingProjectionState, defenderIds).stream()
                            .filter(snapshot -> overrides.effectiveFreeDef(snapshot) > 0)
                            .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                            .toList();

                    if (!currentAttackers.isEmpty() && !currentDefenders.isEmpty()) {
                        eligibleList = currentAttackers.stream().map(DBNationSnapshot::nationId).toList();
                        BlitzPlanner planner = new BlitzPlanner(tuning, treatyProvider, overrides, objective, snapshotActivityProvider);
                        assignment = planner.assign(currentAttackers, currentDefenders, bucketStart);
                    }
                }

                buckets.add(new ScheduledBucketAssignment(bucketStart, bucketEndExclusive - 1, availableList, eligibleList, assignment));
                if (rollingConflict != null) {
                    rollingConflict.applyAssignmentHorizon(assignment.assignment(), bucketEndExclusive - bucketStart);
                } else {
                    rollingProjectionState = rollingProjectionState.advance(
                            tuning,
                            assignment.assignment(),
                            bucketEndExclusive - bucketStart,
                            transitionSemantics
                    );
                }
            }

            PlannerProfiler.addCounter(PlannerProfiler.Scope.SCHEDULED_ASSIGN, "buckets", buckets.size());
            List<ScheduledTimingComparison> timingComparisons = ScheduledTimingComparison.fromBuckets(buckets);
            return new ScheduledTargetPlan(bucketSizeTurns, buckets, timingComparisons, diagnostics);
        }
    }

    private List<DBNationSnapshot> currentSnapshots(
            PlannerLocalConflict rollingConflict,
            PlannerProjectionState rollingProjectionState,
            Collection<Integer> nationIds
    ) {
        if (rollingConflict != null) {
            return rollingConflict.snapshotsFor(nationIds);
        }
        return rollingProjectionState.snapshotsFor(nationIds);
    }
}