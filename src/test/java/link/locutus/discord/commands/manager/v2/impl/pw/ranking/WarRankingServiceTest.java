package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders.WarRankingRequests;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.scheduler.TriFunction;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarRankingServiceTest {
    @Test
    void warCostRequestBuilderAppliesDefaults() {
        long before = System.currentTimeMillis();
        WarRankingService.WarCostRequest request = WarRankingRequests.cost(
                100L,
                null,
                Set.<NationOrAlliance>of(),
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                null
        );
        long after = System.currentTimeMillis();

        assertEquals(100L, request.timeStartMs());
        assertTrue(request.timeEndMs() >= before && request.timeEndMs() <= after);
        assertEquals(WarCostMode.DEALT, request.type());
        assertEquals(WarCostStat.WAR_VALUE, request.stat());
        assertEquals(Set.of(), request.coalition1());
        assertEquals(Set.of(), request.coalition2());
    }

    @Test
    void warCountRequestBuilderDisablesPerMemberForNationRows() {
        WarRankingService.WarCountRequest request = WarRankingRequests.count(
                100L,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                false,
                false,
                true,
                true,
                true,
                null,
                null
        );

        assertTrue(request.rankByNation());
        assertFalse(request.normalizePerMember());
        assertTrue(request.ignoreInactiveNations());
    }

    @Test
    void conflictingWarFiltersAreRejectedAtBuilderBoundary() {
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.cost(
                0L,
                null,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                WarCostMode.DEALT,
                WarCostStat.WAR_VALUE,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                true,
                true,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.count(
                0L,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                true,
                true,
                false,
                false,
                false,
                false,
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.attackType(
                0L,
                AttackType.GROUND,
                Set.of(),
                null,
                false,
                true,
                true
        ));
            assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.cost(
                0L,
                null,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                WarCostMode.ATTACKER_PROFIT,
                WarCostStat.SOLDIER,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                null
            ));
            assertThrows(IllegalArgumentException.class, () -> WarRankingRequests.cost(
                0L,
                null,
                Set.<NationOrAlliance>of(),
                Set.<NationOrAlliance>of(),
                false,
                WarCostMode.DEFENDER_PROFIT,
                WarCostStat.GROUND,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                null
            ));
    }

    @Test
    void applyNormalizationScalesByRequestedFactors() {
        assertEquals(90d, WarRankingService.applyNormalization(90d, 3, 9, false, false), 0.000001d);
        assertEquals(30d, WarRankingService.applyNormalization(90d, 3, 9, true, false), 0.000001d);
        assertEquals(10d, WarRankingService.applyNormalization(90d, 3, 9, false, true), 0.000001d);
        assertEquals(10d, WarRankingService.applyNormalization(180d, 2, 9, true, true), 0.000001d);
    }

    @Test
    void normalizationModeMapsToTypedSemantics() {
        assertEquals(RankingNormalizationMode.NONE, WarRankingService.normalizationMode(false, false));
        assertEquals(RankingNormalizationMode.PER_WAR, WarRankingService.normalizationMode(true, false));
        assertEquals(RankingNormalizationMode.PER_CITY, WarRankingService.normalizationMode(false, true));
        assertEquals(RankingNormalizationMode.PER_WAR_PER_CITY, WarRankingService.normalizationMode(true, true));
    }

    @Test
    void allowedAllianceFilterAppliesToRankedWarSide() {
        DBWar war = war(1, 101, 202, 11, 22);
        IntOpenHashSet allowedAllianceIds = new IntOpenHashSet(new int[]{11});

        assertTrue(WarRankingService.matchesAllowedAllianceSide(war, true, allowedAllianceIds));
        assertFalse(WarRankingService.matchesAllowedAllianceSide(war, false, allowedAllianceIds));
        assertEquals(11, WarRankingService.rankedEntityId(war, true, true, allowedAllianceIds));
        assertEquals(101, WarRankingService.rankedEntityId(war, false, true, allowedAllianceIds));
        assertEquals(0, WarRankingService.rankedEntityId(war, true, false, allowedAllianceIds));
        assertEquals(0, WarRankingService.rankedEntityId(war, false, false, allowedAllianceIds));
    }

    @Test
    void perWarScalingCountsOnlyAllowedRankedSides() {
        List<DBWar> wars = List.of(
                war(1, 101, 202, 11, 22),
                war(2, 303, 104, 33, 11),
                war(3, 205, 306, 22, 33)
        );
        IntOpenHashSet allowedAllianceIds = new IntOpenHashSet(new int[]{11});

        assertEquals(Map.of(11, 2), WarRankingService.countWarsByGroup(wars, true, allowedAllianceIds));
        assertEquals(Map.of(101, 1, 104, 1), WarRankingService.countWarsByGroup(wars, false, allowedAllianceIds));
    }

    @Test
    void warCostAttributionUsesActualAttackSideForUnitStats() {
        DBWar war = war(1, 101, 202, 11, 22);
        TestAttackCursor defensiveCounter = new TestAttackCursor(202, 101, AttackType.GROUND)
                .withUnitLosses(3, 8)
                .withConvertedLosses(30d, 80d);

        assertEquals(3d, attackValue(WarCostMode.DEALT, WarCostStat.SOLDIER, true, war, defensiveCounter), 0.000001d);
        assertEquals(8d, attackValue(WarCostMode.LOSSES, WarCostStat.SOLDIER, true, war, defensiveCounter), 0.000001d);
        assertEquals(0d, attackValue(WarCostMode.ATTACKER_DEALT, WarCostStat.SOLDIER, false, war, defensiveCounter), 0.000001d);
        assertEquals(8d, attackValue(WarCostMode.DEFENDER_DEALT, WarCostStat.SOLDIER, false, war, defensiveCounter), 0.000001d);
    }

    @Test
    void warCostAttributionUsesActualAttackSideForResourceStats() {
        DBWar war = war(1, 101, 202, 11, 22);
        TestAttackCursor defensiveCounter = new TestAttackCursor(202, 101, AttackType.GROUND)
                .withResourceLosses(ResourceType.MUNITIONS, 5d, 11d);

        assertEquals(11d, attackValue(WarCostMode.DEALT, WarCostStat.MUNITIONS, false, war, defensiveCounter), 0.000001d);
        assertEquals(5d, attackValue(WarCostMode.LOSSES, WarCostStat.MUNITIONS, false, war, defensiveCounter), 0.000001d);
        assertEquals(5d, attackValue(WarCostMode.DEALT, WarCostStat.MUNITIONS, true, war, defensiveCounter), 0.000001d);
        assertEquals(11d, attackValue(WarCostMode.LOSSES, WarCostStat.MUNITIONS, true, war, defensiveCounter), 0.000001d);
    }

    @Test
    void warCostAttributionUsesActualAttackSideForWarValue() {
        DBWar war = war(1, 101, 202, 11, 22);
        TestAttackCursor defensiveCounter = new TestAttackCursor(202, 101, AttackType.GROUND)
                .withConvertedLosses(30d, 80d);

        assertEquals(30d, attackValue(WarCostMode.DEALT, WarCostStat.WAR_VALUE, true, war, defensiveCounter), 0.000001d);
        assertEquals(80d, attackValue(WarCostMode.LOSSES, WarCostStat.WAR_VALUE, true, war, defensiveCounter), 0.000001d);
        assertEquals(80d, attackValue(WarCostMode.DEFENDER_DEALT, WarCostStat.WAR_VALUE, false, war, defensiveCounter), 0.000001d);
        assertEquals(30d, attackValue(WarCostMode.DEFENDER_LOSSES, WarCostStat.WAR_VALUE, false, war, defensiveCounter), 0.000001d);
    }

    @Test
    void warCostAttributionUsesActualAttackSideForAttackTypeStats() {
        DBWar war = war(1, 101, 202, 11, 22);
        TestAttackCursor defensiveCounter = new TestAttackCursor(202, 101, AttackType.GROUND);

        assertEquals(0d, attackValue(WarCostMode.DEALT, WarCostStat.GROUND, true, war, defensiveCounter), 0.000001d);
        assertEquals(1d, attackValue(WarCostMode.LOSSES, WarCostStat.GROUND, true, war, defensiveCounter), 0.000001d);
        assertEquals(1d, attackValue(WarCostMode.DEFENDER_DEALT, WarCostStat.GROUND, false, war, defensiveCounter), 0.000001d);
        assertEquals(0d, attackValue(WarCostMode.ATTACKER_DEALT, WarCostStat.GROUND, false, war, defensiveCounter), 0.000001d);
    }

    private static double attackValue(WarCostMode mode, WarCostStat stat, boolean rankedSideIsWarAttacker, DBWar war, AbstractCursor attack) {
        TriFunction<Boolean, DBWar, AbstractCursor, Double> valueFunc = stat.getFunction(false, false, false, false, false, mode);
        return mode.getAttackFunc(valueFunc).apply(rankedSideIsWarAttacker, war, attack);
    }

    private static DBWar war(int warId, int attackerId, int defenderId, int attackerAllianceId, int defenderAllianceId) {
        return new DBWar(
                warId,
                attackerId,
                defenderId,
                attackerAllianceId,
                defenderAllianceId,
                false,
                false,
                WarType.RAID,
                WarStatus.ACTIVE,
                1000L,
                10,
                10,
                0
        );
    }

    private static final class TestAttackCursor extends FailedCursor {
        private final AttackType attackType;
        private int attackerSoldierLosses;
        private int defenderSoldierLosses;
        private double attackerConvertedLosses;
        private double defenderConvertedLosses;
        private final double[] attackerResourceLosses = ResourceType.getBuffer();
        private final double[] defenderResourceLosses = ResourceType.getBuffer();

        private TestAttackCursor(int attackerId, int defenderId, AttackType attackType) {
            this.attacker_id = attackerId;
            this.defender_id = defenderId;
            this.attackType = attackType;
        }

        private TestAttackCursor withUnitLosses(int attackerSoldierLosses, int defenderSoldierLosses) {
            this.attackerSoldierLosses = attackerSoldierLosses;
            this.defenderSoldierLosses = defenderSoldierLosses;
            return this;
        }

        private TestAttackCursor withConvertedLosses(double attackerConvertedLosses, double defenderConvertedLosses) {
            this.attackerConvertedLosses = attackerConvertedLosses;
            this.defenderConvertedLosses = defenderConvertedLosses;
            return this;
        }

        private TestAttackCursor withResourceLosses(ResourceType resource, double attackerLoss, double defenderLoss) {
            attackerResourceLosses[resource.ordinal()] = attackerLoss;
            defenderResourceLosses[resource.ordinal()] = defenderLoss;
            return this;
        }

        @Override
        public AttackType getAttack_type() {
            return attackType;
        }

        @Override
        public SuccessType getSuccess() {
            return SuccessType.PYRRHIC_VICTORY;
        }

        @Override
        public int getAttUnitLosses(MilitaryUnit unit) {
            return unit == MilitaryUnit.SOLDIER ? attackerSoldierLosses : 0;
        }

        @Override
        public int getDefUnitLosses(MilitaryUnit unit) {
            return unit == MilitaryUnit.SOLDIER ? defenderSoldierLosses : 0;
        }

        @Override
        public double getAttLossValue(DBWar war) {
            return attackerConvertedLosses;
        }

        @Override
        public double getDefLossValue(DBWar war) {
            return defenderConvertedLosses;
        }

        @Override
        public double getAttUnitLossValue(DBWar war) {
            return attackerConvertedLosses;
        }

        @Override
        public double getDefUnitLossValue(DBWar war) {
            return defenderConvertedLosses;
        }

        @Override
        public double[] addAttUnitCosts(double[] buffer, DBWar war) {
            return buffer;
        }

        @Override
        public double[] addDefUnitCosts(double[] buffer, DBWar war) {
            return buffer;
        }

        @Override
        public double[] addAttLosses(double[] buffer, DBWar war) {
            return addAttLoot(buffer);
        }

        @Override
        public double[] addDefLosses(double[] buffer, DBWar war) {
            return addDefLoot(buffer);
        }

        @Override
        public double[] addAttLoot(double[] buffer) {
            for (ResourceType resource : ResourceType.values) {
                buffer[resource.ordinal()] += attackerResourceLosses[resource.ordinal()];
            }
            return buffer;
        }

        @Override
        public double[] addDefLoot(double[] buffer) {
            for (ResourceType resource : ResourceType.values) {
                buffer[resource.ordinal()] += defenderResourceLosses[resource.ordinal()];
            }
            return buffer;
        }

        @Override
        public double getAttLootValue() {
            return attackerResourceLosses[ResourceType.MUNITIONS.ordinal()];
        }

        @Override
        public double getDefLootValue() {
            return defenderResourceLosses[ResourceType.MUNITIONS.ordinal()];
        }

        @Override
        public double[] getLoot() {
            return null;
        }

        @Override
        public Map<Building, Integer> getBuildingsDestroyed() {
            return Collections.emptyMap();
        }

        @Override
        public java.util.Set<Integer> getCityIdsDamaged() {
            return Collections.emptySet();
        }
    }
}
