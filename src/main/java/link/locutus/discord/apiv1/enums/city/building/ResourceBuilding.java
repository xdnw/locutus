package link.locutus.discord.apiv1.enums.city.building;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;

public interface ResourceBuilding extends Building {
    ResourceType resource();

    default boolean canBuild(Continent continent) {
        for (ResourceType type : continent.getResourceArray()) {
            if (type == resource()) {
                return true;
            }
        }
        return false;
    }

    ResourceBuilding continents(Continent... continents);

    int baseProduction();
}
