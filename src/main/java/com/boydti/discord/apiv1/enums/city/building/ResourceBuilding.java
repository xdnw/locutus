package com.boydti.discord.apiv1.enums.city.building;

import com.boydti.discord.apiv1.enums.Continent;
import com.boydti.discord.apiv1.enums.ResourceType;

public interface ResourceBuilding extends Building {
    ResourceType resource();

    default boolean canBuild(Continent continent) {
        for (ResourceType type : continent.resources) {
            if (type == resource()) {
                return true;
            }
        }
        return false;
    }

    ResourceBuilding continents(Continent... continents);

    int baseProduction();
}
