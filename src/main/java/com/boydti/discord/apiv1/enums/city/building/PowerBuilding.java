package com.boydti.discord.apiv1.enums.city.building;

import com.boydti.discord.apiv1.enums.ResourceType;

import java.util.Map;

public interface PowerBuilding extends Building {
    Map<ResourceType, Double> input(int infra);

    int infraBase();

    int infraMax();

    double[] consumption(int infra, double[] profitBuffer);

    double consumptionConverted(int infra);
}
