package link.locutus.discord.sim.planners;

import java.util.List;
import java.util.Map;

record PlannerProjectionResult(
        Map<Integer, DBNationSnapshot> snapshotsById,
        List<PlannerProjectedWar> activeWars,
        Map<Integer, PlannerCityInfraOverlay> cityInfraOverlaysByNation
) {
    PlannerProjectionResult {
        snapshotsById = Map.copyOf(snapshotsById);
        activeWars = List.copyOf(activeWars);
        cityInfraOverlaysByNation = Map.copyOf(cityInfraOverlaysByNation);
    }
}
