package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.actions.DeclareWarAction;
import link.locutus.discord.sim.combat.AttackResolver;
import link.locutus.discord.sim.combat.state.BasicCombatCityView;
import link.locutus.discord.sim.combat.state.BasicCombatantView;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.WarStateView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarTypeRoutingTest {

    @Test
    void declareWarActionWarTypeRoutesThroughSimWarIntoAttackResolver() {
        SimWorld world = new SimWorld();
        world.addNation(new SimNation(1, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(2, WarPolicy.TURTLE, 0d, 100d, 2));
        world.addNation(new SimNation(3, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(4, WarPolicy.TURTLE, 0d, 100d, 2));
        world.addNation(new SimNation(5, WarPolicy.FORTRESS, 0d, 100d, 2));
        world.addNation(new SimNation(6, WarPolicy.TURTLE, 0d, 100d, 2));

        world.apply(new DeclareWarAction(1001, 1, 2, WarType.RAID));
        world.apply(new DeclareWarAction(1002, 3, 4, WarType.ORD));
        world.apply(new DeclareWarAction(1003, 5, 6, WarType.ATT));

        assertEquals(WarType.RAID, world.requireWar(1001).warType());
        assertEquals(WarType.ORD, world.requireWar(1002).warType());
        assertEquals(WarType.ATT, world.requireWar(1003).warType());

        CombatantView attacker = nation(101, 70_000, 2_500, 1_800, 12, 1_000_000, 2400);
        CombatantView defender = nation(202, 45_000, 1_500, 1_200, 7, 2_000_000, 2600);

        AttackResolver.AttackRanges raid = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                world.requireWar(1001).asWarStateView(),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );
        AttackResolver.AttackRanges ordinary = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                world.requireWar(1002).asWarStateView(),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );
        AttackResolver.AttackRanges attrition = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                world.requireWar(1003).asWarStateView(),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );

        double raidInfra = midpoint(raid.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);
        double ordinaryInfra = midpoint(ordinary.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);
        double attritionInfra = midpoint(attrition.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);

        double raidLoot = midpoint(raid.defenderLossRanges(), MilitaryUnit.MONEY);
        double ordinaryLoot = midpoint(ordinary.defenderLossRanges(), MilitaryUnit.MONEY);
        double attritionLoot = midpoint(attrition.defenderLossRanges(), MilitaryUnit.MONEY);

        assertTrue(attritionInfra > ordinaryInfra, "Attrition should route to higher infra damage than ordinary");
        assertTrue(ordinaryInfra > raidInfra, "Ordinary should route to higher infra damage than raid");
        assertTrue(raidLoot > ordinaryLoot, "Raid should route to higher loot than ordinary");
        assertTrue(ordinaryLoot > attritionLoot, "Ordinary should route to higher loot than attrition");

        WarStateView raidCounter = world.requireWar(1001).asWarStateViewFor(SimSide.DEFENDER);
        WarStateView attritionCounter = world.requireWar(1003).asWarStateViewFor(SimSide.DEFENDER);

        AttackResolver.AttackRanges raidCounterRanges = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                raidCounter,
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );
        AttackResolver.AttackRanges attritionCounterRanges = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                attritionCounter,
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );

        double raidCounterInfra = midpoint(raidCounterRanges.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);
        double attritionCounterLoot = midpoint(attritionCounterRanges.defenderLossRanges(), MilitaryUnit.MONEY);

        assertTrue(raidCounterInfra > raidInfra, "Raid defenders should route to higher infra damage than raid declarers");
        assertTrue(attritionCounterLoot > attritionLoot, "Attrition defenders should route to higher loot than attrition declarers");
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
