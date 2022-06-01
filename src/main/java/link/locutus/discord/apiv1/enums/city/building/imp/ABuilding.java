package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.Map;
import java.util.function.Predicate;

public class ABuilding<task> implements Building {
    private final Map<ResourceType, Double> cost;
    private final int pollution;
    private final int cap;
    private final Map<ResourceType, Double> upkeep;
    private final double[] costArr;
    private final double costConverted = -1;

    private final double[] upkeepArr;
    private final String name;
    private final double upkeepConverted;
    private int ordinal;

    public ABuilding(Building parent) {
        this(parent.name(), parent.cap(f -> false), parent.pollution(f -> false), parent.cost(), parent.upkeep());
        this.ordinal = parent.ordinal();
    }

    public ABuilding(String name, int cap, int pollution, Map<ResourceType, Double> cost, Map<ResourceType, Double> upkeep) {
        this.name = name;
        this.cap = cap;
        this.pollution = pollution;
        this.cost = cost;
        this.upkeep = upkeep;
        this.costArr = new double[ResourceType.values.length];
        for (Map.Entry<ResourceType, Double> entry : cost.entrySet()) {
            costArr[entry.getKey().ordinal()] += entry.getValue();
        }

        upkeepArr = new double[ResourceType.values.length];
        for (Map.Entry<ResourceType, Double> entry : upkeep.entrySet()) {
            upkeepArr[entry.getKey().ordinal()] += entry.getValue();
        }

        this.upkeepConverted = PnwUtil.convertedTotal(upkeepArr);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Map<ResourceType, Double> cost() {
        return cost;
    }

    @Override
    public double costConverted(double num) {
        return costConverted * num;
    }

    @Override
    public double[] cost(double[] buffer, double num) {
        for (int i = 0; i < costArr.length; i++) {
            buffer[i] += costArr[i] * num;
        }
        return buffer;
    }

    @Override
    public Map<ResourceType, Double> upkeep() {
        return upkeep;
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
    public double profitConverted(double rads, Predicate<Project> hasProject, JavaCity city, int amt) {
        return -upkeepConverted;
    }

    @Override
    public double[] profit(double rads, Predicate<Project> hasProject, JavaCity city, double[] profitBuffer) {
        int amt = city.get(this);
        if (amt > 0) {
            for (int i = 0; i < profitBuffer.length; i++) {
                profitBuffer[i] -= upkeepArr[i] * amt;
            }
        }
        return profitBuffer;
    }
}
