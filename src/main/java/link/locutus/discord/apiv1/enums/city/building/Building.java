package link.locutus.discord.apiv1.enums.city.building;

import com.google.common.base.CaseFormat;
import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBNation;


import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Building {

    @Command(desc = "Get the name of this building")
    String name();

    default String nameUpperUnd() {
        String name = name();
        if (name.startsWith("imp")) name = name.substring(3);
        return CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, name);
    }

    @Command(desc = "Get the name of this building in snake case")
    default String nameSnakeCase() {
        return name().replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    @Command(desc = "Get the cost of this building for a specific resource")
    double cost(ResourceType type);

    @Command(desc = "Get the resource costs of this building")
    default Map<ResourceType, Double> getCostMap() {
        return Arrays.stream(ResourceType.values).collect(Collectors.toMap(type -> type, this::cost));
    }

    @Command(desc = "Get the market cost of N of this building")
    double getNMarketCost(double num);

    @Command(desc = "Get the market cost of this building")
    default double getMarketCost() {
        return getNMarketCost(1);
    }


    default double[] cost(double[] buffer, double num) {
        if (num > 0) {
            for (ResourceType type : ResourceType.values) {
                buffer[type.ordinal()] += cost(type) * num;
            }
        } else if (num < 0) {
            // 50% of money back for selling
            buffer[0] += cost(ResourceType.MONEY) * num * 0.5;
            for (int i = 2; i < ResourceType.values.length; i++) {
                // 75% of rss back for selling
                buffer[i] += cost(ResourceType.values[i]) * num * 0.75;
            }
        }
        return buffer;
    }

    @Command(desc = "Get the upkeep of this building for a specific resource")
    default double getUpkeep(ResourceType type, @Default Predicate<Project> hasProject) {
        return upkeep(type, hasProject == null ? f -> false : hasProject);
    }

    @Command(desc = "Get the upkeep resources of this building")
    default Map<ResourceType, Double> getUpkeepMap(@Default Predicate<Project> hasProject) {
        Predicate<Project> hasProject1 = hasProject == null ? f -> false : hasProject;
        return Arrays.stream(ResourceType.values).collect(Collectors.toMap(type -> type, type -> getUpkeep(type, hasProject1)));
    }

    double upkeep(ResourceType type, Predicate<Project> hasProject);


    @Command(desc = "Get max number of this building that can be built (per city)")
    default int getCap(@Default Predicate<Project> hasProject) {
        return cap(hasProject == null ? f -> false : hasProject);
    }

    /**
     * Max amount of this building that can be built (per city)
     * @param hasProject
     * @return
     */
    int cap(Predicate<Project> hasProject);

    @Command(desc = "Get the pollution created by this building")
    default int getPollution(@Default Predicate<Project> hasProject) {
        return pollution(hasProject == null ? f -> false : hasProject);
    }

    int pollution(Predicate<Project> hasProject);

    @Command(desc = "If this building can be built in a specific continent")
    default boolean canBuild(Continent continent) {
        return true;
    }

    @Command(desc = "Get the numeric id of this building")
    int ordinal();

    double profitConverted(Continent continent, double rads, Predicate<Project> hasProject, JavaCity city, int amt);

    double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, JavaCity city, double[] profitBuffer, int turns);

    @Command(desc = "Get the continents this building can be built on")
    default Set<Continent> getContinents() {
        return Arrays.stream(Continent.values()).filter(this::canBuild).collect(Collectors.toSet());
    }

    @Command(desc = "Get the average number of this building across the specified nations")
    default int getAverage(Set<DBNation> nations) {
        int sum = 0;
        Set<Building> buildings = Set.of(this);
        for (DBNation nation : nations) {
            sum += nation.getBuildings(buildings);
        }
        return sum / nations.size();
    }

    @Command(desc = "Get the total number of this building across the specified nations")
    default int getTotal(Set<DBNation> nations) {
        int sum = 0;
        Set<Building> buildings = Set.of(this);
        for (DBNation nation : nations) {
            sum += nation.getBuildings(buildings);
        }
        return sum;
    }

    @Command(desc = "Get the number of nations that can build this building")
    default int countCanBuild(Set<DBNation> nations) {
        int sum = 0;
        for (DBNation nation : nations) {
            if (canBuild(nation.getContinent())) sum++;
        }
        return sum;
    }


    @Command(desc = "The base infrastructure powered\nOnly applies to power buildings, else 0")
    default int getInfraBase() {
        return 0;
    }

    @Command(desc = "The max infrastructure powered\nOnly applies to power buildings, else 0")
    default int getInfraMax() {
        return 0;
    }

    @Command(desc = "The resource consumed by this building for generating power\nOnly applies to power buildings, else null")
    default ResourceType getPowerResource() {
        return null;
    }

    @Command(desc = "The amount of the resource consumed by this building for generating power\nOnly applies to power buildings, else 0")
    default double getPowerResourceConsumed(int infra) {
        return 0;
    }

    @Command(desc = "The base production of this building\nOnly applies to resource buildings, else 0")
    default int getBaseProduction() {
        return 0;
    }

    @Command(desc = "The resource produced by this building\nOnly applies to resource buildings, else null")
    default ResourceType getResourceProduced() {
        return null;
    }

    @Command(desc = "The resources consumed by this building\nOnly applies to resource buildings, else empty list")
    default List<ResourceType> getResourceTypesConsumed() {
        return List.of();
    }

    @Command(desc = "Get the military unit this building produces\nOnly applies to military buildings, else null")
    default MilitaryUnit getMilitaryUnit() {
        return null;
    }

    // getCitizensPerUnit
    @Command(desc = "Get the number of citizens required to produce a military unit\nOnly applies to military buildings, else 0")
    default double getRequiredCitizens() {
        return 0;
    }

    @Command(desc = "Get the maximum units that this building can hold\nOnly applies to military buildings, else 0")
    default int getUnitCap() {
        return 0;
    }

    @Command(desc = "Get the number of units that this building can produce per day\nOnly applies to military buildings, else 0")
    default int getUnitDailyBuy() {
        return 0;
    }

    @Command(desc = "Get the commerce from this building\nOnly applies to commerce buildings, else 0")
    default int getCommerce() {
        return 0;
    }

    @Command(desc = "The building type (power, raw, manufacturing, civil, commerce, military)")
    BuildingType getType();
}