package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.UnitEconomy;

import java.util.function.Predicate;

/**
 * Shared owner for live-sim nation capacity reads plus explicit synthetic overrides.
 */
public final class NationCapacityRules {
    static final int UNSPECIFIED_CAP_OVERRIDE = -1;

    private NationCapacityRules() {
    }

    public static int normalizeMaxOffOverride(int configuredMaxOffSlots, long projectBits) {
        int derived = WarSlotRules.offensiveSlotCap(projectBits);
        return configuredMaxOffSlots == derived ? UNSPECIFIED_CAP_OVERRIDE : configuredMaxOffSlots;
    }

    public static int maxOffSlots(int explicitOverride, long projectBits) {
        return explicitOverride > 0 ? explicitOverride : WarSlotRules.offensiveSlotCap(projectBits);
    }

    public static int dailyBuyCap(
            int explicitBaseOverride,
            int cities,
            MilitaryUnit unit,
            Predicate<Project> hasProject,
            int researchBits,
            int beigeTurns,
            boolean hasActiveWars
    ) {
        int baseCap = explicitBaseOverride >= 0
                ? explicitBaseOverride
                : UnitEconomy.maxBuyPerDayFor(cities, unit, hasProject, research -> research.getLevel(researchBits));
        return UnitEconomy.applyBeigeDailyBuyBonus(baseCap, unit, beigeTurns, hasActiveWars);
    }

    public static int unitCap(
            int explicitOverride,
            MilitaryUnit unit,
            SpecialistCityProfile[] cityProfilesFlat,
            double[] cityInfraFlat,
            int cityOffset,
            int cityCount,
            Predicate<Project> hasProject,
            int researchBits
    ) {
        if (explicitOverride >= 0) {
            return explicitOverride;
        }
        if (cityOffset < 0 || cityCount < 0 || cityOffset + cityCount > cityProfilesFlat.length || cityOffset + cityCount > cityInfraFlat.length) {
            throw new IllegalArgumentException("cityOffset/cityCount exceed available city profile state");
        }
        return derivedUnitCap(unit, cityProfilesFlat, cityInfraFlat, cityOffset, cityCount, hasProject, researchBits);
    }

    private static int derivedUnitCap(
            MilitaryUnit unit,
            SpecialistCityProfile[] cityProfilesFlat,
            double[] cityInfraFlat,
            int cityOffset,
            int cityCount,
            Predicate<Project> hasProject,
            int researchBits
    ) {
        MilitaryBuilding building = unit.getBuilding();
        int built = 0;
        double totalPopulation = 0d;
        if (building != null) {
            boolean populationLimited = building.getCitizensPerUnit() > 0d;
            for (int i = 0; i < cityCount; i++) {
                int absoluteIndex = cityOffset + i;
                SpecialistCityProfile profile = cityProfilesFlat[absoluteIndex];
                built += militaryBuildingCount(profile, unit);
                if (populationLimited) {
                    totalPopulation += profile.population(cityInfraFlat[absoluteIndex]);
                }
            }
        }
        return unit.getCap(built, totalPopulation, hasProject, researchBits);
    }

    private static int militaryBuildingCount(SpecialistCityProfile profile, MilitaryUnit unit) {
        return switch (unit) {
            case SOLDIER -> profile.barracks();
            case TANK -> profile.factories();
            case AIRCRAFT -> profile.hangars();
            case SHIP -> profile.drydocks();
            default -> 0;
        };
    }
}