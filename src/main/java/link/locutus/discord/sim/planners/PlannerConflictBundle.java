package link.locutus.discord.sim.planners;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Planner-local changed bundle extracted from a current/candidate assignment pair.
 *
 * <p>The bundle is the connected component of the changed attackers in the union of the
 * current and candidate assignment graphs. That keeps exact validation local without
 * dropping linked war outcomes that can affect the score delta.</p>
 */
record PlannerConflictBundle(
        List<DBNationSnapshot> attackers,
        List<DBNationSnapshot> defenders,
        Map<Integer, List<Integer>> currentAssignment,
        Map<Integer, List<Integer>> candidateAssignment
) {
    PlannerConflictBundle {
        attackers = List.copyOf(attackers);
        defenders = List.copyOf(defenders);
        currentAssignment = copyAssignment(currentAssignment);
        candidateAssignment = copyAssignment(candidateAssignment);
    }

    static PlannerConflictBundle extract(
            Map<Integer, List<Integer>> currentAssignment,
            Map<Integer, List<Integer>> candidateAssignment,
            Collection<DBNationSnapshot> attackers,
            Collection<DBNationSnapshot> defenders
    ) {
        Objects.requireNonNull(currentAssignment, "currentAssignment");
        Objects.requireNonNull(candidateAssignment, "candidateAssignment");
        Objects.requireNonNull(attackers, "attackers");
        Objects.requireNonNull(defenders, "defenders");

        LinkedHashMap<Integer, DBNationSnapshot> attackerById = indexSnapshots(attackers);
        LinkedHashMap<Integer, DBNationSnapshot> defenderById = indexSnapshots(defenders);

        Set<Integer> impactedNationIds = extractImpactedNationIds(
                currentAssignment,
                candidateAssignment,
                attackerById.keySet(),
                defenderById.keySet()
        );

        if (impactedNationIds.isEmpty()) {
            return new PlannerConflictBundle(List.of(), List.of(), Map.of(), Map.of());
        }

        List<DBNationSnapshot> bundleAttackers = filterSnapshots(attackers, impactedNationIds);
        List<DBNationSnapshot> bundleDefenders = filterSnapshots(defenders, impactedNationIds);
        Map<Integer, List<Integer>> bundleCurrent = restrictAssignment(currentAssignment, impactedNationIds);
        Map<Integer, List<Integer>> bundleCandidate = restrictAssignment(candidateAssignment, impactedNationIds);

        return new PlannerConflictBundle(bundleAttackers, bundleDefenders, bundleCurrent, bundleCandidate);
    }

    boolean isEmpty() {
        return attackers.isEmpty() && defenders.isEmpty();
    }

    private static LinkedHashMap<Integer, DBNationSnapshot> indexSnapshots(Collection<DBNationSnapshot> snapshots) {
        LinkedHashMap<Integer, DBNationSnapshot> indexed = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            indexed.put(snapshot.nationId(), snapshot);
        }
        return indexed;
    }

    private static Set<Integer> extractImpactedNationIds(
            Map<Integer, List<Integer>> currentAssignment,
            Map<Integer, List<Integer>> candidateAssignment,
            Set<Integer> attackerIds,
            Set<Integer> defenderIds
    ) {
        Map<Integer, Set<Integer>> attackerToDefenders = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> defenderToAttackers = new LinkedHashMap<>();
        indexAssignment(currentAssignment, attackerToDefenders, defenderToAttackers);
        indexAssignment(candidateAssignment, attackerToDefenders, defenderToAttackers);

        LinkedHashSet<Integer> seeds = new LinkedHashSet<>();
        LinkedHashSet<Integer> attackerOrder = new LinkedHashSet<>();
        attackerOrder.addAll(currentAssignment.keySet());
        attackerOrder.addAll(candidateAssignment.keySet());
        for (Integer attackerId : attackerOrder) {
            if (!Objects.equals(currentAssignment.get(attackerId), candidateAssignment.get(attackerId))
                    && attackerIds.contains(attackerId)) {
                seeds.add(attackerId);
            }
        }

        if (seeds.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Integer> impacted = new LinkedHashSet<>(seeds);
        Deque<Integer> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            int nodeId = queue.removeFirst();
            if (attackerIds.contains(nodeId)) {
                for (Integer defenderId : attackerToDefenders.getOrDefault(nodeId, Set.of())) {
                    if (impacted.add(defenderId)) {
                        queue.addLast(defenderId);
                    }
                }
            }
            if (defenderIds.contains(nodeId)) {
                for (Integer attackerId : defenderToAttackers.getOrDefault(nodeId, Set.of())) {
                    if (impacted.add(attackerId)) {
                        queue.addLast(attackerId);
                    }
                }
            }
        }
        return impacted;
    }

    private static void indexAssignment(
            Map<Integer, List<Integer>> assignment,
            Map<Integer, Set<Integer>> attackerToDefenders,
            Map<Integer, Set<Integer>> defenderToAttackers
    ) {
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerId = entry.getKey();
            List<Integer> defenderIds = entry.getValue();
            if (attackerId == null || defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            Set<Integer> defenders = attackerToDefenders.computeIfAbsent(attackerId, ignored -> new LinkedHashSet<>());
            for (Integer defenderId : defenderIds) {
                if (defenderId == null) {
                    continue;
                }
                defenders.add(defenderId);
                defenderToAttackers.computeIfAbsent(defenderId, ignored -> new LinkedHashSet<>()).add(attackerId);
            }
        }
    }

    private static List<DBNationSnapshot> filterSnapshots(
            Collection<DBNationSnapshot> snapshots,
            Set<Integer> impactedNationIds
    ) {
        List<DBNationSnapshot> filtered = new ArrayList<>();
        for (DBNationSnapshot snapshot : snapshots) {
            if (impactedNationIds.contains(snapshot.nationId())) {
                filtered.add(snapshot);
            }
        }
        return List.copyOf(filtered);
    }

    private static Map<Integer, List<Integer>> restrictAssignment(
            Map<Integer, List<Integer>> assignment,
            Set<Integer> impactedNationIds
    ) {
        if (assignment.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Integer, List<Integer>> filtered = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerId = entry.getKey();
            if (attackerId == null || !impactedNationIds.contains(attackerId)) {
                continue;
            }
            List<Integer> defenderIds = entry.getValue();
            if (defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            LinkedHashSet<Integer> compacted = new LinkedHashSet<>();
            for (Integer defenderId : defenderIds) {
                if (defenderId != null && impactedNationIds.contains(defenderId)) {
                    compacted.add(defenderId);
                }
            }
            if (!compacted.isEmpty()) {
                filtered.put(attackerId, List.copyOf(compacted));
            }
        }
        if (filtered.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(filtered);
    }

    private static Map<Integer, List<Integer>> copyAssignment(Map<Integer, List<Integer>> assignment) {
        if (assignment.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Integer, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignment.entrySet()) {
            Integer attackerId = entry.getKey();
            List<Integer> defenderIds = entry.getValue();
            if (attackerId == null || defenderIds == null || defenderIds.isEmpty()) {
                continue;
            }
            copy.put(attackerId, List.copyOf(defenderIds));
        }
        if (copy.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(copy);
    }
}