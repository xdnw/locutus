package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.ServiceBuilding;

public class AServiceBuilding extends ABuilding implements ServiceBuilding {
    public AServiceBuilding(BuildingBuilder parent) {
        super(parent);
    }
}
