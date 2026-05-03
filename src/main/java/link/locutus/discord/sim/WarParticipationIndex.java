package link.locutus.discord.sim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Collections;
import java.util.List;

/**
 * World-owned index for active war participation and same-opponent redeclare lockout state.
 */
final class WarParticipationIndex {
    static final int RECENT_OPPONENT_LOCKOUT_TURNS = WarSlotRules.sameOpponentLockoutTurns();

    private final Int2ObjectOpenHashMap<IntLinkedOpenHashSet> activeWarIdsByNation = new Int2ObjectOpenHashMap<>();
    private final LongOpenHashSet activePairKeys = new LongOpenHashSet();
    private final Long2IntOpenHashMap pairUnlockTurnByKey = new Long2IntOpenHashMap();

    void onWarAdded(SimWar war) {
        long pairKey = pairKey(war.attackerNationId(), war.defenderNationId());
        if (!activePairKeys.add(pairKey)) {
            throw new IllegalStateException(
                    "Active pair conflict already indexed for nations "
                            + war.attackerNationId() + " and " + war.defenderNationId()
            );
        }
        activeWarIdsByNation
            .computeIfAbsent(war.attackerNationId(), ignored -> new IntLinkedOpenHashSet())
                .add(war.warId());
        activeWarIdsByNation
            .computeIfAbsent(war.defenderNationId(), ignored -> new IntLinkedOpenHashSet())
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
        IntLinkedOpenHashSet warIds = activeWarIdsByNation.get(nationId);
        return warIds != null && !warIds.isEmpty();
    }

    List<Integer> activeWarIdsForNation(int nationId) {
        IntLinkedOpenHashSet warIds = activeWarIdsByNation.get(nationId);
        if (warIds == null || warIds.isEmpty()) {
            return Collections.emptyList();
        }
        IntList orderedWarIds = new IntArrayList(warIds);
        return IntLists.unmodifiable(orderedWarIds);
    }

    boolean hasActivePairConflict(int nationIdA, int nationIdB) {
        return activePairKeys.contains(pairKey(nationIdA, nationIdB));
    }

    boolean isSameOpponentLockoutActive(int nationIdA, int nationIdB, int currentTurn) {
        long pairKey = pairKey(nationIdA, nationIdB);
        if (!pairUnlockTurnByKey.containsKey(pairKey)) {
            return false;
        }
        int unlockTurn = pairUnlockTurnByKey.get(pairKey);
        if (currentTurn >= unlockTurn) {
            pairUnlockTurnByKey.remove(pairKey);
            return false;
        }
        return true;
    }

    WarParticipationIndex deepCopy() {
        WarParticipationIndex copy = new WarParticipationIndex();
        for (Int2ObjectMap.Entry<IntLinkedOpenHashSet> entry : activeWarIdsByNation.int2ObjectEntrySet()) {
            copy.activeWarIdsByNation.put(entry.getIntKey(), new IntLinkedOpenHashSet(entry.getValue()));
        }
        copy.activePairKeys.addAll(activePairKeys);
        copy.pairUnlockTurnByKey.putAll(pairUnlockTurnByKey);
        return copy;
    }

    private void removeActiveWarId(int nationId, int warId) {
        IntLinkedOpenHashSet warIds = activeWarIdsByNation.get(nationId);
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