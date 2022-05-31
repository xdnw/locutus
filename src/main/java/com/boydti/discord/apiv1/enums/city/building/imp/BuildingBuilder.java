package com.boydti.discord.apiv1.enums.city.building.imp;

import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.building.Building;

import java.util.EnumMap;
import java.util.Map;

public class BuildingBuilder {
    private final String name;
    private int cap = Integer.MAX_VALUE;
    private int pollution = 0;
    private Map<ResourceType, Double> cost = new EnumMap<>(ResourceType.class);
    private Map<ResourceType, Double> upkeep = new EnumMap<>(ResourceType.class);

    public BuildingBuilder(String name) {
        this.name = name;
    }

    public BuildingBuilder cap(int cap) {
        this.cap = cap;
        return this;
    }

    public BuildingBuilder pollution(int pollution) {
        this.pollution = pollution;
        return this;
    }

    public BuildingBuilder cost(ResourceType resource, double amount) {
        this.cost.put(resource, amount);
        return this;
    }

    public BuildingBuilder upkeep(ResourceType resource, double amount) {
        this.upkeep.put(resource, amount);
        return this;
    }

    public Building build() {
        return new ABuilding(name, cap, pollution, cost, upkeep);
    }

    public ServiceBuilding buildService() {
        return new ServiceBuilding(new ABuilding(name, cap, pollution, cost, upkeep));
    }
}
