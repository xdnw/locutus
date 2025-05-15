package link.locutus.discord.apiv1.enums.city;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.util.PW;
import link.locutus.discord.web.WebUtil;

import link.locutus.discord.util.scheduler.KeyValue;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public interface ICity {
    Boolean getPowered();

    int getPoweredInfra();

    double getInfra();

    default double getFreeInfra() {
        return getInfra() - getNumBuildings() * 50;
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

    int getAgeDays();

    public int getNumBuildings();

    @Command(desc = "Get the required infrastructure level for the number of buildings")
    default int getRequiredInfra() {
        return getNumBuildings() * 50;
    }

    @Command(desc = "The city build json")
    default String toJson(@Default Boolean pretty) {
        JsonObject object = new JsonObject();

        Map<String, String> json = new HashMap<>();
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
        double density = calcPopulation(hasProject) / getLand();
        double infra = getInfra();
        double factor = 1;
        if (hasProject.test(Projects.GUIDING_SATELLITE)) {
            factor = 1.2;
        }
        double destroyedMin = Math.min(infra, Math.min(1700, infra * 0.8 + 150)) * factor;
        double destroyedMax = Math.min(infra, Math.max(Math.max(2000, density * 13.5), infra * 0.8 + 150)) * factor;
        return new KeyValue<>((int) Math.round(destroyedMin), (int) Math.round(destroyedMax));
    }

    default Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
        double density = calcPopulation(hasProject) / getLand();
        double infra = getInfra();
        double factor = 1;
        if (hasProject.test(Projects.GUIDING_SATELLITE)) {
            factor = 1.2;
        }
        double destroyedMin = Math.min(infra, Math.min(300, infra * 0.3 + 100)) * factor;
        double destroyedMax = Math.min(infra, Math.max(Math.max(350, density * 3), infra * 0.8 + 150)) * factor;
        return new KeyValue<>((int) Math.round(destroyedMin), (int) Math.round(destroyedMax));
    }

    @Command(desc = """
            Get the MMR of the city
            In the form `5553`
            Each digit is the number of buildings (barracks, factory, hangar, drydock)""")
    default String getMMR() {
        return getBuilding(Buildings.BARRACKS) + "" + getBuilding(Buildings.FACTORY) + getBuilding(Buildings.HANGAR) + getBuilding(Buildings.DRYDOCK);
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
}
