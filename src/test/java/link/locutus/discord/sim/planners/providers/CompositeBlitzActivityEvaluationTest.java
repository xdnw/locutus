package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.GroundCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeBlitzActivityEvaluationTest {
    @Test
    void evaluationRowsPairSignalsWithObservedFirstActionOutcomes() {
        long nowTurn = 5_000L;
        long startTurn = nowTurn - CompositeBlitzActivityModel.LOOKBACK_TURNS + 1L;
        int currentWeekTurn = (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS);
        Int2IntOpenHashMap allianceByNationId = alliances(101, 1, 202, 2);
        Map<Integer, Set<Long>> loginTurnsByNation = Map.of(
                101, Set.of(nowTurn - 48, nowTurn),
                202, Set.of(nowTurn - 48, nowTurn)
        );
        List<DBWar> wars = List.of(
                war(1, 101, 901, 1, 9, nowTurn - 24),
                war(2, 202, 902, 2, 9, nowTurn - 24)
        );
        List<AbstractCursor> attacks = List.of(
                attack(1, 101, 901, nowTurn - 24),
                attack(2, 202, 902, nowTurn - 12)
        );

        CompositeBlitzActivityEvaluation evaluation = CompositeBlitzActivityEvaluation.evaluateHistory(
                Set.of(101, 202),
                allianceByNationId,
                loginTurnsByNation,
                wars,
                attacks,
                startTurn,
                nowTurn,
                currentWeekTurn,
                19L,
                CompositeBlitzActivityModel.Options.DEFAULT
        );

        CompositeBlitzActivityEvaluation.NationRow first = evaluation.rowFor(101);
        CompositeBlitzActivityEvaluation.NationRow second = evaluation.rowFor(202);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1, first.declaredWarCount());
        assertEquals(1, second.declaredWarCount());
        assertEquals(1, first.warsWithObservedFirstAction());
        assertEquals(1, second.warsWithObservedFirstAction());
        assertEquals(1.0d, first.observedParticipationRate());
        assertEquals(1.0d, second.observedParticipationRate());
        assertTrue(first.observedFirstActionScore() > second.observedFirstActionScore());
        assertNotNull(first.signals());
        assertNotNull(second.signals());
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

    private static AbstractCursor attack(int warId, int attackerId, int defenderId, long turn) {
        return new TestGroundCursor(warId, attackerId, defenderId, turn);
    }

    private static final class TestGroundCursor extends GroundCursor {
        private TestGroundCursor(int warId, int attackerId, int defenderId, long turn) {
            this.war_id = warId;
            this.attacker_id = attackerId;
            this.defender_id = defenderId;
            this.date = TimeUtil.getTimeFromTurn(turn);
        }

        @Override
        public AttackType getAttack_type() {
            return AttackType.GROUND;
        }
    }
}