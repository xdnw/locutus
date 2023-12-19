package link.locutus.discord.apiv1.enums.city.building;

import com.google.common.base.CaseFormat;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;


import java.util.Arrays;
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


}