package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.ResourceBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.util.PW;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AResourceBuilding extends ABuilding implements ResourceBuilding {
    private final int baseInput;
    private final double boostFactor;
    private final ResourceType output;
    private final ResourceType[] inputs;
    private boolean[] continents;
    private Supplier<Double> outputValue;
    private InputValueFunc inputValueFunc;

    public AResourceBuilding(BuildingBuilder parent, ResourceType output) {
        this(parent, output.getBaseInput(), output.getBoostFactor(), output, output.getInputs());
    }
    public AResourceBuilding(BuildingBuilder parent, int baseInput, double boostFactor, ResourceType output, ResourceType... inputs) {
        super(parent);
        this.baseInput = baseInput;
        this.boostFactor = boostFactor;
        this.output = output;
        this.inputs = inputs;
        this.outputValue = () -> {
            double value = ResourceType.convertedTotalPositive(output, 1);
            outputValue = () -> value;
            return value;
        };
        if (inputs.length == 0) {
            this.inputValueFunc = (c, r, h, b, a, p) -> 0d;
        } else {
            this.inputValueFunc = (c0, r0, h0, b0, a0, p0) -> {
                double factor = 1d / output.getManufacturingMultiplier();
                InputValueFunc newFunc;
                if (inputs.length == 1) {
                    double value = ResourceType.convertedTotalNegative(inputs[0], 1);
                    newFunc = (c, r, h, b, a, p) -> {
                        double inputAmt = factor * p;
                        return -(value * inputAmt);
                    };
                } else {
                    double[] values = new double[inputs.length];
                    for (int i = 0; i < inputs.length; i++) {
                        values[i] = ResourceType.convertedTotalNegative(inputs[i], 1);
                    }
                    newFunc = (c, r, h, b, a, p) -> {
                        double inputAmt = factor * p;
                        double profit = 0;
                        for (double value : values) {
                            profit -= value * inputAmt;
                        }
                        return profit;
                    };
                }
                inputValueFunc = (c, r, h, b, a, p) -> {
                    if (p != 0) {
                        return newFunc.apply(c, r, h, b, a, p);
                    }
                    return 0d;
                };
                return inputValueFunc.apply(c0, r0, h0, b0, a0, p0);
            };

        }
    }

    private static interface InputValueFunc {
        public double apply(Continent continent, double rads, Predicate<Project> hasProjects, double land, int amt, double production);
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
    public ResourceType getResourceProduced() {
        return output;
    }

    @Override
    public int getBaseProduction() {
        return baseInput * 3;
    }

    @Override
    public int getBaseInput() {
        return baseInput;
    }

    @Override
    public List<ResourceType> getResourceTypesConsumed() {
        return List.of(inputs);
    }

    @Override
    public double profitConverted(Continent continent, double rads, Predicate<Project> hasProjects, double land, int amt) {
        double profit = super.profitConverted(continent, rads, hasProjects, land, amt);
        double production = output.getProduction(continent, rads, hasProjects, land, amt, -1);
        profit += production * outputValue.get();
        profit += inputValueFunc.apply(continent, rads, hasProjects, land, amt, production);
        return profit;
    }

    @Override
    public double[] profit(Continent continent, double rads, long date, Predicate<Project> hasProjects, ICity city, double[] profitBuffer, int turns, int amt) {
        profitBuffer = super.profit(continent, rads, date, hasProjects, city, profitBuffer, turns, amt);

        ResourceType type = getResourceProduced();

        double production = type.getProduction(continent, rads, hasProjects, city.getLand(), amt, date);
        if (production != 0) {
            profitBuffer[type.ordinal()] += production * turns / 12;

            double inputAmt = type.getInput(continent, rads, hasProjects, city, amt);

            for (ResourceType input : inputs) {
                if (inputAmt != 0) {
                    profitBuffer[input.ordinal()] -= inputAmt * turns / 12;
                }
            }
        }
        return profitBuffer;
    }

    @Override
    public boolean canBuild(Continent continent) {
        return this.continents == null || continents[continent.ordinal()];
    }

    @Override
    public double upkeep(ResourceType type, Predicate<Project> hasProject) {
        return super.upkeep(type, hasProject) * (hasProject.test(Projects.GREEN_TECHNOLOGIES) ? 0.9d : 1d);
    }

    @Override
    public double getUpkeepConverted(Predicate<Project> hasProject) {
        return super.getUpkeepConverted(hasProject) * (hasProject.test(Projects.GREEN_TECHNOLOGIES) ? 0.9d : 1d);
    }

    @Override
    public BuildingType getType() {
        return getResourceProduced().isRaw() ? BuildingType.RAW : BuildingType.MANUFACTURING;
    }
}
