package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.sim.combat.state.BasicCombatCityView;
import link.locutus.discord.sim.combat.state.BasicCombatantView;
import link.locutus.discord.sim.combat.state.BasicWarStateView;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.combat.state.WarStateView;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackResolverWarTypeAndFortifyTest {

    @Test
    void groundRangesVaryByWarTypeForInfraAndLoot() {
        CombatantView attacker = nation(1, 70_000, 2_500, 1_800, 12, 1_000_000, 2400);
        CombatantView defender = nation(2, 45_000, 1_500, 1_200, 7, 2_000_000, 2600);

        AttackResolver.AttackRanges raid = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                BasicWarStateView.simple(WarType.RAID),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );
        AttackResolver.AttackRanges ordinary = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                BasicWarStateView.simple(WarType.ORD),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );
        AttackResolver.AttackRanges attrition = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                BasicWarStateView.simple(WarType.ATT),
                AttackType.GROUND,
                SuccessType.IMMENSE_TRIUMPH
        );

        double raidInfra = midpoint(raid.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);
        double ordinaryInfra = midpoint(ordinary.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);
        double attritionInfra = midpoint(attrition.defenderLossRanges(), MilitaryUnit.INFRASTRUCTURE);

        double raidLoot = midpoint(raid.defenderLossRanges(), MilitaryUnit.MONEY);
        double ordinaryLoot = midpoint(ordinary.defenderLossRanges(), MilitaryUnit.MONEY);
        double attritionLoot = midpoint(attrition.defenderLossRanges(), MilitaryUnit.MONEY);

        assertTrue(attritionInfra > ordinaryInfra, "Attrition should do more infra damage than ordinary");
        assertTrue(ordinaryInfra > raidInfra, "Ordinary should do more infra damage than raid");

        assertTrue(raidLoot > ordinaryLoot, "Raid should loot more than ordinary");
        assertTrue(ordinaryLoot > attritionLoot, "Ordinary should loot more than attrition");
    }

    @Test
    void fortifiedDefenderIncreasesAttackerGroundLosses() {
        CombatantView attacker = nation(1, 52_000, 1_800, 1_400, 8, 500_000, 2200);
        CombatantView defender = nation(2, 46_000, 1_600, 1_300, 8, 500_000, 2200);

        WarStateView normalWar = BasicWarStateView.ofRelative(WarType.ORD, false, false, false, false);
        WarStateView fortifiedWar = BasicWarStateView.ofRelative(WarType.ORD, false, false, false, true);

        AttackResolver.AttackRanges normal = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                normalWar,
                AttackType.GROUND,
                SuccessType.MODERATE_SUCCESS
        );
        AttackResolver.AttackRanges fortified = AttackResolver.rangesForSuccess(
                attacker,
                defender,
                fortifiedWar,
                AttackType.GROUND,
                SuccessType.MODERATE_SUCCESS
        );

        double normalLosses = midpoint(normal.attackerLossRanges(), MilitaryUnit.SOLDIER)
                + midpoint(normal.attackerLossRanges(), MilitaryUnit.TANK);
        double fortifiedLosses = midpoint(fortified.attackerLossRanges(), MilitaryUnit.SOLDIER)
                + midpoint(fortified.attackerLossRanges(), MilitaryUnit.TANK);

        assertTrue(fortifiedLosses > normalLosses, "Fortify should increase incoming attacker casualties");
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
