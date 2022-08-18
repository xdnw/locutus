package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;

import java.util.function.Function;

public class FarmBuilding extends AResourceBuilding {
    public FarmBuilding(BuildingBuilder parent) {
        super(parent, ResourceType.FOOD.getBaseInput(), ResourceType.FOOD.getBoostFactor(), ResourceType.FOOD, ResourceType.FOOD.getInputs());
    }
}
