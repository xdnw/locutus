package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.WarSlotRules;
import link.locutus.discord.util.PW;

/**
 * Generated military-rules constants for the blitz planner frontend.
 *
 * <p>The frontend used to hand-maintain unit caps, daily-buy rules, MMR ordinal
 * layout, and project/research bit indices. Now those are read from this
 * record via the dedicated `blitzRules()` bootstrap endpoint so adding a
 * project or tweaking a research bonus only needs a backend change.
 *
 * <p>All per-unit arrays are indexed by {@link MilitaryUnit#ordinal()}; entries
 * for non-MMR / non-buyable units are left at zero.
 */
public record BlitzMilitaryRules(
        double warRangeMin,
        double warRangeMax,
        int defensiveSlotCap,
        int baseOffensiveSlotCap,
        int sameOpponentLockoutTurns,
        int researchMaxLevel,
        int bitsPerResearchSlot,
        int[] mmrUnitOrdinals,
        int[] mmrMaxByUnitOrdinal,
        int[] capacityPerBuildingByUnitOrdinal,
        int[] dailyBuyPerBuildingByUnitOrdinal,
        int[] capacityResearchOrdinalByUnitOrdinal,
        int[] capacityResearchBonusByUnitOrdinal,
        int[] dailyBuyResearchOrdinalByUnitOrdinal,
        int[] dailyBuyResearchBonusByUnitOrdinal,
        boolean[] propagandaAffectsDailyBuyByUnitOrdinal,
        int[] militaryProjectOrdinals,
        int propagandaProjectOrdinal,
        int missileLaunchPadProjectOrdinal,
        int spaceProgramProjectOrdinal,
        int nuclearResearchProjectOrdinal,
        int nuclearLaunchProjectOrdinal,
        int intelligenceAgencyProjectOrdinal,
        int spySatelliteProjectOrdinal,
        double propagandaDailyBuyMultiplier,
        int missileLaunchPadDailyBuy,
        int spaceProgramDailyBuy,
        int nuclearResearchDailyBuy,
        int nuclearLaunchDailyBuy,
        int spyBaseDailyBuy,
        int spyIntelligenceAgencyDailyBuyBonus,
        int spySatelliteDailyBuyBonus,
        int spyBaseUnitCap,
        int spyIntelligenceAgencyUnitCap,
        int[] researchScoreByOrdinal,
        int projectScore,
        double[] unitScoreByOrdinal,
        boolean[] unitScoreCappedAt50ByOrdinal,
        int[] unitBaseMonetaryValueCentsByOrdinal
) {

    private static final BlitzMilitaryRules INSTANCE = build();

    public static BlitzMilitaryRules instance() {
        return INSTANCE;
    }

    private static BlitzMilitaryRules build() {
        MilitaryUnit[] units = MilitaryUnit.values;
        int unitCount = units.length;

        int[] mmrMax = new int[unitCount];
        int[] capPerBuilding = new int[unitCount];
        int[] buyPerBuilding = new int[unitCount];
        int[] capResearchOrd = new int[unitCount];
        int[] capResearchBonus = new int[unitCount];
        int[] buyResearchOrd = new int[unitCount];
        int[] buyResearchBonus = new int[unitCount];
        boolean[] propagandaAffects = new boolean[unitCount];

        for (int i = 0; i < unitCount; i++) {
            capResearchOrd[i] = -1;
            buyResearchOrd[i] = -1;
        }

        int[] mmrOrdinalsTmp = new int[]{
                MilitaryUnit.SOLDIER.ordinal(),
                MilitaryUnit.TANK.ordinal(),
                MilitaryUnit.AIRCRAFT.ordinal(),
                MilitaryUnit.SHIP.ordinal()
        };

        for (int ordinal : mmrOrdinalsTmp) {
            MilitaryUnit unit = units[ordinal];
            MilitaryBuilding building = unit.getBuilding();
            if (building == null) {
                throw new IllegalStateException("MMR unit without building: " + unit);
            }
            mmrMax[ordinal] = building.cap(p -> false);
            capPerBuilding[ordinal] = building.getUnitCap();
            buyPerBuilding[ordinal] = building.getUnitDailyBuy();
            propagandaAffects[ordinal] = true;

            Research capResearch = unit.getCapacityResearch();
            if (capResearch != null) {
                capResearchOrd[ordinal] = capResearch.ordinal();
                capResearchBonus[ordinal] = unit.getCapacityAmount();
            }
            Research buyResearch = unit.getRebuyResearch();
            if (buyResearch != null) {
                buyResearchOrd[ordinal] = buyResearch.ordinal();
                buyResearchBonus[ordinal] = unit.getRebuyAmount();
            }
        }

        // Military-relevant projects shown as chips in the row expander.
        Project[] militaryProjects = new Project[]{
                Projects.PROPAGANDA_BUREAU,
                Projects.MISSILE_LAUNCH_PAD,
                Projects.IRON_DOME,
                Projects.VITAL_DEFENSE_SYSTEM,
                Projects.SPACE_PROGRAM,
                Projects.NUCLEAR_RESEARCH_FACILITY,
                Projects.NUCLEAR_LAUNCH_FACILITY,
        };
        int[] militaryProjectOrdinals = new int[militaryProjects.length];
        for (int i = 0; i < militaryProjects.length; i++) {
            militaryProjectOrdinals[i] = militaryProjects[i].ordinal();
        }

        // Score coefficients so the frontend can recompute nation score from
        // base inputs (cities/projects/research/units/avg infra) when the
        // user edits overrides. Mirrors PW.computeNonInfraScoreBase /
        // PW.scoreBreakdown / MilitaryUnit.getScore.
        Research[] researchValues = Research.values;
        int[] researchScoreByOrdinal = new int[researchValues.length];
        for (Research rs : researchValues) {
            researchScoreByOrdinal[rs.ordinal()] = rs.getScore();
        }
        double[] unitScoreByOrdinal = new double[unitCount];
        boolean[] unitScoreCappedAt50ByOrdinal = new boolean[unitCount];
        int[] unitBaseMonetaryValueCentsByOrdinal = new int[unitCount];
        for (MilitaryUnit unit : units) {
            long cents = Math.round(Math.max(0d, unit.getBaseMonetaryValue(1)) * 100d);
            unitBaseMonetaryValueCentsByOrdinal[unit.ordinal()] = (int) Math.min((long) Integer.MAX_VALUE, cents);
        }
        // Score-counted units (mirrors PW.scoreBreakdown). INFRASTRUCTURE is
        // intentionally excluded here because its contribution is added via
        // the separate infra/40 term, not via the unit loop.
        MilitaryUnit[] scoreUnits = new MilitaryUnit[]{
                MilitaryUnit.SOLDIER,
                MilitaryUnit.TANK,
                MilitaryUnit.AIRCRAFT,
                MilitaryUnit.SHIP,
                MilitaryUnit.MISSILE,
                MilitaryUnit.NUKE
        };
        for (MilitaryUnit unit : scoreUnits) {
            int ord = unit.ordinal();
            unitScoreByOrdinal[ord] = unit.getScore(1);
            unitScoreCappedAt50ByOrdinal[ord] = unit.getScore(100) == unit.getScore(50);
        }

        return new BlitzMilitaryRules(
                PW.WAR_RANGE_MIN_MODIFIER,
                PW.WAR_RANGE_MAX_MODIFIER,
                WarSlotRules.defensiveSlotCap(),
                WarSlotRules.baseOffensiveSlotCap(),
                WarSlotRules.sameOpponentLockoutTurns(),
                Research.MAX_LEVEL,
                5,
                mmrOrdinalsTmp,
                mmrMax,
                capPerBuilding,
                buyPerBuilding,
                capResearchOrd,
                capResearchBonus,
                buyResearchOrd,
                buyResearchBonus,
                propagandaAffects,
                militaryProjectOrdinals,
                Projects.PROPAGANDA_BUREAU.ordinal(),
                Projects.MISSILE_LAUNCH_PAD.ordinal(),
                Projects.SPACE_PROGRAM.ordinal(),
                Projects.NUCLEAR_RESEARCH_FACILITY.ordinal(),
                Projects.NUCLEAR_LAUNCH_FACILITY.ordinal(),
                Projects.INTELLIGENCE_AGENCY.ordinal(),
                Projects.SPY_SATELLITE.ordinal(),
                1.1,
                2,
                3,
                1,
                1,
                2,
                1,
                1,
                50,
                60,
                researchScoreByOrdinal,
                Projects.getScore(),
                unitScoreByOrdinal,
                unitScoreCappedAt50ByOrdinal,
                unitBaseMonetaryValueCentsByOrdinal
        );
    }
}
