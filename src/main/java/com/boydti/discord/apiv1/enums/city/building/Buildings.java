package com.boydti.discord.apiv1.enums.city.building;

import static com.boydti.discord.apiv1.enums.Continent.*;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.building.imp.ABuilding;
import com.boydti.discord.apiv1.enums.city.building.imp.BuildingBuilder;
import com.boydti.discord.apiv1.enums.city.building.imp.ServiceBuilding;
import com.boydti.discord.apiv1.enums.city.project.Projects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.boydti.discord.apiv1.enums.ResourceType.ALUMINUM;
import static com.boydti.discord.apiv1.enums.ResourceType.BAUXITE;
import static com.boydti.discord.apiv1.enums.ResourceType.COAL;
import static com.boydti.discord.apiv1.enums.ResourceType.FOOD;
import static com.boydti.discord.apiv1.enums.ResourceType.GASOLINE;
import static com.boydti.discord.apiv1.enums.ResourceType.IRON;
import static com.boydti.discord.apiv1.enums.ResourceType.LEAD;
import static com.boydti.discord.apiv1.enums.ResourceType.MONEY;
import static com.boydti.discord.apiv1.enums.ResourceType.MUNITIONS;
import static com.boydti.discord.apiv1.enums.ResourceType.OIL;
import static com.boydti.discord.apiv1.enums.ResourceType.STEEL;
import static com.boydti.discord.apiv1.enums.ResourceType.URANIUM;

public class Buildings {
    public final static PowerBuilding COAL_POWER = new BuildingBuilder("impCoalpower").cost(MONEY, 5000).pollution(8).upkeep(MONEY, 1200).build().power(COAL, 1.2d, 100, 500);
    public final static PowerBuilding OIL_POWER = new BuildingBuilder("impOilpower").cost(MONEY, 7000).pollution(6).upkeep(MONEY, 1800).build().power(OIL, 1.2d, 100, 500);
    public final static PowerBuilding NUCLEAR_POWER = new BuildingBuilder("impNuclearpower").cost(MONEY, 500000).upkeep(MONEY, 10500).cost(STEEL, 100).build().power(URANIUM, 1.2d, 1000, 2000);
    public final static PowerBuilding WIND_POWER = new BuildingBuilder("impWindpower").cost(MONEY, 30000).cost(ALUMINUM, 25).upkeep(MONEY, 500).build().power(null, 0, 250, 250);
    public final static ResourceBuilding COAL_MINE = new BuildingBuilder("impCoalmine").cost(MONEY, 1000).pollution(12).upkeep(MONEY, 400).cap(10).build().resource(COAL).continents(NORTH_AMERICA, EUROPE, AUSTRALIA, ANTARCTICA);
    public final static ResourceBuilding OIL_WELL = new BuildingBuilder("impOilwell").cost(MONEY, 1500).pollution(12).upkeep(MONEY, 600).cap(10).build().resource(OIL).continents(SOUTH_AMERICA, AFRICA, ASIA, ANTARCTICA);
    public final static ResourceBuilding BAUXITE_MINE = new BuildingBuilder("impBauxitemine").cost(MONEY, 9500).pollution(12).upkeep(MONEY, 1600).cap(10).build().resource(BAUXITE).continents(SOUTH_AMERICA, AFRICA, AUSTRALIA);
    public final static ResourceBuilding IRON_MINE = new BuildingBuilder("impIronmine").cost(MONEY, 9500).pollution(12).upkeep(MONEY, 1600).cap(10).build().resource(IRON).continents(NORTH_AMERICA, EUROPE, ASIA);
    public final static ResourceBuilding LEAD_MINE = new BuildingBuilder("impLeadmine").cost(MONEY, 7500).pollution(12).upkeep(MONEY, 1500).cap(10).build().resource(LEAD).continents(SOUTH_AMERICA, EUROPE, AUSTRALIA);
    public final static ResourceBuilding URANIUM_MINE = new BuildingBuilder("impUramine").cost(MONEY, 25000).pollution(20).upkeep(MONEY, 5000).cap(5).build().resource(URANIUM, n -> Integer.parseInt(n.getUraniumenrich()) > 0).continents(NORTH_AMERICA, AFRICA, ASIA, ANTARCTICA);
    public final static ResourceBuilding FARM = new BuildingBuilder("impFarm").cost(MONEY, 1000).pollution(2).upkeep(MONEY, 300).cap(20).build().resource(FOOD, n -> Integer.parseInt(n.getMassirrigation()) > 0);

