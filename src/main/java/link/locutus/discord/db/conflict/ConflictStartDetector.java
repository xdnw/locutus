package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Detects likely start turns for a conflict from a single batched war lookup.
 *
 * <p>The trigger is based on distinct attacker nations declaring wars onto the
 * opposing coalition during a turn. We deliberately do not use raw war counts
 * for the trigger because one nation can open multiple wars in the same turn
 * and would otherwise create noisy false positives.
 *
 * <p>Once the first qualifying turn is found, the detector keeps nearby
 * qualifying turns for a short window so the web UI can surface a small set of
 * plausible candidates without another database roundtrip.
 */
public final class ConflictStartDetector {
    /** Lower cut-off (matches the wiki import cut-off). */
    private static final long MIN_START_MS = 1577836800000L;
    /** Minimum distinct declaring nations on one coalition for the first hit. */
    public static final int MIN_TRIGGER_NATIONS = 3;
    /** Keep nearby candidates so the frontend can tab between plausible starts. */
    public static final long CANDIDATE_WINDOW_TURNS = 6;
    /** Upper bound on candidates returned to callers. */
    private static final int MAX_CANDIDATES = 6;
    /** Confirmation tokens stay valid long enough for a normal review/apply flow. */
    private static final PassiveExpiringMap<UUID, PendingSelection> PENDING_SELECTIONS =
            new PassiveExpiringMap<>(30, TimeUnit.MINUTES);

    public static final class AllianceSummary {
        private final int allianceId;
        private final int nations;
        private final int declarations;

        AllianceSummary(int allianceId, int nations, int declarations) {
            this.allianceId = allianceId;
            this.nations = nations;
            this.declarations = declarations;
        }

        public int allianceId() {
            return allianceId;
        }

        public int nations() {
            return nations;
        }

        public int declarations() {
            return declarations;
        }
    }

    public static final class Candidate {
        private final long turn;
        private final int coal1Nations;
        private final int coal2Nations;
        private final int coal1Declarations;
        private final int coal2Declarations;
        private final List<AllianceSummary> coal1Alliances;
        private final List<AllianceSummary> coal2Alliances;

        Candidate(long turn, int coal1Nations, int coal2Nations,
                  int coal1Declarations, int coal2Declarations,
                  List<AllianceSummary> coal1Alliances,
                  List<AllianceSummary> coal2Alliances) {
            this.turn = turn;
            this.coal1Nations = coal1Nations;
            this.coal2Nations = coal2Nations;
            this.coal1Declarations = coal1Declarations;
            this.coal2Declarations = coal2Declarations;
            this.coal1Alliances = Collections.unmodifiableList(coal1Alliances);
            this.coal2Alliances = Collections.unmodifiableList(coal2Alliances);
        }

        public long turn() {
            return turn;
        }

        public long turnMs() {
            return TimeUtil.getTimeFromTurn(turn);
        }

        public int coal1Nations() {
            return coal1Nations;
        }

        public int coal2Nations() {
            return coal2Nations;
        }

        public int coal1Declarations() {
            return coal1Declarations;
        }

        public int coal2Declarations() {
            return coal2Declarations;
        }

        public int totalDeclarations() {
            return coal1Declarations + coal2Declarations;
        }

        public int peakNations() {
            return Math.max(coal1Nations, coal2Nations);
        }

        public List<AllianceSummary> coal1Alliances() {
            return coal1Alliances;
        }

        public List<AllianceSummary> coal2Alliances() {
            return coal2Alliances;
        }
    }

    public static final class Result {
        private final long currentStartTurn;
        private final long searchedFromTurn;
        private final List<Candidate> candidates;

        Result(long currentStartTurn, long searchedFromTurn, List<Candidate> candidates) {
            this.currentStartTurn = currentStartTurn;
            this.searchedFromTurn = searchedFromTurn;
            this.candidates = Collections.unmodifiableList(candidates);
        }

        public long currentStartTurn() {
            return currentStartTurn;
        }

        public long searchedFromTurn() {
            return searchedFromTurn;
        }

        public long currentStartMs() {
            return TimeUtil.getTimeFromTurn(currentStartTurn);
        }

        public long searchedFromMs() {
            return TimeUtil.getTimeFromTurn(searchedFromTurn);
        }

        public List<Candidate> candidates() {
            return candidates;
        }
    }

    private record PendingSelection(String conflictKey, List<Candidate> candidates) {
    }

