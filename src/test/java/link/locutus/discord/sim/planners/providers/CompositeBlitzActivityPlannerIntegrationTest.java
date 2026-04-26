package link.locutus.discord.sim.planners.providers;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CompositeBlitzActivityPlannerIntegrationTest {
    @Test
    void plannerDirectionChangesWhenAllianceCoordinationIsDisabled() {
        long nowTurn = 4_000L;
        long startTurn = nowTurn - CompositeBlitzActivityModel.LOOKBACK_TURNS + 1L;
        int currentWeekTurn = (int) Math.floorMod(nowTurn, CompositeBlitzActivityModel.WEEK_TURNS);
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

        long tieBreakSeed = seedFavoringNation202WithoutCoordination(
                allianceByNationId,
                loginTurnsByNation,
                wars,
                startTurn,
                nowTurn,
                currentWeekTurn
        );

        CompositeBlitzActivityModel disabled = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 102, 103, 202),
                allianceByNationId,
                loginTurnsByNation,
                wars,
                null,
                startTurn,
                nowTurn,
                currentWeekTurn,
                tieBreakSeed,
                CompositeBlitzActivityModel.Options.DEFAULT.withoutAllianceCoordination()
        );
        CompositeBlitzActivityModel enabled = CompositeBlitzActivityModel.fromHistory(
                Set.of(101, 102, 103, 202),
                allianceByNationId,
                loginTurnsByNation,
                wars,
                null,
                startTurn,
                nowTurn,
                currentWeekTurn,
                tieBreakSeed,
                CompositeBlitzActivityModel.Options.DEFAULT
        );

        assertTrue(disabled.signalsFor(202).resolvedScore() > disabled.signalsFor(101).resolvedScore());
        assertTrue(enabled.signalsFor(101).resolvedScore() > enabled.signalsFor(202).resolvedScore());

        DBNationSnapshot nation101 = snapshot(101, 1);
        DBNationSnapshot nation202 = snapshot(202, 2);
        List<DBNationSnapshot> combined = List.of(nation101, nation202);
        TreatyProvider oppositeSideOnly = (attackerId, defenderId) -> attackerId == defenderId
                || ((attackerId < 200) == (defenderId < 200));

        BlitzAssignment disabledAssignment = new BlitzPlanner(
                SimTuning.defaults(),
                oppositeSideOnly,
                OverrideSet.EMPTY,
                new DamageObjective(),
                disabled.snapshotProvider()
        ).assign(combined, combined);
        BlitzAssignment enabledAssignment = new BlitzPlanner(
                SimTuning.defaults(),
                oppositeSideOnly,
                OverrideSet.EMPTY,
                new DamageObjective(),
                enabled.snapshotProvider()
        ).assign(combined, combined);

        assertEquals(1, disabledAssignment.pairCount());
        assertEquals(1, enabledAssignment.pairCount());
        assertTrue(disabledAssignment.targetsFor(202).contains(101));
        assertTrue(enabledAssignment.targetsFor(101).contains(202));
    }

    private static long seedFavoringNation202WithoutCoordination(
            Int2IntOpenHashMap allianceByNationId,
            Map<Integer, Set<Long>> loginTurnsByNation,
            List<DBWar> wars,
            long startTurn,
            long nowTurn,
            int currentWeekTurn
    ) {
        for (long seed = 1L; seed <= 10_000L; seed++) {
            CompositeBlitzActivityModel disabled = CompositeBlitzActivityModel.fromHistory(
                    Set.of(101, 102, 103, 202),
                    allianceByNationId,
                    loginTurnsByNation,
                    wars,
                    null,
                    startTurn,
                    nowTurn,
                    currentWeekTurn,
                    seed,
                    CompositeBlitzActivityModel.Options.DEFAULT.withoutAllianceCoordination()
            );
            if (disabled.signalsFor(202).resolvedScore() > disabled.signalsFor(101).resolvedScore()) {
                return seed;
            }
        }
        fail("Expected a deterministic tie-break seed that favors nation 202 when coordination is disabled");
        return -1L;
    }

    private static DBNationSnapshot snapshot(int nationId, int allianceId) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(allianceId)
                .allianceId(allianceId)
                .score(1_000d)
                .cities(10)
                .nonInfraScoreBase(1_000d)
                .cityInfra(uniformInfra(10, 1_000d))
                .maxOff(5)
                .currentOffensiveWars(0)
                .currentDefensiveWars(0)
                .unit(MilitaryUnit.AIRCRAFT, 500)
                .unit(MilitaryUnit.SOLDIER, 500)
                .warPolicy(WarPolicy.ATTRITION)
                .build();
    }

    private static double[] uniformInfra(int cities, double infra) {
        double[] result = new double[cities];
        for (int i = 0; i < cities; i++) {
            result[i] = infra;
        }
        return result;
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