package link.locutus.discord.apiv1.enums.city.building;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.imp.ACommerceBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.AMilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.APowerBuilding;
import link.locutus.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import rocker.grant.city;


import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Building {

    String name();

    default String nameSnakeCase() {
        return name().replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    double cost(ResourceType type);

    double costConverted(double num);

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

    double upkeep(ResourceType type, Predicate<Project> hasProject);

    int cap(Predicate<Project> hasProject);

    int pollution(Predicate<Project> hasProject);

    default boolean canBuild(Continent continent) {
        return true;
    }

    int ordinal();

    double profitConverted(Continent continent, double rads, Predicate<Project> hasProject, JavaCity city, int amt);

    double[] profit(Continent continent, double rads, Predicate<Project> hasProject, JavaCity city, double[] profitBuffer, int turns);
}