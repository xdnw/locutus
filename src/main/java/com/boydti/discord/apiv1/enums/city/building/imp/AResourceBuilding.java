package com.boydti.discord.apiv1.enums.city.building.imp;

import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.domains.Nation;
import com.boydti.discord.apiv1.domains.subdomains.SCityContainer;
import com.boydti.discord.apiv1.enums.Continent;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import com.boydti.discord.apiv1.enums.city.building.Building;
import com.boydti.discord.apiv1.enums.city.building.ResourceBuilding;
import com.boydti.discord.apiv1.enums.city.project.Project;

import java.util.function.Function;
import java.util.function.Predicate;

public class AResourceBuilding extends ABuilding implements ResourceBuilding {
    private final int baseInput;
    private final Function<Nation, Boolean> hasWorks;
    private final double boostFactor;
    private final ResourceType output;
    private final ResourceType[] inputs;
    private boolean[] continents;

    public AResourceBuilding(AResourceBuilding other) {
        this(other, other.baseInput, other.boostFactor, other.hasWorks, other.output, other.inputs);
    }

    public AResourceBuilding(Building parent, int baseInput, double boostFactor, Function<Nation, Boolean> hasWorks, ResourceType output, ResourceType... inputs) {
        super(parent);
        this.baseInput = baseInput;
        this.boostFactor = boostFactor;
        this.hasWorks = hasWorks;
        this.output = output;
        this.inputs = inputs;
    }

    public AResourceBuilding continents(Continent... continents) {
        if (this.continents == null) {
            this.continents = new boolean[Continent.values().length];
        }
        for (Continent continent : continents) {
            this.continents[continent.ordinal()] = true;
        }
        return this;
    }

    @Override
    public ResourceType resource() {
        return output;
    }

    @Override
    public int baseProduction() {
        return baseInput * 3;
    }

    @Override
    public double profitConverted(double rads, Predicate hasProjects, JavaCity city, int amt) {
        double profit = super.profitConverted(rads, hasProjects, city, amt);

        int improvements = city.get(this);

        Project project = output.getProject();
        boolean hasProject = project != null && hasProjects.test(project);

        double production = output.getProduction(rads, hasProject, city.getLand(), improvements);
        if (production != 0) {
            profit += PnwUtil.convertedTotalPositive(output, production);

            double inputAmt = output.getInput(rads, hasProject, city, improvements);

            if (inputAmt > 0) {
                switch (inputs.length) {
                    case 0:
                        break;
                    case 1:
                        profit -= PnwUtil.convertedTotalNegative(inputs[0], inputAmt);
                        break;
                    default:
                        for (ResourceType input : inputs) {
                            profit -= PnwUtil.convertedTotalNegative(input, inputAmt);
                        }
                }
            }
        }
        return profit;
    }

    @Override
    public double[] profit(double rads, Predicate hasProjects, JavaCity city, double[] profitBuffer) {
        profitBuffer = super.profit(rads, hasProjects, city, profitBuffer);

        ResourceType type = resource();
        int improvements = city.get(this);

        Project project = type.getProject();
        boolean hasProject = project != null && hasProjects.test(project);

        double production = type.getProduction(rads, hasProject, city.getLand(), improvements);
        if (production != 0) {
            profitBuffer[type.ordinal()] += production;

            double inputAmt = type.getInput(rads, hasProject, city, improvements);

            for (ResourceType input : inputs) {
                if (inputAmt != 0) {
                    profitBuffer[input.ordinal()] -= inputAmt;
                }
            }
        }
        return profitBuffer;
    }

    public double getProduction(Nation nation, double land, JavaCity city) {
        return getProduction(nation, land, city.get(this));
    }

    public double getProduction(Nation nation, JavaCity city) {
        return getProduction(nation, city.getLand(), city.get(this));
    }

    public double getProduction(Nation nation, SCityContainer city, int improvements) {
        return getProduction(nation, Double.parseDouble(city.getLand()), improvements);
    }

    public double getProduction(Nation nation, double land, int improvements) {
        double base = baseProduction();

        if (inputs.length > 0 && nation != null && hasWorks.apply(nation)) {
            base = base * boostFactor;
        }

        return base * (1+0.5*((improvements - 1)/(cap() - 1))) *improvements;
    }

    @Override
    public boolean canBuild(Continent continent) {
        return this.continents == null || continents[continent.ordinal()];
    }
}
