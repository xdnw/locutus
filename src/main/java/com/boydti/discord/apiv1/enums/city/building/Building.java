package com.boydti.discord.apiv1.enums.city.building;

import com.boydti.discord.apiv1.domains.Nation;
import com.boydti.discord.apiv1.enums.Continent;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import com.boydti.discord.apiv1.enums.city.building.imp.ACommerceBuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.AMilitaryBuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.APowerBuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.AResourceBuilding;
import com.boydti.discord.apiv1.enums.city.project.Project;


import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public interface Building {

    String name();

    default String nameSnakeCase() {
        return name().replaceAll("([A-Z])", "_$1").toLowerCase();
    }

    Map<ResourceType, Double> cost();

    double costConverted(double num);

    double[] cost(double[] buffer, double num);

    Map<ResourceType, Double> upkeep();

    /**
     * @return how many of these buildings can be built in a city
     */
    default int cap() {
        return cap(f -> false);
    }

    int cap(Predicate<Project> hasProject);

    int pollution(Predicate<Project> hasProject);

    default boolean canBuild(Continent continent) {
        return true;
    }

    default MilitaryBuilding unit(MilitaryUnit unit, int max, int perDay, double requiredCitizens) {
        return new AMilitaryBuilding(this, unit, max, perDay, requiredCitizens);
    }

    default PowerBuilding power(ResourceType input, double inputAmt, int infraBase, int infraMax) {
        return new APowerBuilding(this, input, inputAmt, infraBase, infraMax);
    }

    default ResourceBuilding resource(ResourceType output) {
        return resource(output, t -> false);
    }

    default ResourceBuilding resource(ResourceType output, Function<Nation, Boolean> hasWorks) {
        return resource(output.getBaseInput(), output.getBoostFactor(), hasWorks, output, output.getInputs());
    }

    default ResourceBuilding resource(int baseInput, double boostFactor, Function<Nation, Boolean> hasWorks, ResourceType output, ResourceType... inputs) {
        return new AResourceBuilding(this, baseInput, boostFactor, hasWorks, output, inputs);
    }

    default CommerceBuilding commerce(int commerce) {
        return new ACommerceBuilding(this, commerce);
    }

    int ordinal();

    double profitConverted(double rads, Predicate<Project> hasProject, JavaCity city, int amt);

    double[] profit(double rads, Predicate<Project> hasProject, JavaCity city, double[] profitBuffer);
}