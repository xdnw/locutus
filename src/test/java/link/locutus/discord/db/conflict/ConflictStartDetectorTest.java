package link.locutus.discord.db.conflict;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConflictStartDetectorTest {
    @Test
    void detectCountsDistinctNationsAndIgnoresWarsOutsideParticipantWindows() {
        long baseTurn = TimeUtil.getTurn(1577836800000L) + 100L;
        Map<Integer, long[]> windows = new HashMap<>();
        windows.put(11, new long[] {baseTurn, baseTurn + 100L});
        windows.put(12, new long[] {baseTurn, baseTurn + 100L});
        windows.put(21, new long[] {baseTurn, baseTurn + 100L});
        windows.put(22, new long[] {baseTurn + 5L, baseTurn + 8L});

        List<DBWar> wars = List.of(
                // Below the threshold: only two distinct attacking nations.
                war(1, baseTurn + 1L, 101, 201, 11, 21),
                war(2, baseTurn + 1L, 102, 202, 12, 21),

                // First qualifying turn: three distinct nations on coalition 1,
                // but one nation declares twice so declarations > nations.
                war(3, baseTurn + 2L, 101, 203, 11, 21),
                war(4, baseTurn + 2L, 101, 204, 11, 21),
                war(5, baseTurn + 2L, 102, 205, 12, 21),
                war(6, baseTurn + 2L, 103, 206, 12, 21),

                // Nearby qualifying turn on the opposite coalition.
                war(7, baseTurn + 4L, 201, 101, 21, 11),
                war(8, baseTurn + 4L, 202, 102, 21, 12),
                war(9, baseTurn + 4L, 203, 103, 21, 11),

                // Would qualify on declarations, but defender alliance 22 is not
                // active in the conflict yet, so it must be ignored.
                war(10, baseTurn + 4L, 104, 301, 11, 22),
                war(11, baseTurn + 4L, 105, 302, 11, 22),
                war(12, baseTurn + 4L, 106, 303, 12, 22),

                // Outside the nearby-candidate window.
                war(13, baseTurn + 12L, 301, 101, 21, 11),
                war(14, baseTurn + 12L, 302, 102, 21, 12),
                war(15, baseTurn + 12L, 303, 103, 21, 11)
        );

        ConflictStartDetector.Result result = ConflictStartDetector.detect(
                baseTurn,
                baseTurn + 100L,
                Set.of(11, 12),
                Set.of(21, 22),
                (allianceId, turn) -> {
                    long[] window = windows.get(allianceId);
                    return window != null && window[0] <= turn && turn < window[1];
                },
                0,
                wars);

        assertEquals(2, result.candidates().size());

        ConflictStartDetector.Candidate first = result.candidates().get(0);
        assertEquals(baseTurn + 2L, first.turn());
        assertEquals(3, first.coal1Nations());
        assertEquals(0, first.coal2Nations());
        assertEquals(4, first.coal1Declarations());
        assertEquals(4, first.totalDeclarations());
        assertEquals(2, first.coal1Alliances().size());
        assertEquals(12, first.coal1Alliances().get(0).allianceId());
        assertEquals(2, first.coal1Alliances().get(0).nations());
        assertEquals(2, first.coal1Alliances().get(0).declarations());
        assertEquals(11, first.coal1Alliances().get(1).allianceId());
        assertEquals(1, first.coal1Alliances().get(1).nations());
        assertEquals(2, first.coal1Alliances().get(1).declarations());

        ConflictStartDetector.Candidate second = result.candidates().get(1);
        assertEquals(baseTurn + 4L, second.turn());
        assertEquals(0, second.coal1Nations());
        assertEquals(3, second.coal2Nations());
        assertEquals(3, second.coal2Declarations());
    }

    @Test
    void tokenResolutionDefaultsToFirstCandidateAndValidatesSelectedTurns() {
        long baseTurn = TimeUtil.getTurn(1577836800000L) + 100L;
        Conflict conflict = new Conflict(-2, 0, "preview", baseTurn, baseTurn + 100L, 0L, 0L, 0L, false);

        ConflictStartDetector.Candidate first = new ConflictStartDetector.Candidate(
                baseTurn + 2L, 3, 0, 4, 0, List.of(), List.of());
        ConflictStartDetector.Candidate second = new ConflictStartDetector.Candidate(
                baseTurn + 4L, 0, 3, 0, 3, List.of(), List.of());
        ConflictStartDetector.Result result = new ConflictStartDetector.Result(
                baseTurn, baseTurn, List.of(first, second));

        UUID token = ConflictStartDetector.createToken(conflict, result);

        assertEquals(baseTurn + 2L, ConflictStartDetector.resolveCandidate(conflict, token, null).turn());
        assertEquals(baseTurn + 4L, ConflictStartDetector.resolveCandidate(conflict, token, baseTurn + 4L).turn());
        assertThrows(IllegalArgumentException.class,
                () -> ConflictStartDetector.resolveCandidate(conflict, token, baseTurn + 6L));
    }

    private static DBWar war(int id, long turn, int attackerId, int defenderId, int attackerAlliance, int defenderAlliance) {
        return new DBWar(
                id,
                attackerId,
                defenderId,
                attackerAlliance,
                defenderAlliance,
                false,
                false,
                WarType.ORD,
                WarStatus.ACTIVE,
                TimeUtil.getTimeFromTurn(turn),
                10,
                10,
                0);
    }
}
