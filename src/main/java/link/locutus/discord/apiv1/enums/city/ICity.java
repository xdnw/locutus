package link.locutus.discord.apiv1.enums.city;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.web.WebUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public interface ICity {
    Boolean getPowered();

    int getPoweredInfra();

    double getInfra();

    default double getFreeInfra() {
        return getInfra() - getNumBuildings() * 50;
    }

    default int getSlots() {
        return (int) (ArrayUtil.toCents(getInfra()) / 50_00);
    }

    default int getFreeSlots() {
        return this.getSlots() - getNumBuildings();
    }

    default double caclulateBuildingCostConverted(JavaCity from) {
        double total = 0;
        for (Building building : Buildings.values()) {
            int amtA = getBuilding(building);
            int amtB = from.getBuilding(building);
            if (amtA != amtB) {
                if (amtB > amtA) {
                    total += building.getNMarketCost((amtB - amtA) * 0.5);
                } else {
                    total += building.getNMarketCost(amtB - amtA);
                }
            }
        }
        return total;
    }

    default double calculateCostConverted(JavaCity from) {
        double total = caclulateBuildingCostConverted(from);
        if (this.getInfra() > from.getInfra()) {
            total += PW.City.Infra.calculateInfra(from.getInfra(), getInfra());
        }
        if (!Objects.equals(getLand(), from.getLand())) {
            total += PW.City.Land.calculateLand(from.getLand(), getLand());
        }
        return total;
    }

    double getLand();

    int getBuilding(Building building);

    int getBuildingOrdinal(int ordinal);

    int calcCommerce(Predicate<Project> hasProject);

    int calcPopulation(Predicate<Project> hasProject);

    double calcDisease(Predicate<Project> hasProject);

    double calcCrime(Predicate<Project> hasProject);

    int calcPollution(Predicate<Project> hasProject);

    default int getAgeDays() {
        if (getCreated() <= 0) return 1;
        if (getCreated() == Long.MAX_VALUE) return 1;
        return (int) Math.max(1, TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - getCreated()));
    }

    long getCreated();

    public int getNumBuildings();

    @Command(desc = "Get the required infrastructure level for the number of buildings")
    default int getRequiredInfra() {
        return getNumBuildings() * 50;
    }

    @Command(desc = "The city build json")
    default String toJson(@Default Boolean pretty) {
        JsonObject object = new JsonObject();

        Map<String, String> json = new Object2ObjectLinkedOpenHashMap<>();
        json.put("infra_needed", getRequiredInfra() + "");
        json.put("imp_total", getNumBuildings() + "");
        if (getLand() > 0) {
            json.put("land", getLand() + "");
        }
        if (getAgeDays() > 0) {
            json.put("age", getAgeDays() + "");
        }
        for (Building building : Buildings.values()) {
            int amt = getBuilding(building);
            if (amt == 0) continue;
            json.put(building.nameSnakeCase(), amt + "");
        }

        if (pretty == Boolean.TRUE) {
            return new GsonBuilder().setPrettyPrinting().create().toJson(json);
        }
        return WebUtil.GSON.toJson(json);
    }

    default Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject) {
        return missileDamageRange(getInfra(), getLand(), calcPopulation(hasProject), hasProject.test(Projects.GUIDING_SATELLITE));
    }

    int getNuke_turn();

    default INationCity findBest(Continent continent, int numCities, ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal, Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow) {
        return CityFallbackHeuristic.findBest(this, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow, Locutus.imp().getNationDB().getCities());
    }

    default INationCity findBestFromDonors(Continent continent, int numCities, ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal, Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow, Collection<DBCity> donors) {
        return CityFallbackHeuristic.findBest(this, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow, donors);
    }

    default INationCity findBestExactSlotOnlyFromDonors(Continent continent, int numCities, ToDoubleFunction<INationCity> valueFunction, Predicate<INationCity> goal, Predicate<Project> hasProject, double rads, double grossModifier, Double infraLow, Collection<DBCity> donors) {
        return CityFallbackHeuristic.findBestExactSlotOnly(this, continent, numCities, valueFunction, goal, hasProject, rads, grossModifier, infraLow, donors);
    }

    default Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
        return nukeDamageRange(getInfra(), getLand(), calcPopulation(hasProject), hasProject.test(Projects.GUIDING_SATELLITE));
    }

    static Map.Entry<Integer, Integer> missileDamageRange(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageRange(infra, land, population, guidingSatellite, true);
    }

    static Map.Entry<Integer, Integer> nukeDamageRange(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageRange(infra, land, population, guidingSatellite, false);
    }

    static int missileDamageMin(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageBound(infra, land, population, guidingSatellite, true, true);
    }

    static int missileDamageMax(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageBound(infra, land, population, guidingSatellite, true, false);
    }

    static int nukeDamageMin(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageBound(infra, land, population, guidingSatellite, false, true);
    }

    static int nukeDamageMax(double infra, double land, int population, boolean guidingSatellite) {
        return projectileDamageBound(infra, land, population, guidingSatellite, false, false);
    }

    private static Map.Entry<Integer, Integer> projectileDamageRange(
            double infra,
            double land,
            int population,
            boolean guidingSatellite,
            boolean missile
    ) {
        return new KeyValue<>(
                projectileDamageBound(infra, land, population, guidingSatellite, missile, true),
                projectileDamageBound(infra, land, population, guidingSatellite, missile, false)
        );
    }

    private static int projectileDamageBound(
            double infra,
            double land,
            int population,
            boolean guidingSatellite,
            boolean missile,
            boolean minBound
    ) {
        double normalizedInfra = Math.max(0d, infra);
        double normalizedLand = Math.max(1d, land);
        double density = population / normalizedLand;
        double factor = guidingSatellite ? 1.2d : 1d;
        double destroyed;
        if (missile) {
            destroyed = minBound
                    ? Math.min(normalizedInfra, Math.min(1700d, normalizedInfra * 0.8d + 150d)) * factor
                    : Math.min(normalizedInfra, Math.max(Math.max(2000d, density * 13.5d), normalizedInfra * 0.8d + 150d)) * factor;
        } else {
            destroyed = minBound
                    ? Math.min(normalizedInfra, Math.min(300d, normalizedInfra * 0.3d + 100d)) * factor
                    : Math.min(normalizedInfra, Math.max(Math.max(350d, density * 3d), normalizedInfra * 0.8d + 150d)) * factor;
        }
        return (int) Math.round(destroyed);
    }

    @Command(desc = """
            Get the MMR of the city
            In the form `5553`
            Each digit is the number of buildings (barracks, factory, hangar, drydock)""")
    default String getMMR() {
        return getBuilding(Buildings.BARRACKS) + "" + getBuilding(Buildings.FACTORY) + getBuilding(Buildings.HANGAR) + getBuilding(Buildings.DRYDOCK);
    }



    @Command(desc = "Get the required infrastructure level for the number of buildings without military buildings")
    default int getRequiredInfraWithoutMilitaryAndPower() {
        int numBuildings = 0;
        for (Building building : Buildings.values()) {
            if (building.getType() == BuildingType.POWER || building.getType() == BuildingType.MILITARY) {
                continue;
            }
            numBuildings += getBuilding(building);
        }
        return numBuildings * 50;
    }

    default int getMaxCommerce(Predicate<Project> hasProject) {
        int maxCommerce;
        if (hasProject.test(Projects.INTERNATIONAL_TRADE_CENTER)) {
            if (hasProject.test(Projects.TELECOMMUNICATIONS_SATELLITE)) {
                maxCommerce = 125;
            } else {
                maxCommerce = 115;
            }
        } else {
            maxCommerce = 100;
        }
        return maxCommerce;
    }

    default boolean canBuild(Continent continent, Predicate<Project> hasProject, boolean throwError) {
        // check the building can exist in the continent and that the cap for that building is sufficient
        for (Building building : Buildings.values()) {
            int amt = getBuilding(building);
            if (amt <= 0) continue;
            if (!building.canBuild(continent)) {
                if (throwError) {
                    throw new IllegalArgumentException("Building " + building.name() + " cannot be built in " + continent);
                }
                return false;
            }
            if (amt > building.getCap(hasProject)) {
                if (throwError) {
                    throw new IllegalArgumentException("Building " + building.name() + " has a cap of " + building.getCap(hasProject));
                }
                return false;
            }
        }
        return true;
    }

    default int[] getMMRArray() {
        return new int[]{getBuilding(Buildings.BARRACKS), getBuilding(Buildings.FACTORY), getBuilding(Buildings.HANGAR), getBuilding(Buildings.DRYDOCK)};
    }

    default MMRInt getMMRInt() {
        return new MMRInt(getMMRArray());
    }

    default int getNumBuildingsMatching(Predicate<Building> filter) {
        int num = 0;
        for (Building building : Buildings.values()) {
            if (filter.test(building)) {
                num += getBuilding(building);
            }
        }
        return num;
    }
}
