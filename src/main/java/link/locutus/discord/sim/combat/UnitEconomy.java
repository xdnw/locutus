package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.sim.combat.state.CombatantView;

import java.util.function.Function;
import java.util.function.Predicate;

public final class UnitEconomy {
    private UnitEconomy() {
    }

    public static int applyBeigeDailyBuyBonus(
            int baseCap,
            MilitaryUnit unit,
            int beigeTurns,
            boolean hasActiveWars
    ) {
        if (!receivesBeigeDailyBuyBonus(unit) || beigeTurns <= 0 || hasActiveWars || baseCap == Integer.MAX_VALUE) {
            return baseCap;
        }
        return (int) Math.round(baseCap * 1.15d);
    }

    public static int capacity(CombatantView nation, MilitaryUnit unit) {
        return nation.getUnitCapacity(unit);
    }

    public static int maxBuyPerDay(CombatantView nation, MilitaryUnit unit) {
        return nation.getUnitMaxPerDay(unit);
    }

    @Deprecated
    public static double unitCost(CombatantView nation, MilitaryUnit unit, int qty) {
        return unitCostConverted(nation, unit, qty);
    }

    public static double unitCostConverted(CombatantView nation, MilitaryUnit unit, int qty) {
        return nation.getUnitConvertedCost(unit) * qty;
    }

    public static double[] unitCostResources(CombatantView nation, MilitaryUnit unit, int qty) {
        return unit.addCost(ResourceType.getBuffer(), qty, nation.researchBits());
    }

    public static int daysUntilFullRebuild(CombatantView nation, MilitaryUnit unit) {
        int cap = capacity(nation, unit);
        int current = nation.getUnits(unit);
        if (current >= cap) return 0;
        int perDay = maxBuyPerDay(nation, unit);
        if (perDay <= 0) return Integer.MAX_VALUE;
        return (int) Math.ceil((cap - current) / (double) perDay);
    }

    public static double groundStrength(CombatKernel.NationState nation, boolean equipSoldiers, boolean underEnemyAir) {
        return groundStrengthRaw(
                nation.getUnits(MilitaryUnit.SOLDIER),
                nation.getUnits(MilitaryUnit.TANK),
                equipSoldiers,
                underEnemyAir
        );
    }

    /**
     * Authoritative ground strength formula in raw-count form for call sites that only have
     * primitive unit counts (e.g. planner candidate generation reading from
     * {@code CompiledScenario}). Mirrors {@link #groundStrength(CombatKernel.NationState, boolean, boolean)}
     * so both forms stay in lockstep with game rules.
     */
    public static double groundStrengthRaw(int soldiers, int tanks, boolean equipSoldiers, boolean underEnemyAir) {
        double soldierFactor = equipSoldiers ? 1.75 : 1.0;
        double tankFactor = underEnemyAir ? 20.0 : 40.0;
        return soldiers * soldierFactor + tanks * tankFactor;
    }

    public static double airStrength(CombatKernel.NationState nation) {
        return nation.getUnits(MilitaryUnit.AIRCRAFT);
    }

    public static double navalStrength(CombatKernel.NationState nation) {
        return nation.getUnits(MilitaryUnit.SHIP);
    }

    public static int maxBuyPerDayFor(
            int cities,
            MilitaryUnit unit,
            Predicate<Project> hasProject,
            Function<link.locutus.discord.apiv1.enums.Research, Integer> getResearch
    ) {
        return unit.getMaxPerDay(cities, hasProject, getResearch);
    }

    public static int maxBuyPerDayFor(
            int cities,
            MilitaryUnit unit,
            Predicate<Project> hasProject,
            Function<link.locutus.discord.apiv1.enums.Research, Integer> getResearch,
            int beigeTurns,
            boolean hasActiveWars
    ) {
        return applyBeigeDailyBuyBonus(
                maxBuyPerDayFor(cities, unit, hasProject, getResearch),
                unit,
                beigeTurns,
                hasActiveWars
        );
    }

    private static boolean receivesBeigeDailyBuyBonus(MilitaryUnit unit) {
        return switch (unit) {
            case SOLDIER, TANK, AIRCRAFT, SHIP -> true;
            default -> false;
        };
    }
}