    @FunctionalInterface
    interface AllianceWindow {
        boolean isActive(int allianceId, long turn);
    }

    private ConflictStartDetector() {
    }

    /**
     * Run the detector using one batched war lookup from the database.
     */
    public static Result detect(Conflict conflict, int turnAllowance) {
        long currentStart = conflict.getStartTurn();
        long allowance = Math.max(0, turnAllowance);
        long minTurn = TimeUtil.getTurn(MIN_START_MS);
        long fromTurn = Math.max(minTurn, currentStart - allowance);
        long endTurn = conflict.getEndTurn();

        Set<Integer> coal1 = conflict.getCoalition1();
        Set<Integer> coal2 = conflict.getCoalition2();
        if (coal1.isEmpty() || coal2.isEmpty()) {
            return new Result(currentStart, fromTurn, Collections.emptyList());
        }

        long endMs = endTurn == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(endTurn);
        // WarDB.getWars uses `date > start`, so subtract 1 to include wars that
        // land exactly on the starting turn boundary.
        long fromMs = TimeUtil.getTimeFromTurn(fromTurn) - 1L;

        return detect(conflict, turnAllowance, Locutus.imp().getWarDb().getWars(
                coal1, Collections.emptySet(),
                coal2, Collections.emptySet(),
                fromMs, endMs).values());
    }

    /**
     * Package-private seam so tests can exercise the detector without touching
     * the global Locutus runtime.
     */
    static Result detect(Conflict conflict, int turnAllowance, Collection<DBWar> wars) {
        Set<Integer> coal1 = conflict.getCoalition1();
        Set<Integer> coal2 = conflict.getCoalition2();
        return detect(
                conflict.getStartTurn(),
                conflict.getEndTurn(),
                coal1,
                coal2,
                (allianceId, turn) -> isAllianceActive(conflict, allianceId, turn),
                turnAllowance,
                wars);
    }

    static Result detect(long currentStartTurn, long endTurn,
                         Set<Integer> coal1, Set<Integer> coal2,
                         AllianceWindow allianceWindow,
                         int turnAllowance,
                         Collection<DBWar> wars) {
        long allowance = Math.max(0, turnAllowance);
        long minTurn = TimeUtil.getTurn(MIN_START_MS);
        long fromTurn = Math.max(minTurn, currentStartTurn - allowance);

        if (coal1.isEmpty() || coal2.isEmpty()) {
            return new Result(currentStartTurn, fromTurn, Collections.emptyList());
        }

        Long2ObjectOpenHashMap<TurnBreakdown> byTurn = new Long2ObjectOpenHashMap<>();
        for (DBWar war : wars) {
            long warTurn = TimeUtil.getTurn(war.getDate());
            if (warTurn < fromTurn || warTurn >= endTurn) {
                continue;
            }

            int attackerAlliance = war.getAttacker_aa();
            int defenderAlliance = war.getDefender_aa();
            boolean attackerIsCoal1 = coal1.contains(attackerAlliance) && coal2.contains(defenderAlliance);
            boolean attackerIsCoal2 = !attackerIsCoal1 && coal2.contains(attackerAlliance) && coal1.contains(defenderAlliance);
            if (!attackerIsCoal1 && !attackerIsCoal2) {
                continue;
            }

            // Only count wars where both concrete alliances are active conflict
            // participants at the declaration turn.
            if (!allianceWindow.isActive(attackerAlliance, warTurn)
                    || !allianceWindow.isActive(defenderAlliance, warTurn)) {
                continue;
            }

            TurnBreakdown breakdown = byTurn.computeIfAbsent(warTurn, ignored -> new TurnBreakdown());
            breakdown.record(attackerIsCoal1, attackerAlliance, war.getAttacker_id());
        }

        long[] turns = byTurn.keySet().toLongArray();
        java.util.Arrays.sort(turns);

        List<Candidate> candidates = new ArrayList<>(Math.min(MAX_CANDIDATES, turns.length));
        long firstCandidateTurn = Long.MIN_VALUE;
        for (long turn : turns) {
            TurnBreakdown breakdown = byTurn.get(turn);
            if (breakdown == null || breakdown.peakNations() < MIN_TRIGGER_NATIONS) {
                continue;
            }
            if (firstCandidateTurn == Long.MIN_VALUE) {
                firstCandidateTurn = turn;
            } else if (turn > firstCandidateTurn + CANDIDATE_WINDOW_TURNS) {
                break;
            }

            candidates.add(breakdown.toCandidate(turn));
            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }

        return new Result(currentStartTurn, fromTurn, candidates);
    }

