package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class ABuilding implements Building {
    private final int pollution;
    private final int cap;
    private final double[] costArr;
    private final Supplier<Double> costConverted;

    private final double[] upkeepArr;
    private final String name;
    private final Supplier<Double> upkeepConverted;
    private int ordinal;

    public ABuilding(BuildingBuilder parent) {
        this(parent.getName(), parent.getCap(), parent.getPollution(), parent.getCost(), parent.getUpkeep());
    }

    public ABuilding(String name, int cap, int pollution, double[] cost, double[] upkeep) {
        this.name = name;
        this.cap = cap;
        this.pollution = pollution;
        this.costArr = cost;
        this.costConverted = ResourceType.convertedCostLazy(costArr);
        this.upkeepArr = upkeep;
        this.upkeepConverted = ResourceType.convertedCostLazy(upkeepArr);
    }

    public double getUpkeepConverted(Predicate<Project> hasProject) {
        return upkeepConverted.get();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double cost(ResourceType type) {
        return this.costArr[type.ordinal()];
    }

    @Override
    public double getNMarketCost(double num) {
        return costConverted.get() * num;
    }

    @Override
    public double upkeep(ResourceType type, Predicate<Project> hasProject) {
        return upkeepArr[type.ordinal()];
    }

    @Override
    public int cap(Predicate<Project> hasProject) {
        return cap;
    }

    @Override
    public int pollution(Predicate<Project> hasProject) {
        return pollution;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    @Override
    public int ordinal() {
        return ordinal;
    }

    @Override
    public double profitConverted(Continent continent, double rads, Predicate<Project> hasProject, double land, int amt) {
        return -(getUpkeepConverted(hasProject) * amt);
    }

    @Override
    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProject, ICity city, double[] profitBuffer, int turns, int amt) {
        if (amt > 0) {
            for (ResourceType type : ResourceType.values) {
                profitBuffer[type.ordinal()] -= upkeep(type, hasProject) * amt * turns / 12;
            }
        }
        return profitBuffer;
    }

    @Override
    public abstract BuildingType getType();

    @Override
    public String toString() {
        return name();
    }
}
