package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.SimTuning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable planner-owned projection state carrying base snapshots, planned wars, and sparse city overlays.
 */
final class PlannerProjectionState {
    private final Map<Integer, DBNationSnapshot> baseSnapshotsById;
    private final Map<Long, PlannerProjectedWar> activePlannedWarsByPair;
    private final Map<Integer, PlannerCityInfraOverlay> cityInfraOverlaysByNation;
    private final int currentTurn;

    private PlannerProjectionState(
            Map<Integer, DBNationSnapshot> baseSnapshotsById,
            Map<Long, PlannerProjectedWar> activePlannedWarsByPair,
            Map<Integer, PlannerCityInfraOverlay> cityInfraOverlaysByNation,
            int currentTurn
    ) {
        this.baseSnapshotsById = Collections.unmodifiableMap(new LinkedHashMap<>(baseSnapshotsById));
        this.activePlannedWarsByPair = Collections.unmodifiableMap(new LinkedHashMap<>(activePlannedWarsByPair));
        this.cityInfraOverlaysByNation = Collections.unmodifiableMap(new LinkedHashMap<>(cityInfraOverlaysByNation));
        this.currentTurn = currentTurn;
    }

    static PlannerProjectionState seed(OverrideSet overrides, Collection<DBNationSnapshot> snapshots) {
        return seed(overrides, snapshots, List.of(), 0);
    }

    static PlannerProjectionState seed(
            OverrideSet overrides,
            Collection<DBNationSnapshot> snapshots,
            Collection<PlannerProjectedWar> activeWars
    ) {
        return seed(overrides, snapshots, activeWars, 0);
    }

    static PlannerProjectionState seed(
            OverrideSet overrides,
            Collection<DBNationSnapshot> snapshots,
            Collection<PlannerProjectedWar> activeWars,
            int currentTurn
    ) {
        Map<Integer, DBNationSnapshot> byId = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            byId.put(snapshot.nationId(), overrides.applyToSnapshot(snapshot));
        }
        Map<Long, PlannerProjectedWar> activeByPair = new LinkedHashMap<>();
        for (PlannerProjectedWar war : activeWars) {
            activeByPair.put(war.pairKey(), war);
        }
        return new PlannerProjectionState(byId, activeByPair, Map.of(), currentTurn);
    }

    PlannerProjectionState advance(
            SimTuning tuning,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns,
            PlannerTransitionSemantics transitionSemantics
    ) {
        PlannerTransitionSemantics effectiveTransitionSemantics = transitionSemantics == null
                ? PlannerTransitionSemantics.NONE
                : transitionSemantics;
        Map<Integer, DBNationSnapshot> effectiveSnapshotsById = effectiveSnapshotsById();
        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                OverrideSet.EMPTY,
                effectiveSnapshotsById.values(),
                activePlannedWarsByPair.values(),
            currentTurn,
                tuning,
                effectiveTransitionSemantics
        );
        conflict.applyAssignmentHorizon(assignment, horizonTurns);
        PlannerProjectionResult projection = conflict.project();

        Map<Long, PlannerProjectedWar> nextActiveWars = new LinkedHashMap<>();
        for (PlannerProjectedWar war : projection.activeWars()) {
            nextActiveWars.put(war.pairKey(), war);
        }

        Map<Integer, DBNationSnapshot> nextBaseSnapshots = new LinkedHashMap<>(projection.snapshotsById().size());
        for (Map.Entry<Integer, DBNationSnapshot> entry : projection.snapshotsById().entrySet()) {
            int nationId = entry.getKey();
            DBNationSnapshot projectedSnapshot = entry.getValue();
            DBNationSnapshot priorBaseSnapshot = baseSnapshotsById.get(nationId);
            double[] baseCityInfra = priorBaseSnapshot != null
                    ? priorBaseSnapshot.cityInfra()
                    : projectedSnapshot.cityInfra();
            nextBaseSnapshots.put(
                    nationId,
                    projectedSnapshot.toBuilder().cityInfra(baseCityInfra).build()
            );
        }

        Map<Integer, PlannerCityInfraOverlay> nextOverlays = mergeCityInfraOverlays(
                cityInfraOverlaysByNation,
                projection.cityInfraOverlaysByNation(),
                nextBaseSnapshots
        );

        return new PlannerProjectionState(nextBaseSnapshots, nextActiveWars, nextOverlays, conflict.currentTurn());
    }

    PlannerProjectionState advance(
            SimTuning tuning,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns
    ) {
        return advance(tuning, assignment, horizonTurns, PlannerTransitionSemantics.NONE);
    }

    PlannerProjectionResult toProjectionResult() {
        return new PlannerProjectionResult(
                effectiveSnapshotsById(),
                new ArrayList<>(activePlannedWarsByPair.values()),
                cityInfraOverlaysByNation
        );
    }

    List<DBNationSnapshot> snapshotsFor(Collection<Integer> nationIds) {
        List<DBNationSnapshot> snapshots = new ArrayList<>(nationIds.size());
        for (Integer nationId : nationIds) {
            DBNationSnapshot snapshot = baseSnapshotsById.get(nationId);
            if (snapshot != null) {
                snapshots.add(applyCityInfraOverlay(snapshot));
            }
        }
        return snapshots;
    }

    Map<Long, PlannerProjectedWar> activePlannedWarsByPair() {
        return activePlannedWarsByPair;
    }

    Map<Integer, PlannerCityInfraOverlay> cityInfraOverlaysByNation() {
        return cityInfraOverlaysByNation;
    }

    int currentTurn() {
        return currentTurn;
    }

    private DBNationSnapshot applyCityInfraOverlay(DBNationSnapshot snapshot) {
        PlannerCityInfraOverlay overlay = cityInfraOverlaysByNation.get(snapshot.nationId());
        if (overlay == null || overlay.isEmpty()) {
            return snapshot;
        }
        return overlay.applyTo(snapshot);
    }

    private Map<Integer, DBNationSnapshot> effectiveSnapshotsById() {
        Map<Integer, DBNationSnapshot> effective = new LinkedHashMap<>(baseSnapshotsById.size());
        for (Map.Entry<Integer, DBNationSnapshot> entry : baseSnapshotsById.entrySet()) {
            effective.put(entry.getKey(), applyCityInfraOverlay(entry.getValue()));
        }
        return effective;
    }

    private static Map<Integer, PlannerCityInfraOverlay> mergeCityInfraOverlays(
            Map<Integer, PlannerCityInfraOverlay> existing,
            Map<Integer, PlannerCityInfraOverlay> incoming,
            Map<Integer, DBNationSnapshot> nextBaseSnapshots
    ) {
        Map<Integer, PlannerCityInfraOverlay> compact = new LinkedHashMap<>();
        for (Map.Entry<Integer, DBNationSnapshot> snapshotEntry : nextBaseSnapshots.entrySet()) {
            int nationId = snapshotEntry.getKey();
            PlannerCityInfraOverlay current = existing.get(nationId);
            PlannerCityInfraOverlay update = incoming.get(nationId);
            PlannerCityInfraOverlay merged = current == null ? update : (update == null ? current : current.merge(update));
            if (merged == null) {
                continue;
            }
            PlannerCityInfraOverlay compactedOverlay = merged.compactAgainst(snapshotEntry.getValue().cityInfra());
            if (!compactedOverlay.isEmpty()) {
                compact.put(nationId, compactedOverlay);
            }
        }
        return compact;
    }
}