    public static UUID createToken(Conflict conflict, Result result) {
        if (result.candidates().isEmpty()) {
            throw new IllegalArgumentException("No conflict start candidates are available");
        }
        UUID token = UUID.randomUUID();
        PENDING_SELECTIONS.put(token, new PendingSelection(conflictKey(conflict), result.candidates()));
        return token;
    }

    public static Candidate resolveCandidate(Conflict conflict, UUID token, Long selectedTurn) {
        if (token == null) {
            throw new IllegalArgumentException("A detection token is required");
        }
        PendingSelection pending = PENDING_SELECTIONS.get(token);
        if (pending == null) {
            throw new IllegalArgumentException("The detection token is invalid or expired. Please detect again.");
        }
        if (!pending.conflictKey().equals(conflictKey(conflict))) {
            throw new IllegalArgumentException("That detection token does not belong to this conflict.");
        }
        if (pending.candidates().isEmpty()) {
            throw new IllegalArgumentException("That detection token has no candidates.");
        }
        if (selectedTurn == null) {
            return pending.candidates().get(0);
        }
        for (Candidate candidate : pending.candidates()) {
            if (candidate.turn() == selectedTurn.longValue()) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Selected turn `" + selectedTurn + "` is not one of the surfaced candidates.");
    }

    private static boolean isAllianceActive(Conflict conflict, int allianceId, long turn) {
        return allianceId > 0
                && conflict.getStartTurn(allianceId) <= turn
                && conflict.getEndTurn(allianceId) > turn;
    }

    private static String conflictKey(Conflict conflict) {
        ConflictUtil.VirtualConflictId virtualId = conflict.getVirtualConflictId();
        if (virtualId != null) {
            return "virtual:" + virtualId.toWebId();
        }
        return "conflict:" + conflict.getId();
    }

    private static final class TurnBreakdown {
        private final SideBreakdown coal1 = new SideBreakdown();
        private final SideBreakdown coal2 = new SideBreakdown();

        void record(boolean isCoal1, int allianceId, int attackerId) {
            (isCoal1 ? coal1 : coal2).record(allianceId, attackerId);
        }

        int peakNations() {
            return Math.max(coal1.nationCount(), coal2.nationCount());
        }

        Candidate toCandidate(long turn) {
            return new Candidate(
                    turn,
                    coal1.nationCount(),
                    coal2.nationCount(),
                    coal1.declarationCount(),
                    coal2.declarationCount(),
                    coal1.toAllianceSummaries(),
                    coal2.toAllianceSummaries());
        }
    }

    private static final class SideBreakdown {
        private static final Comparator<AllianceSummary> ALLIANCE_ORDER =
                Comparator.comparingInt(AllianceSummary::declarations).reversed()
                        .thenComparing(Comparator.comparingInt(AllianceSummary::nations).reversed())
                        .thenComparingInt(AllianceSummary::allianceId);

        private final IntOpenHashSet nations = new IntOpenHashSet();
        private final Int2ObjectOpenHashMap<AllianceBreakdown> alliances = new Int2ObjectOpenHashMap<>();
        private int declarations;

        void record(int allianceId, int attackerId) {
            declarations++;
            nations.add(attackerId);
            alliances.computeIfAbsent(allianceId, ignored -> new AllianceBreakdown()).record(attackerId);
        }

        int nationCount() {
            return nations.size();
        }

        int declarationCount() {
            return declarations;
        }

        List<AllianceSummary> toAllianceSummaries() {
            if (alliances.isEmpty()) {
                return Collections.emptyList();
            }
            List<AllianceSummary> result = new ArrayList<>(alliances.size());
            for (Int2ObjectMap.Entry<AllianceBreakdown> entry : alliances.int2ObjectEntrySet()) {
                AllianceBreakdown breakdown = entry.getValue();
                result.add(new AllianceSummary(entry.getIntKey(), breakdown.nations.size(), breakdown.declarations));
            }
            result.sort(ALLIANCE_ORDER);
            return result;
        }
    }

    private static final class AllianceBreakdown {
        private final IntOpenHashSet nations = new IntOpenHashSet();
        private int declarations;

        void record(int attackerId) {
            declarations++;
            nations.add(attackerId);
        }
    }
}
