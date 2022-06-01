package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;

import java.util.function.Function;

public class FarmBuilding extends AResourceBuilding {
    public FarmBuilding(Building parent, int baseInput, double boostFactor, Function<Nation, Boolean> hasWorks, ResourceType output, ResourceType... inputs) {
        super(parent, baseInput, boostFactor, hasWorks, output, inputs);
    }
}
