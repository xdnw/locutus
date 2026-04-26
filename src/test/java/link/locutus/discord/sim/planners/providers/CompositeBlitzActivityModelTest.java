package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeBlitzActivityModelTest {
    @Test
    void wartimeAndBlitzSignalsOutrankPurePeacetimeFallback() {
        long nowTurn = 1_000L;
        long startTurn = nowTurn - CompositeBlitzActivityModel.LOOKBACK_TURNS + 1L;
        Int2IntOpenHashMap allianceByNationId = alliances(101, 1, 202, 2);
        Map<Integer, Set<Long>> loginTurnsByNation = Map.of(
                101, Set.of(nowTurn - 84, nowTurn),
                202, Set.of(nowTurn - 83, nowTurn - 1)
        );

        CompositeBlitzActivityModel model = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 202),
                allianceByNationId,
                loginTurnsByNation,
                List.of(
                        war(1, 101, 999, 1, 9, nowTurn - 47),
                        war(2, 101, 998, 1, 9, nowTurn - 84)
                ),
                null,
                startTurn,
                nowTurn,
                (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS),
                7L
        );

        assertTrue(model.signalsFor(101).compositeScore() > model.signalsFor(202).compositeScore());
        assertTrue(model.activityBasisPoints(101) > model.activityBasisPoints(202));
        assertEquals(2, model.signalsFor(101).offensiveActionSamples());
        assertTrue(model.signalsFor(101).wartimeEligibleTurns() >= CompositeBlitzActivityModel.MIN_WARTIME_TURNS);
    }

    @Test
    void identicalSignalsUseDeterministicSeededTieBreak() {
        long nowTurn = 2_000L;
        long startTurn = nowTurn - CompositeBlitzActivityModel.LOOKBACK_TURNS + 1L;
        Int2IntOpenHashMap allianceByNationId = alliances(101, 1, 202, 2);
        Map<Integer, Set<Long>> loginTurnsByNation = Map.of(
                101, Set.of(nowTurn - 84, nowTurn),
                202, Set.of(nowTurn - 84, nowTurn)
        );

        CompositeBlitzActivityModel first = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 202),
                allianceByNationId,
                loginTurnsByNation,
                List.of(),
                null,
                startTurn,
                nowTurn,
                (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS),
                13L
        );
        CompositeBlitzActivityModel second = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 202),
                allianceByNationId,
                loginTurnsByNation,
                List.of(),
                null,
                startTurn,
                nowTurn,
                (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS),
                13L
        );

        assertEquals(first.signalsFor(101).resolvedScore(), second.signalsFor(101).resolvedScore());
        assertEquals(first.signalsFor(202).resolvedScore(), second.signalsFor(202).resolvedScore());
        assertEquals(first.signalsFor(101).compositeScore(), first.signalsFor(202).compositeScore());
        assertNotEquals(first.signalsFor(101).resolvedScore(), first.signalsFor(202).resolvedScore());
        assertTrue(Math.abs(first.signalsFor(101).resolvedScore() - first.signalsFor(202).resolvedScore())
                < CompositeBlitzActivityModel.ACTIVITY_TIE_EPSILON);
    }

    @Test
    void disablingAllianceCoordinationCollapsesBackToSharedBaseSignals() {
        long nowTurn = 3_000L;
        long startTurn = nowTurn - CompositeBlitzActivityModel.LOOKBACK_TURNS + 1L;
        Int2IntOpenHashMap allianceByNationId = alliances(
                101, 1,
                102, 1,
                103, 1,
                202, 2
        );
        Map<Integer, Set<Long>> loginTurnsByNation = Map.of(
                101, Set.of(nowTurn - 84, nowTurn - 48, nowTurn),
                102, Set.of(nowTurn - 84, nowTurn - 48, nowTurn),
                103, Set.of(nowTurn - 84, nowTurn - 48, nowTurn),
                202, Set.of(nowTurn - 84, nowTurn - 48, nowTurn)
        );
        List<DBWar> wars = List.of(
                war(1, 101, 901, 1, 9, nowTurn - 84),
                war(2, 102, 902, 1, 9, nowTurn - 84),
                war(3, 103, 903, 1, 9, nowTurn - 84),
                war(4, 101, 904, 1, 9, nowTurn - 48),
                war(5, 102, 905, 1, 9, nowTurn - 48),
                war(6, 103, 906, 1, 9, nowTurn - 48),
                war(7, 202, 907, 2, 9, nowTurn - 84),
                war(8, 202, 908, 2, 9, nowTurn - 48)
        );

        CompositeBlitzActivityModel enabled = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 102, 103, 202),
                allianceByNationId,
                loginTurnsByNation,
                wars,
                null,
                startTurn,
                nowTurn,
                (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS),
                17L,
                CompositeBlitzActivityModel.Options.DEFAULT
        );
        CompositeBlitzActivityModel disabled = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 102, 103, 202),
                allianceByNationId,
                loginTurnsByNation,
                wars,
                null,
                startTurn,
                nowTurn,
                (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS),
                17L,
                CompositeBlitzActivityModel.Options.DEFAULT.withoutAllianceCoordination()
        );

        assertEquals(1.0d, enabled.signalsFor(101).allianceCoordination());
        assertEquals(0.0d, enabled.signalsFor(202).allianceCoordination());
        assertEquals(0.0d, disabled.signalsFor(101).allianceCoordination());
        assertTrue(enabled.signalsFor(101).compositeScore() > enabled.signalsFor(202).compositeScore());
        assertEquals(disabled.signalsFor(101).compositeScore(), disabled.signalsFor(202).compositeScore());
    }

    private static Int2IntOpenHashMap alliances(int... entries) {
        Int2IntOpenHashMap result = new Int2IntOpenHashMap();
        result.defaultReturnValue(0);
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return result;
    }

    private static DBWar war(int warId, int attackerId, int defenderId, int attackerAllianceId, int defenderAllianceId, long startTurn) {
        return new DBWar(
                warId,
                attackerId,
                defenderId,
                attackerAllianceId,
                defenderAllianceId,
                false,
                false,
                link.locutus.discord.apiv1.enums.WarType.ORD,
                WarStatus.ACTIVE,
                TimeUtil.getTimeFromTurn(startTurn),
                10,
                10,
                0
        );
    }
}