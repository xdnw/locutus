package link.locutus.discord.apiv1.enums.city.building;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.Map;

public interface PowerBuilding extends Building {
    Map<ResourceType, Double> input(int infra);

    int getInfraBase();

    int getInfraMax();

    double[] consumption(int infra, double[] profitBuffer, int turns);

    double consumptionConverted(int infra);
    ResourceType getPowerResource();


}
