package link.locutus.discord.apiv1.enums.city.building;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;

public interface ResourceBuilding extends Building {
    ResourceType getResourceProduced();

    default boolean canBuild(Continent continent) {
        for (ResourceType type : continent.getResourceArray()) {
            if (type == getResourceProduced()) {
                return true;
            }
        }
        return false;
    }

    ResourceBuilding continents(Continent... continents);

    int getBaseProduction();

    int getBaseInput();
}
