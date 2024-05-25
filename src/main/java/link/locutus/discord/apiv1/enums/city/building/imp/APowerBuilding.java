package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.util.PW;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.PowerBuilding;

import java.util.Collections;
import java.util.Map;

public class APowerBuilding extends ABuilding implements PowerBuilding {
    private final Map<ResourceType, Double> input;
    private final double[] inputArr;
    private final int infra;
    private final int infraLevels;
    private final ResourceType resource;
    private final double resourceValue;

    public APowerBuilding(BuildingBuilder parent, ResourceType input, double inputAmt, int infraLevels, int infraPowered) {
        super(parent);
        if (input == null) {
            this.input = Collections.emptyMap();
        } else {
            this.input = Map.of(input, inputAmt);
        }
        this.inputArr = ResourceType.resourcesToArray(this.input);
        this.resource = input;
        this.infraLevels = infraLevels;
        this.infra = infraPowered;
        this.resourceValue = resource != null ? ResourceType.convertedTotal(resource, 1) : 0;
    }

    @Override
    public ResourceType getPowerResource() {
        return resource;
    }

    @Override
    public Map<ResourceType, Double> input(int infra) {
        double levels = (infra + infraLevels - 1d) / infraLevels;
        return PW.multiply(input, levels);
    }

    @Override
    public int getInfraBase() {
        return infraLevels;
    }

    @Override
    public int getInfraMax() {
        return infra;
    }

    @Override
    public double consumptionConverted(int infra) {
        if (resourceValue == 0) return 0;
        int amt;
        if (infra < infraLevels) {
            amt = 1;
        } else {
            amt = (Math.min(infra, this.infra) + infraLevels - 1) / infraLevels;
        }
        return -(amt * resourceValue * inputArr[resource.ordinal()]);
    }

    @Override
    public double[] consumption(int infra, double[] profitBuffer, int turns) {
        if (resource != null) {
            double amt = getPowerResourceConsumed(infra);
            profitBuffer[resource.ordinal()] -= amt * inputArr[resource.ordinal()] * turns / 12;
        }
        return profitBuffer;
    }

    @Override
    public double getPowerResourceConsumed(int infra) {
        if (resource != null) {
            return (Math.min(infra, this.infra) + infraLevels - 1) / infraLevels;
        }
        return 0;
    }

    @Override
    public BuildingType getType() {
        return BuildingType.POWER;
    }
}