    public final static ResourceBuilding GAS_REFINERY = new BuildingBuilder("impGasrefinery").cost(MONEY, 45000).pollution(32).upkeep(MONEY, 4000).cap(5).build().resource(GASOLINE, n -> Integer.parseInt(n.getEmgasreserve()) > 0);
    public final static ResourceBuilding STEEL_MILL = new BuildingBuilder("impSteelmill").cost(MONEY, 45000).pollution(40).upkeep(MONEY, 4000).cap(5).build().resource(STEEL, n -> Integer.parseInt(n.getIronworks()) > 0);
    public final static ResourceBuilding ALUMINUM_REFINERY = new BuildingBuilder("impAluminumrefinery").cost(MONEY, 30000).pollution(40).upkeep(MONEY, 2500).cap(5).build().resource(ALUMINUM, n -> Integer.parseInt(n.getBauxiteworks()) > 0);
    public final static ResourceBuilding MUNITIONS_FACTORY = new BuildingBuilder("impMunitionsfactory").cost(MONEY, 35000).pollution(32).upkeep(MONEY, 3500).cap(5).build().resource(MUNITIONS,n -> Integer.parseInt(n.getArmsstockpile()) > 0);

    public final static CommerceBuilding SUBWAY = new BuildingBuilder("impSubway").cost(MONEY, 250000).cost(STEEL, 50).cost(ALUMINUM, 25).pollution(-45).upkeep(MONEY, 3250).cap(1).build().commerce(8);
    public final static CommerceBuilding MALL = new BuildingBuilder("impMall").cost(MONEY, 45000).cost(ALUMINUM, 50).cost(STEEL, 20).pollution(2).upkeep(MONEY, 5400).cap(4).build().commerce(9);
    public final static CommerceBuilding STADIUM = new BuildingBuilder("impStadium").cost(MONEY, 100000).cost(STEEL, 40).cost(ALUMINUM, 50).pollution(5).upkeep(MONEY, 12150).cap(3).build().commerce(12);
    public final static CommerceBuilding BANK = new BuildingBuilder("impBank").cost(MONEY, 15000).cost(ALUMINUM, 10).cost(STEEL, 5).upkeep(MONEY, 1800).cap(5).build().commerce(5);
    public final static CommerceBuilding SUPERMARKET = new BuildingBuilder("impSupermarket").cost(MONEY, 5000).upkeep(MONEY, 600).cap(6).build().commerce(3);

    public final static ServiceBuilding POLICE_STATION = new BuildingBuilder("impPolicestation").cost(MONEY, 75000).cost(STEEL, 20).pollution(1).upkeep(MONEY, 750).cap(5).buildService();
    public final static ServiceBuilding HOSPITAL = new BuildingBuilder("impHospital").cost(MONEY, 100000).cost(ALUMINUM, 25).pollution(4).upkeep(MONEY, 1000).cap(5).buildService();
    public final static ServiceBuilding RECYCLING_CENTER = new ServiceBuilding(new BuildingBuilder("impRecyclingcenter").cost(MONEY, 125000).pollution(-70).upkeep(MONEY, 2500).cap(3).buildService()) {
        @Override
        public int pollution(Predicate hasProject) {
            int basePollution = super.pollution(hasProject);
            if (hasProject.test(Projects.RECYCLING_INITIATIVE)) basePollution -= 5;
            return basePollution;
        }

        @Override
        public int cap(Predicate hasProject) {
            int baseCap = super.cap(hasProject);
            if (hasProject.test(Projects.RECYCLING_INITIATIVE)) baseCap++;
            return baseCap;
        }
    };

