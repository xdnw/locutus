package link.locutus.discord.sim.planners;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import link.locutus.discord.sim.StrategicAssetValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Planner-local adapter from snapshot collections to the canonical strategic value owner.
 */
final class PlannerStrategicValue {
    private PlannerStrategicValue() {
    }

    static Map<Integer, StrategicAssetValue.StrategicRelevance> relevanceByNationId(
            Collection<DBNationSnapshot> primary,
            Collection<DBNationSnapshot> secondary
    ) {
        return relevanceByNationId(orderedUniqueNations(primary, secondary));
    }

    static Map<Integer, StrategicAssetValue.StrategicRelevance> relevanceByNationId(
            Collection<DBNationSnapshot> snapshots
    ) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
        List<DBNationSnapshot> orderedSnapshots = List.copyOf(snapshots);
        Map<Integer, StrategicAssetValue.StrategicRelevance> relevanceByNationId =
                new Int2ObjectLinkedOpenHashMap<>(orderedSnapshots.size());
        for (DBNationSnapshot snapshot : orderedSnapshots) {
            List<DBNationSnapshot> opposingNations = new ArrayList<>(orderedSnapshots.size());
            for (DBNationSnapshot other : orderedSnapshots) {
                if (other.teamId() != snapshot.teamId()) {
                    opposingNations.add(other);
                }
            }
            relevanceByNationId.put(snapshot.nationId(), relevance(snapshot, opposingNations));
        }
        return Map.copyOf(relevanceByNationId);
    }

    static StrategicAssetValue.StrategicRelevance relevance(
            DBNationSnapshot snapshot,
            Collection<DBNationSnapshot> opposingNations
    ) {
        List<DBNationSnapshot> opponents = opposingNations == null ? List.of() : List.copyOf(opposingNations);
        return StrategicAssetValue.relevanceForWarRange(
                snapshot.cities(),
                snapshot.score(),
                snapshot.currentOffensiveWars()
                        + snapshot.currentDefensiveWars()
                        + snapshot.activeOpponentNationIds().size(),
                opponents.size(),
                index -> opponents.get(index).score()
        );
    }

    static StrategicAssetValue.StrategicRelevance relevance(
            DBNationSnapshot snapshot,
            DBNationSnapshot opponent
    ) {
        return relevance(snapshot, opponent == null ? List.of() : List.of(opponent));
    }

    static StrategicAssetValue.StrategicRelevance localRelevance(DBNationSnapshot snapshot) {
        return new StrategicAssetValue.StrategicRelevance(
                snapshot.cities(),
                0,
                0,
                snapshot.activeOpponentNationIds().size()
        );
    }

    static double strategicValue(
            DBNationSnapshot snapshot,
            StrategicAssetValue.StrategicRelevance relevance
    ) {
        StrategicAssetValue.ActiveWarContext activeWarContext = activeWarContext(snapshot);
        return StrategicAssetValue.contextualMilitaryValue(
                snapshot::unit,
                snapshot::pendingBuysNextTurn,
                snapshot::unitsBoughtToday,
                snapshot::dailyBuyCap,
                snapshot.researchBits(),
                activeWarContext,
                relevance
        ).totalValue() + StrategicAssetValue.infrastructureValue(snapshot.cityInfraRaw(), activeWarContext, relevance);
    }

    static double strategicValue(DBNationSnapshot snapshot, Collection<DBNationSnapshot> opposingNations) {
        return strategicValue(snapshot, relevance(snapshot, opposingNations));
    }

    static double strategicValue(DBNationSnapshot snapshot, DBNationSnapshot opponent) {
        return strategicValue(snapshot, relevance(snapshot, opponent));
    }

    static double localStrategicValue(DBNationSnapshot snapshot) {
        return strategicValue(snapshot, localRelevance(snapshot));
    }

    static double marginalActionSpaceValue(DBNationSnapshot snapshot) {
        return localStrategicValue(snapshot)
                * StrategicAssetValue.marginalActionSpaceMultiplier(activeWarContext(snapshot));
    }

    static StrategicAssetValue.ActiveWarContext activeWarContext(DBNationSnapshot snapshot) {
        return StrategicAssetValue.ActiveWarContext.fromSlots(
                snapshot.currentOffensiveWars(),
                snapshot.maxOff(),
                snapshot.currentDefensiveWars(),
                snapshot.activeOpponentNationIds().size()
        );
    }

    private static List<DBNationSnapshot> orderedUniqueNations(
            Collection<DBNationSnapshot> primary,
            Collection<DBNationSnapshot> secondary
    ) {
        Map<Integer, DBNationSnapshot> byId = new Int2ObjectLinkedOpenHashMap<>();
        if (primary != null) {
            for (DBNationSnapshot snapshot : primary) {
                byId.put(snapshot.nationId(), snapshot);
            }
        }
        if (secondary != null) {
            for (DBNationSnapshot snapshot : secondary) {
                byId.putIfAbsent(snapshot.nationId(), snapshot);
            }
        }
        return new ArrayList<>(byId.values());
    }
}
