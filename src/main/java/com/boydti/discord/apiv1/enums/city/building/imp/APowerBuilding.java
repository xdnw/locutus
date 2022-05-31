package com.boydti.discord.apiv1.enums.city.building.imp;

import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.building.Building;
import com.boydti.discord.apiv1.enums.city.building.PowerBuilding;

import java.util.Collections;
import java.util.Map;

public class APowerBuilding extends ABuilding implements PowerBuilding {
    private final Map<ResourceType, Double> input;
    private final double[] inputArr;
    private final int infra;
    private final int infraLevels;
    private final ResourceType resource;
    private final double resourceValue;

    public APowerBuilding(Building parent, ResourceType input, double inputAmt, int infraLevels, int infraPowered) {
        super(parent);
        if (input == null) {
            this.input = Collections.emptyMap();
        } else {
            this.input = Map.of(input, inputAmt);
        }
        this.inputArr = PnwUtil.resourcesToArray(this.input);
        this.resource = input;
        this.infraLevels = infraLevels;
        this.infra = infraPowered;
        this.resourceValue = resource != null ? PnwUtil.convertedTotal(resource, 1) : 0;
    }

    @Override
    public Map<ResourceType, Double> input(int infra) {
        double levels = (infra + infraLevels - 1) / infraLevels;
        return PnwUtil.multiply(input, levels);
    }

    @Override
    public int infraBase() {
        return infraLevels;
    }

    @Override
    public int infraMax() {
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
        return -(amt * resourceValue);
    }

    @Override
    public double[] consumption(int infra, double[] profitBuffer) {
        if (resource != null) {
            int amt = (Math.min(infra, this.infra) + infraLevels - 1) / infraLevels;
            profitBuffer[resource.ordinal()] -= amt * inputArr[resource.ordinal()];
        }
        return profitBuffer;
    }
}
