package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.SimTuning;

import java.util.Collection;
import java.util.List;
import java.util.Map;

final class ScheduledPlannerState {
    private final PlannerProjectionState state;

    private ScheduledPlannerState(PlannerProjectionState state) {
        this.state = state;
    }

    static ScheduledPlannerState seed(OverrideSet overrides, Collection<DBNationSnapshot> snapshots) {
        return seed(overrides, snapshots, List.of(), 0);
    }

    static ScheduledPlannerState seed(
            OverrideSet overrides,
            Collection<DBNationSnapshot> snapshots,
            Collection<PlannerProjectedWar> activeWars
    ) {
        return seed(overrides, snapshots, activeWars, 0);
    }

    static ScheduledPlannerState seed(
            OverrideSet overrides,
            Collection<DBNationSnapshot> snapshots,
            Collection<PlannerProjectedWar> activeWars,
            int currentTurn
    ) {
        return new ScheduledPlannerState(PlannerProjectionState.seed(overrides, snapshots, activeWars, currentTurn));
    }

    List<DBNationSnapshot> snapshotsFor(Collection<Integer> nationIds) {
        return state.snapshotsFor(nationIds);
    }

    ScheduledPlannerState advance(
            SimTuning tuning,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns
    ) {
        return advance(tuning, assignment, horizonTurns, PlannerTransitionSemantics.NONE);
    }

    ScheduledPlannerState advance(
            SimTuning tuning,
            Map<Integer, List<Integer>> assignment,
            int horizonTurns,
            PlannerTransitionSemantics transitionSemantics
    ) {
        return new ScheduledPlannerState(state.advance(tuning, assignment, horizonTurns, transitionSemantics));
    }

    Map<Long, PlannerProjectedWar> activePlannedWars() {
        return state.activePlannedWarsByPair();
    }

    Map<Integer, PlannerCityInfraOverlay> cityInfraOverlaysByNation() {
        return state.cityInfraOverlaysByNation();
    }

    int currentTurn() {
        return state.currentTurn();
    }
}
