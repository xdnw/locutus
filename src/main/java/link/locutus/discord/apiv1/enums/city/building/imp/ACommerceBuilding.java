package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.enums.BuildingType;
import link.locutus.discord.apiv1.enums.city.building.CommerceBuilding;

public class ACommerceBuilding extends ABuilding implements CommerceBuilding {
    private final int commerce;

    public ACommerceBuilding(BuildingBuilder parent, int commerce) {
        super(parent);
        this.commerce = commerce;
    }

    @Override
    public int getCommerce() {
        return commerce;
    }

    @Override
    public BuildingType getType() {
        return BuildingType.COMMERCE;
    }
}