    public final static MilitaryBuilding BARRACKS = new BuildingBuilder("impBarracks").cost(MONEY, 3000).cap(5).build().unit(MilitaryUnit.SOLDIER, 3000, 1000, 6.67);
    public final static MilitaryBuilding FACTORY = new BuildingBuilder("impFactory").cost(MONEY, 15000).cost(ALUMINUM, 5).cap(5).build().unit(MilitaryUnit.TANK, 250, 50, 66.67);
    public final static MilitaryBuilding HANGAR = new BuildingBuilder("impHangars").cost(MONEY, 100000).cost(STEEL, 10).cap(5).build().unit(MilitaryUnit.AIRCRAFT, 15, 3, 1000);
    public final static MilitaryBuilding DRYDOCK = new BuildingBuilder("impDrydock").cost(MONEY, 250000).cost(ALUMINUM, 20).cap(3).build().unit(MilitaryUnit.SHIP, 5, 1, 10000);

    public final static Building[] BUILDINGS;
    public final static Building[] POLLUTION_BUILDINGS;
    public final static Building[] COMMERCE_BUILDINGS = {SUBWAY, MALL, STADIUM, BANK, SUPERMARKET};

    public final static Map<ResourceType, Building> RESOURCE_BUILDING = new ConcurrentHashMap<>();
    static {
        RESOURCE_BUILDING.put(COAL, COAL_MINE);
        RESOURCE_BUILDING.put(OIL, OIL_WELL);
        RESOURCE_BUILDING.put(BAUXITE, BAUXITE_MINE);
        RESOURCE_BUILDING.put(IRON, IRON_MINE);
        RESOURCE_BUILDING.put(LEAD, LEAD_MINE);
        RESOURCE_BUILDING.put(URANIUM, URANIUM_MINE);
        RESOURCE_BUILDING.put(FOOD, FARM);
        RESOURCE_BUILDING.put(GASOLINE, GAS_REFINERY);
        RESOURCE_BUILDING.put(STEEL, STEEL_MILL);
        RESOURCE_BUILDING.put(ALUMINUM, ALUMINUM_REFINERY);
        RESOURCE_BUILDING.put(MUNITIONS, MUNITIONS_FACTORY);
    }
    public final static int[] HASHCODES;
    private final static Map<String, Building> BUILDINGS_MAP = new HashMap<>();

    static {
        try {
            List<Building> buildingsList = new ArrayList<>();
            for (Field field : Buildings.class.getDeclaredFields()) {
                Object value = field.get(null);
                if (value != null && value instanceof Building) {
                    Building building = (Building) value;
                    ((ABuilding) building).setOrdinal(buildingsList.size());
                    buildingsList.add(building);
                    BUILDINGS_MAP.put(building.name(), building);
                }
            }
            BUILDINGS = buildingsList.toArray(new Building[0]);

            HASHCODES = new int[BUILDINGS.length];
            int total = 0;
            for (int i = 0; i < BUILDINGS.length; i++) {
                Building building = BUILDINGS[i];
                HASHCODES[i] = total;
                total += building.cap(f -> true);
            }

            List<Building> pollutionBuildings = new ArrayList<>();
            for (Building building : buildingsList) {
                int pollution = building.pollution(f -> false);
                if (pollution != 0) {
                    pollutionBuildings.add(building);
                }
            }
            POLLUTION_BUILDINGS = pollutionBuildings.toArray(new Building[0]);


        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Building getByType(ResourceType type) {
        switch (type) {
            case FOOD:
                return FARM;
            case COAL:
                return COAL_MINE;
            case OIL:
                return OIL_WELL;
            case URANIUM:
                return URANIUM_MINE;
            case LEAD:
                return LEAD_MINE;
            case IRON:
                return IRON_MINE;
            case BAUXITE:
                return BAUXITE_MINE;
            case GASOLINE:
                return GAS_REFINERY;
            case MUNITIONS:
                return MUNITIONS_FACTORY;
            case STEEL:
                return STEEL_MILL;
            case ALUMINUM:
                return ALUMINUM_REFINERY;
        }
        return null;
    }

    public static int size() {
        return BUILDINGS.length;
    }

    public static Building get(String jsonId) {
        return BUILDINGS_MAP.get(jsonId);
    }

    public static Building get(int ordinal) {
        return BUILDINGS[ordinal];
    }

    public static Building[] values() {
        return BUILDINGS;
    }
}
