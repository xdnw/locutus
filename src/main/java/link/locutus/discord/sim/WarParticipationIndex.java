package link.locutus.discord.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World-owned index for active war participation and same-opponent redeclare lockout state.
 */
final class WarParticipationIndex {
    static final int RECENT_OPPONENT_LOCKOUT_TURNS = 12;

    private final Map<Integer, LinkedHashSet<Integer>> activeWarIdsByNation = new HashMap<>();
    private final Set<Long> activePairKeys = new HashSet<>();
    private final Map<Long, Integer> pairUnlockTurnByKey = new HashMap<>();

    void onWarAdded(SimWar war) {
        long pairKey = pairKey(war.attackerNationId(), war.defenderNationId());
        if (!activePairKeys.add(pairKey)) {
            throw new IllegalStateException(
                    "Active pair conflict already indexed for nations "
                            + war.attackerNationId() + " and " + war.defenderNationId()
            );
        }
        activeWarIdsByNation
                .computeIfAbsent(war.attackerNationId(), ignored -> new LinkedHashSet<>())
                .add(war.warId());
        activeWarIdsByNation
                .computeIfAbsent(war.defenderNationId(), ignored -> new LinkedHashSet<>())
                .add(war.warId());
        pairUnlockTurnByKey.remove(pairKey);
    }

    void onWarEnded(SimWar war, int currentTurn) {
        long pairKey = pairKey(war.attackerNationId(), war.defenderNationId());
        activePairKeys.remove(pairKey);
        removeActiveWarId(war.attackerNationId(), war.warId());
        removeActiveWarId(war.defenderNationId(), war.warId());
        pairUnlockTurnByKey.put(pairKey, currentTurn + RECENT_OPPONENT_LOCKOUT_TURNS);
    }

    boolean hasActiveWar(int nationId) {
        LinkedHashSet<Integer> warIds = activeWarIdsByNation.get(nationId);
        return warIds != null && !warIds.isEmpty();
    }

    List<Integer> activeWarIdsForNation(int nationId) {
        LinkedHashSet<Integer> warIds = activeWarIdsByNation.get(nationId);
        if (warIds == null || warIds.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(new ArrayList<>(warIds));
    }

    boolean hasActivePairConflict(int nationIdA, int nationIdB) {
        return activePairKeys.contains(pairKey(nationIdA, nationIdB));
    }

    boolean isSameOpponentLockoutActive(int nationIdA, int nationIdB, int currentTurn) {
        long pairKey = pairKey(nationIdA, nationIdB);
        Integer unlockTurn = pairUnlockTurnByKey.get(pairKey);
        if (unlockTurn == null) {
            return false;
        }
        if (currentTurn >= unlockTurn) {
            pairUnlockTurnByKey.remove(pairKey);
            return false;
        }
        return true;
    }

    WarParticipationIndex deepCopy() {
        WarParticipationIndex copy = new WarParticipationIndex();
        for (Map.Entry<Integer, LinkedHashSet<Integer>> entry : activeWarIdsByNation.entrySet()) {
            copy.activeWarIdsByNation.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        copy.activePairKeys.addAll(activePairKeys);
        copy.pairUnlockTurnByKey.putAll(pairUnlockTurnByKey);
        return copy;
    }

    private void removeActiveWarId(int nationId, int warId) {
        LinkedHashSet<Integer> warIds = activeWarIdsByNation.get(nationId);
        if (warIds == null) {
            return;
        }
        warIds.remove(warId);
        if (warIds.isEmpty()) {
            activeWarIdsByNation.remove(nationId);
        }
    }

    private static long pairKey(int nationIdA, int nationIdB) {
        int lower = Math.min(nationIdA, nationIdB);
        int upper = Math.max(nationIdA, nationIdB);
        return ((long) lower << 32) | (upper & 0xffffffffL);
    }
}