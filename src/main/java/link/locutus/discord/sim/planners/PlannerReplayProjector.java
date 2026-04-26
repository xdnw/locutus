package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.SimTuning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlannerReplayProjector {
    private PlannerReplayProjector() {
    }

    public static PlannerReplayTrace capture(
            SimTuning tuning,
            OverrideSet overrides,
            Collection<DBNationSnapshot> nations,
            Map<Integer, List<Integer>> assignment,
            int currentTurn,
            int horizonTurns,
            boolean includeResources
    ) {
        PlannerLocalConflict conflict = PlannerLocalConflict.createWithActiveWars(
                overrides,
                nations,
                List.of(),
                currentTurn,
                tuning,
                PlannerTransitionSemantics.NONE
        );

        PlannerProjectionResult initialProjection = conflict.project();
        PlannerReplayTrace.Frame initialFrame = new PlannerReplayTrace.Frame(
                conflict.currentTurn(),
                nationStates(initialProjection.snapshotsById(), includeResources),
                activeWarStates(conflict.replayWarStatesByPair())
        );

        Map<Integer, DBNationSnapshot> previousSnapshots = initialProjection.snapshotsById();
        Map<Long, PlannerReplayTrace.WarState> previousWars = conflict.replayWarStatesByPair();
        List<PlannerReplayTrace.Delta> deltas = new ArrayList<>();
        int turns = Math.max(1, horizonTurns);
        for (int turnIndex = 0; turnIndex < turns; turnIndex++) {
            conflict.applyReplayTurn(assignment, turnIndex == 0);
            PlannerProjectionResult projection = conflict.project();
            Map<Integer, DBNationSnapshot> currentSnapshots = projection.snapshotsById();
            Map<Long, PlannerReplayTrace.WarState> currentWars = conflict.replayWarStatesByPair();

            PlannerReplayTrace.NationState[] changedNations = changedNationStates(previousSnapshots, currentSnapshots, includeResources);
            PlannerReplayTrace.WarState[] changedWars = changedActiveWarStates(previousWars, currentWars);
            PlannerReplayTrace.DeclaredWar[] declaredWars = declaredWars(previousWars, currentWars);
            PlannerReplayTrace.ConcludedWar[] concludedWars = concludedWars(previousWars, currentWars);
            if (changedNations.length > 0 || changedWars.length > 0 || declaredWars.length > 0 || concludedWars.length > 0) {
                deltas.add(new PlannerReplayTrace.Delta(
                        conflict.currentTurn(),
                        changedNations,
                        changedWars,
                        declaredWars,
                        concludedWars
                ));
            }
            previousSnapshots = currentSnapshots;
            previousWars = currentWars;
        }

        return new PlannerReplayTrace(initialFrame, deltas.toArray(PlannerReplayTrace.Delta[]::new));
    }

    private static PlannerReplayTrace.NationState[] nationStates(
            Map<Integer, DBNationSnapshot> snapshotsById,
            boolean includeResources
    ) {
        return snapshotsById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> nationState(entry.getValue(), includeResources))
                .toArray(PlannerReplayTrace.NationState[]::new);
    }

    private static PlannerReplayTrace.NationState nationState(DBNationSnapshot snapshot, boolean includeResources) {
        MilitaryUnit[] units = MilitaryUnit.values();
        int[] unitCounts = new int[units.length];
        for (MilitaryUnit unit : units) {
            unitCounts[unit.ordinal()] = snapshot.unit(unit);
        }
        double[] resources = includeResources ? snapshot.resources() : new double[0];
        return new PlannerReplayTrace.NationState(
                snapshot.nationId(),
                unitCounts,
                snapshot.cityInfra(),
                snapshot.score(),
                snapshot.beigeTurns(),
                resources
        );
    }

    private static PlannerReplayTrace.WarState[] activeWarStates(Map<Long, PlannerReplayTrace.WarState> allWars) {
        return allWars.values().stream()
                .filter(PlannerReplayProjector::isActive)
                .sorted(Comparator.comparingInt(PlannerReplayTrace.WarState::declarerNationId)
                        .thenComparingInt(PlannerReplayTrace.WarState::targetNationId))
                .toArray(PlannerReplayTrace.WarState[]::new);
    }

    private static PlannerReplayTrace.NationState[] changedNationStates(
            Map<Integer, DBNationSnapshot> previousSnapshots,
            Map<Integer, DBNationSnapshot> currentSnapshots,
            boolean includeResources
    ) {
        List<PlannerReplayTrace.NationState> changed = new ArrayList<>();
        for (Map.Entry<Integer, DBNationSnapshot> entry : currentSnapshots.entrySet()) {
            DBNationSnapshot previous = previousSnapshots.get(entry.getKey());
            DBNationSnapshot current = entry.getValue();
            if (previous == null || nationChanged(previous, current, includeResources)) {
                changed.add(nationState(current, includeResources));
            }
        }
        changed.sort(Comparator.comparingInt(PlannerReplayTrace.NationState::nationId));
        return changed.toArray(PlannerReplayTrace.NationState[]::new);
    }

    private static boolean nationChanged(DBNationSnapshot previous, DBNationSnapshot current, boolean includeResources) {
        if (Double.compare(previous.score(), current.score()) != 0 || previous.beigeTurns() != current.beigeTurns()) {
            return true;
        }
        for (MilitaryUnit unit : MilitaryUnit.values()) {
            if (previous.unit(unit) != current.unit(unit)) {
                return true;
            }
        }
        double[] previousInfra = previous.cityInfraRaw();
        double[] currentInfra = current.cityInfraRaw();
        if (previousInfra.length != currentInfra.length) {
            return true;
        }
        for (int i = 0; i < previousInfra.length; i++) {
            if (Double.compare(previousInfra[i], currentInfra[i]) != 0) {
                return true;
            }
        }
        if (!includeResources) {
            return false;
        }
        for (ResourceType resource : ResourceType.values) {
            if (Double.compare(previous.resource(resource), current.resource(resource)) != 0) {
                return true;
            }
        }
        return false;
    }

    private static PlannerReplayTrace.WarState[] changedActiveWarStates(
            Map<Long, PlannerReplayTrace.WarState> previousWars,
            Map<Long, PlannerReplayTrace.WarState> currentWars
    ) {
        List<PlannerReplayTrace.WarState> changed = new ArrayList<>();
        for (Map.Entry<Long, PlannerReplayTrace.WarState> entry : currentWars.entrySet()) {
            PlannerReplayTrace.WarState current = entry.getValue();
            if (!isActive(current)) {
                continue;
            }
            PlannerReplayTrace.WarState previous = previousWars.get(entry.getKey());
            if (!current.equals(previous)) {
                changed.add(current);
            }
        }
        changed.sort(Comparator.comparingInt(PlannerReplayTrace.WarState::declarerNationId)
                .thenComparingInt(PlannerReplayTrace.WarState::targetNationId));
        return changed.toArray(PlannerReplayTrace.WarState[]::new);
    }

    private static PlannerReplayTrace.DeclaredWar[] declaredWars(
            Map<Long, PlannerReplayTrace.WarState> previousWars,
            Map<Long, PlannerReplayTrace.WarState> currentWars
    ) {
        List<PlannerReplayTrace.DeclaredWar> declared = new ArrayList<>();
        for (Map.Entry<Long, PlannerReplayTrace.WarState> entry : currentWars.entrySet()) {
            if (previousWars.containsKey(entry.getKey())) {
                continue;
            }
            PlannerReplayTrace.WarState war = entry.getValue();
            declared.add(new PlannerReplayTrace.DeclaredWar(
                    war.declarerNationId(),
                    war.targetNationId(),
                    war.warTypeOrdinal(),
                    war.startTurn()
            ));
        }
        declared.sort(Comparator.comparingInt(PlannerReplayTrace.DeclaredWar::declarerNationId)
                .thenComparingInt(PlannerReplayTrace.DeclaredWar::targetNationId));
        return declared.toArray(PlannerReplayTrace.DeclaredWar[]::new);
    }

    private static PlannerReplayTrace.ConcludedWar[] concludedWars(
            Map<Long, PlannerReplayTrace.WarState> previousWars,
            Map<Long, PlannerReplayTrace.WarState> currentWars
    ) {
        List<PlannerReplayTrace.ConcludedWar> concluded = new ArrayList<>();
        for (Map.Entry<Long, PlannerReplayTrace.WarState> entry : currentWars.entrySet()) {
            PlannerReplayTrace.WarState previous = previousWars.get(entry.getKey());
            PlannerReplayTrace.WarState current = entry.getValue();
            if (previous == null || !isActive(previous) || isActive(current)) {
                continue;
            }
            concluded.add(new PlannerReplayTrace.ConcludedWar(
                    current.declarerNationId(),
                    current.targetNationId(),
                    current.statusOrdinal()
            ));
        }
        concluded.sort(Comparator.comparingInt(PlannerReplayTrace.ConcludedWar::declarerNationId)
                .thenComparingInt(PlannerReplayTrace.ConcludedWar::targetNationId));
        return concluded.toArray(PlannerReplayTrace.ConcludedWar[]::new);
    }

    private static boolean isActive(PlannerReplayTrace.WarState warState) {
        return WarStatus.values[warState.statusOrdinal()].isActive();
    }
}