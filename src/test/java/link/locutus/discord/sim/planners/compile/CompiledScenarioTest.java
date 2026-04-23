package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledScenarioTest {
    private final ScenarioCompiler compiler = new ScenarioCompiler();

    @Test
    void indexesDefendersByWarRangeBucket() {
        DBNationSnapshot attacker = nation(1, 1_000.0).build();
        DBNationSnapshot inRangeA = nation(101, 900.0).build();
        DBNationSnapshot inRangeB = nation(102, 1_600.0).build();
        DBNationSnapshot outOfRange = nation(103, 4_000.0).build();

        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(inRangeA, inRangeB, outOfRange),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                activityWeights(attacker, inRangeA, inRangeB, outOfRange)
        );

        List<Integer> defenderIds = new ArrayList<>();
        scenario.forEachDefenderIndexInRange(0, defenderIndex -> defenderIds.add(scenario.defenderNationId(defenderIndex)));

        assertEquals(2, defenderIds.size());
        assertEquals(Set.of(101, 102), Set.copyOf(defenderIds));
    }

    @Test
    void tracksTreatiesAndExistingPairConflicts() {
        DBNationSnapshot attacker = nation(1, 1_000.0)
                .activeOpponentNationId(101)
                .build();
        DBNationSnapshot conflictDefender = nation(101, 1_000.0).build();
        DBNationSnapshot treatedDefender = nation(102, 1_000.0).build();

        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(conflictDefender, treatedDefender),
                OverrideSet.EMPTY,
                (a, d) -> d == 102,
                activityWeights(attacker, conflictDefender, treatedDefender)
        );

        assertTrue(scenario.hasActivePairConflict(0, 0));
        assertTrue(scenario.isTreated(0, 1));
        assertFalse(scenario.isTreated(0, 0));
        assertEquals(List.of(0), toList(scenario.relevantDefenderIndexes(0)));
    }

        @Test
        void treatyCompilationOnlyChecksDefendersInWarRange() {
                DBNationSnapshot attacker = nation(1, 1_000.0).build();
                DBNationSnapshot inRange = nation(101, 1_000.0).build();
                DBNationSnapshot outOfRange = nation(102, 5_000.0).build();
                AtomicInteger checks = new AtomicInteger();

                compiler.compile(
                                List.of(attacker),
                                List.of(inRange, outOfRange),
                                OverrideSet.EMPTY,
                                (a, d) -> {
                                        checks.incrementAndGet();
                                        return false;
                                },
                                activityWeights(attacker, inRange, outOfRange)
                );

                assertEquals(1, checks.get(), "Treaty checks should be limited to in-range defenders");
        }

    @Test
    void storesExplicitActivityWeights() {
        DBNationSnapshot attacker = nation(1, 1_000.0)
                .currentOffensiveWars(1)
                .build();
        DBNationSnapshot defender = nation(101, 1_000.0).build();

        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of(attacker.nationId(), 0.75f, defender.nationId(), 0.5f)
        );

        assertEquals(0.75f, scenario.attackerActivityWeight(0));
        assertEquals(0.5f, scenario.defenderActivityWeight(0));
    }

    @Test
    void compilesAuthoritativeUnitsAndCityInfraSlices() {
        DBNationSnapshot attacker = nation(1, 1_000.0)
                .unit(MilitaryUnit.SOLDIER, 12_345)
                .unit(MilitaryUnit.TANK, 321)
                .cityInfra(new double[]{1_250.5, 999.5, 800.0})
                .build();
        DBNationSnapshot defender = nation(101, 1_100.0)
                .unit(MilitaryUnit.AIRCRAFT, 222)
                .cityInfra(new double[]{1_750.0, 1_400.0})
                .build();

        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                activityWeights(attacker, defender)
        );

        assertEquals(12_345, scenario.attackerUnitCount(0, MilitaryUnit.SOLDIER));
        assertEquals(321, scenario.attackerUnitCount(0, MilitaryUnit.TANK));
        assertEquals(222, scenario.defenderUnitCount(0, MilitaryUnit.AIRCRAFT));

        assertEquals(3, scenario.attackerCityCount(0));
        assertEquals(2, scenario.defenderCityCount(0));
        assertEquals(1_250.5, scenario.attackerCityInfraAt(0, 0));
        assertEquals(800.0, scenario.attackerCityInfraAt(0, 2));
        assertEquals(1_750.0, scenario.defenderCityInfraAt(0, 0));
        assertEquals(1_400.0, scenario.defenderCityInfraAt(0, 1));
    }

    @Test
    void compilesResearchAndProjectBits() {
        long attackerProjectBits = (1L << 5) | (1L << 19);
        int attackerResearchBits = 0b11111_00000_00000_00000;
        long defenderProjectBits = 1L << 3;
        int defenderResearchBits = 0b11;

        DBNationSnapshot attacker = nation(1, 1_000.0)
                .researchBits(attackerResearchBits)
                .projectBits(attackerProjectBits)
                .build();
        DBNationSnapshot defender = nation(101, 900.0)
                .researchBits(defenderResearchBits)
                .projectBits(defenderProjectBits)
                .build();

        CompiledScenario scenario = compiler.compile(
                List.of(attacker),
                List.of(defender),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                activityWeights(attacker, defender)
        );

        assertEquals(attackerResearchBits, scenario.attackerResearchBits(0));
        assertEquals(attackerProjectBits, scenario.attackerProjectBits(0));
        assertEquals(defenderResearchBits, scenario.defenderResearchBits(0));
        assertEquals(defenderProjectBits, scenario.defenderProjectBits(0));
    }

    private static DBNationSnapshot.Builder nation(int nationId, double score) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(nationId)
                .allianceId(nationId)
                .score(score)
                .cities(5)
                .nonInfraScoreBase(score)
                .cityInfra(new double[]{1_000, 1_000, 1_000, 1_000, 1_000})
                .warPolicy(WarPolicy.ATTRITION);
    }

    private static Map<Integer, Float> activityWeights(DBNationSnapshot... snapshots) {
        Map<Integer, Float> weights = new LinkedHashMap<>();
        for (DBNationSnapshot snapshot : snapshots) {
            weights.put(snapshot.nationId(), 0.5f);
        }
        return weights;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }
}