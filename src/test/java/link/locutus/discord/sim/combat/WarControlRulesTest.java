package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.state.BasicCombatCityView;
import link.locutus.discord.sim.combat.state.BasicCombatantView;
import link.locutus.discord.sim.combat.state.CombatantView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarControlRulesTest {

    @Test
    void navalAirStripsDefenderAirAndImmenseStillGrantsBlockade() {
        CombatantView attacker = nation(1, 0, 0, 0, 12);
        CombatantView defender = nation(2, 0, 0, 80, 6);
        TestContext context = new TestContext(
                attacker,
                defender,
                false,
                true,
                false,
                false,
                false,
                false,
                CombatKernel.AttackContext.BLOCKADE_NONE
        );

        ControlFlagDelta moderate = WarControlRules.controlDelta(context, AttackType.NAVAL_AIR, SuccessType.MODERATE_SUCCESS);
        assertEquals(0, moderate.airSuperiority());
        assertTrue(moderate.clearAirSuperiority());
        assertEquals(0, moderate.blockade());

        ControlFlagDelta immense = WarControlRules.controlDelta(context, AttackType.NAVAL_AIR, SuccessType.IMMENSE_TRIUMPH);
        assertEquals(1, immense.blockade());
        assertTrue(immense.clearAirSuperiority());
    }

    @Test
    void reconcileAfterAttackClearsOutgoingBlockadeWhenNationBecomesBlockaded() {
        CombatantView attacker = nation(1, 0, 0, 0, 20);
        CombatantView defender = nation(2, 0, 0, 0, 12);

        TestWar currentWar = new TestWar(1001, 1, 2);
        TestWar otherWar = new TestWar(1002, 2, 3);
        otherWar.blockadeNationId = 2;

        Map<Integer, List<TestWar>> warsByNation = Map.of(
                1, List.of(currentWar),
                2, List.of(currentWar, otherWar),
                3, List.of(otherWar)
        );
        List<Integer> blockadeChangedWars = new ArrayList<>();

        WarControlRules.reconcileAfterAttack(
                currentWar,
                attacker,
                defender,
                ControlFlagDelta.of(0, 0, 1, false, false, false),
                activeWars(warsByNation),
                war -> blockadeChangedWars.add(war.warId)
        );

        assertEquals(1, currentWar.blockadeNationId);
        assertNull(otherWar.blockadeNationId);
        assertEquals(List.of(1001, 1002), blockadeChangedWars);
    }

    @Test
    void reconcileAfterAttackClearsOutgoingAirWhenNationHasNoAircraftLeft() {
        CombatantView attacker = nation(1, 0, 0, 10, 0);
        CombatantView defenderWithoutAircraft = nation(2, 0, 0, 0, 0);

        TestWar currentWar = new TestWar(2001, 1, 2);
        TestWar otherWar = new TestWar(2002, 2, 3);
        otherWar.airSuperiorityNationId = 2;

        Map<Integer, List<TestWar>> warsByNation = Map.of(
                1, List.of(currentWar),
                2, List.of(currentWar, otherWar),
                3, List.of(otherWar)
        );

        WarControlRules.reconcileAfterAttack(
                currentWar,
                attacker,
                defenderWithoutAircraft,
                ControlFlagDelta.NONE,
                activeWars(warsByNation),
                ignored -> { }
        );

        assertNull(otherWar.airSuperiorityNationId);
    }

    @Test
        void booleanOverloadMatchesContextDrivenControlDelta() {
        CombatantView attacker = nation(1, 0, 0, 0, 12);
        CombatantView defender = nation(2, 0, 0, 80, 6);
        TestContext context = new TestContext(
            attacker,
            defender,
            false,
            true,
            false,
            true,
            false,
            false,
            CombatKernel.AttackContext.BLOCKADE_DEFENDER
        );

        ControlFlagDelta fromContext =
            WarControlRules.controlDelta(context, AttackType.NAVAL_GROUND, SuccessType.MODERATE_SUCCESS);
        ControlFlagDelta fromBooleans =
            WarControlRules.controlDelta(AttackType.NAVAL_GROUND, SuccessType.MODERATE_SUCCESS, true, true, true);

        assertEquals(fromContext, fromBooleans);
        assertFalse(fromBooleans == ControlFlagDelta.NONE);
    }

    private static IntFunction<Iterable<TestWar>> activeWars(Map<Integer, List<TestWar>> warsByNation) {
        return nationId -> warsByNation.getOrDefault(nationId, List.of());
    }

    private static CombatantView nation(int nationId, int soldiers, int tanks, int aircraft, int ships) {
        return BasicCombatantView.builder()
                .nationId(nationId)
                .cities(1)
                .researchBits(0)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .unit(MilitaryUnit.TANK, tanks)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SHIP, ships)
                .capacity(MilitaryUnit.SOLDIER, soldiers)
                .capacity(MilitaryUnit.TANK, tanks)
                .capacity(MilitaryUnit.AIRCRAFT, aircraft)
                .capacity(MilitaryUnit.SHIP, ships)
                .city(BasicCombatCityView.ofInfra(2000d))
                .build();
    }

    private record TestContext(
            CombatKernel.NationState attacker,
            CombatKernel.NationState defender,
            boolean attackerHasAirControl,
            boolean defenderHasAirControl,
            boolean attackerHasGroundControl,
            boolean defenderHasGroundControl,
            boolean attackerFortified,
            boolean defenderFortified,
            int blockadeOwner
    ) implements CombatKernel.AttackContext {
        @Override
        public WarType warType() {
            return WarType.ORD;
        }

        @Override
        public int attackerMaps() {
            return 6;
        }

        @Override
        public int defenderMaps() {
            return 6;
        }

        @Override
        public int attackerResistance() {
            return 100;
        }

        @Override
        public int defenderResistance() {
            return 100;
        }
    }

    private static final class TestWar implements WarControlRules.MutableWarControlState {
        private final int warId;
        private final int attackerNationId;
        private final int defenderNationId;
        private Integer groundControlNationId;
        private Integer airSuperiorityNationId;
        private Integer blockadeNationId;

        private TestWar(int warId, int attackerNationId, int defenderNationId) {
            this.warId = warId;
            this.attackerNationId = attackerNationId;
            this.defenderNationId = defenderNationId;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        public int attackerNationId() {
            return attackerNationId;
        }

        public int defenderNationId() {
            return defenderNationId;
        }

        @Override
        public Integer groundControlNationId() {
            return groundControlNationId;
        }

        @Override
        public Integer airSuperiorityNationId() {
            return airSuperiorityNationId;
        }

        @Override
        public Integer blockadeNationId() {
            return blockadeNationId;
        }

        @Override
        public void setGroundControlNationId(Integer nationId) {
            groundControlNationId = nationId;
        }

        @Override
        public void setAirSuperiorityNationId(Integer nationId) {
            airSuperiorityNationId = nationId;
        }

        @Override
        public void setBlockadeNationId(Integer nationId) {
            blockadeNationId = nationId;
        }
    }
}