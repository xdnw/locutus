package com.boydti.discord.apiv1.enums.city.building.imp;

import com.boydti.discord.apiv1.enums.city.building.Building;
import com.boydti.discord.apiv1.enums.city.building.CommerceBuilding;

public class ACommerceBuilding extends ABuilding implements CommerceBuilding {
    private final int commerce;

    public ACommerceBuilding(Building parent, int commerce) {
        super(parent);
        this.commerce = commerce;
    }

    @Override
    public int commerce() {
        return commerce;
    }
}
