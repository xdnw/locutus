package com.boydti.discord.apiv1.enums.city.building.imp;

import com.boydti.discord.apiv1.domains.Nation;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.building.Building;

import java.util.function.Function;

public class FarmBuilding extends AResourceBuilding {
    public FarmBuilding(Building parent, int baseInput, double boostFactor, Function<Nation, Boolean> hasWorks, ResourceType output, ResourceType... inputs) {
        super(parent, baseInput, boostFactor, hasWorks, output, inputs);
    }
}
