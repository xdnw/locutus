package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.AttackAction;
import link.locutus.discord.sim.combat.AttackResolver;
import link.locutus.discord.sim.combat.state.BasicCombatCityView;
import link.locutus.discord.sim.combat.state.BasicCombatantView;
import link.locutus.discord.sim.combat.state.CombatantView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FortifyLifecycleTest {

        @Test
        void attackActionRejectsPeaceAndVictoryTypes() {
                assertThrows(IllegalArgumentException.class, () -> new AttackAction(77, 1, AttackType.PEACE));
                assertThrows(IllegalArgumentException.class, () -> new AttackAction(77, 1, AttackType.VICTORY));
                assertThrows(IllegalArgumentException.class, () -> new AttackAction(77, 1, AttackType.A_LOOT));
        }

    @Test
    void fortifySetsDoesNotStackAndClearsOnActorsNextNonFortifyAttack() {
        SimWorld world = new SimWorld();
                world.addNation(new SimNation(1, WarPolicy.FORTRESS));
                world.addNation(new SimNation(2, WarPolicy.TURTLE));
        SimWar war = new SimWar(77, 1, 2, WarType.ORD);
        world.addWar(war);

        world.apply(new AttackAction(77, 1, AttackType.FORTIFY));
        assertTrue(war.attackerFortified());
        assertFalse(war.defenderFortified());

        world.apply(new AttackAction(77, 1, AttackType.FORTIFY));
        assertTrue(war.attackerFortified(), "Repeated fortify should not stack; state remains fortified");

        world.apply(new AttackAction(77, 2, AttackType.GROUND));
        assertTrue(war.attackerFortified(), "Enemy attacks do not clear your fortify");

        world.apply(new AttackAction(77, 1, AttackType.AIRSTRIKE_AIRCRAFT));
        assertFalse(war.attackerFortified(), "Your next non-fortify attack clears your own fortify");
    }

    @Test
    void activeFortifyInSimWarRaisesIncomingAttackerLosses() {
        SimWorld world = new SimWorld();
                world.addNation(new SimNation(1, WarPolicy.FORTRESS));
                world.addNation(new SimNation(2, WarPolicy.TURTLE));
        SimWar war = new SimWar(88, 1, 2, WarType.ORD);
        world.addWar(war);

        CombatantView attacker = nation(1, 52_000, 1_800, 1_400, 8, 500_000, 2200);
        CombatantView defender = nation(2, 46_000, 1_600, 1_300, 8, 500_000, 2200);

        world.apply(new AttackAction(88, 2, AttackType.FORTIFY));
        AttackResolver.AttackRanges fortified = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                war.asWarStateView(),
                AttackType.GROUND,
                SuccessType.MODERATE_SUCCESS
        );

        world.apply(new AttackAction(88, 2, AttackType.GROUND));
        AttackResolver.AttackRanges cleared = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                war.asWarStateView(),
                AttackType.GROUND,
                SuccessType.MODERATE_SUCCESS
        );

        double fortifiedLosses = midpoint(fortified.attackerLossRanges(), MilitaryUnit.SOLDIER)
                + midpoint(fortified.attackerLossRanges(), MilitaryUnit.TANK);
        double clearedLosses = midpoint(cleared.attackerLossRanges(), MilitaryUnit.SOLDIER)
                + midpoint(cleared.attackerLossRanges(), MilitaryUnit.TANK);

        assertTrue(fortifiedLosses > clearedLosses, "Fortify should increase incoming attacker casualties while active");
    }

    private static CombatantView nation(
            int id,
            int soldiers,
            int tanks,
            int aircraft,
            int ships,
            int money,
            double cityInfra
    ) {
        return BasicCombatantView.builder()
                .nationId(id)
                .cities(8)
                .researchBits(0)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .unit(MilitaryUnit.TANK, tanks)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SHIP, ships)
                .unit(MilitaryUnit.MONEY, money)
                .capacity(MilitaryUnit.SOLDIER, soldiers)
                .capacity(MilitaryUnit.TANK, tanks)
                .capacity(MilitaryUnit.AIRCRAFT, aircraft)
                .capacity(MilitaryUnit.SHIP, ships)
                .capacity(MilitaryUnit.MONEY, Integer.MAX_VALUE)
                .infraAttackModifierAll(1d)
                .infraDefendModifierAll(1d)
                .looterModifier(true, 1d)
                .looterModifier(false, 1d)
                .lootModifier(1d)
                .city(BasicCombatCityView.of(cityInfra, 150, 180, 300, 360))
                .build();
    }

    private static double midpoint(
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> ranges,
            MilitaryUnit unit
    ) {
        Map.Entry<Integer, Integer> range = ranges.get(unit);
        if (range == null) {
            return 0d;
        }
        return (range.getKey() + range.getValue()) * 0.5d;
    }
}
