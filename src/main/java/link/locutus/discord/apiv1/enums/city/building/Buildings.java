package link.locutus.discord.apiv1.enums.city.building;

import static link.locutus.discord.apiv1.enums.Continent.*;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.imp.*;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static link.locutus.discord.apiv1.enums.ResourceType.ALUMINUM;
import static link.locutus.discord.apiv1.enums.ResourceType.BAUXITE;
import static link.locutus.discord.apiv1.enums.ResourceType.COAL;
import static link.locutus.discord.apiv1.enums.ResourceType.FOOD;
import static link.locutus.discord.apiv1.enums.ResourceType.GASOLINE;
import static link.locutus.discord.apiv1.enums.ResourceType.IRON;
import static link.locutus.discord.apiv1.enums.ResourceType.LEAD;
import static link.locutus.discord.apiv1.enums.ResourceType.MONEY;
import static link.locutus.discord.apiv1.enums.ResourceType.MUNITIONS;
import static link.locutus.discord.apiv1.enums.ResourceType.OIL;
import static link.locutus.discord.apiv1.enums.ResourceType.STEEL;
import static link.locutus.discord.apiv1.enums.ResourceType.URANIUM;

public class Buildings {
    public final static PowerBuilding COAL_POWER = new BuildingBuilder("impCoalpower").cost(MONEY, 5000).pollution(8).upkeep(MONEY, 1200).power(COAL, 1.2d, 100, 500);
    public final static PowerBuilding OIL_POWER = new BuildingBuilder("impOilpower").cost(MONEY, 7000).pollution(6).upkeep(MONEY, 1800).power(OIL, 1.2d, 100, 500);
    public final static PowerBuilding NUCLEAR_POWER = new BuildingBuilder("impNuclearpower").cost(MONEY, 500000).upkeep(MONEY, 10500).cost(STEEL, 100).power(URANIUM, 1.2d, 1000, 2000);
    public final static PowerBuilding WIND_POWER = new BuildingBuilder("impWindpower").cost(MONEY, 30000).cost(ALUMINUM, 25).upkeep(MONEY, 500).power(null, 0, 250, 250);
    public final static ResourceBuilding COAL_MINE = new BuildingBuilder("impCoalmine").cost(MONEY, 1000).pollution(12).upkeep(MONEY, 400).cap(10).resource(COAL).continents(NORTH_AMERICA, EUROPE, AUSTRALIA, ANTARCTICA);
    public final static ResourceBuilding OIL_WELL = new BuildingBuilder("impOilwell").cost(MONEY, 1500).pollution(12).upkeep(MONEY, 600).cap(10).resource(OIL).continents(SOUTH_AMERICA, AFRICA, ASIA, ANTARCTICA);
    public final static ResourceBuilding BAUXITE_MINE = new BuildingBuilder("impBauxitemine").cost(MONEY, 9500).pollution(12).upkeep(MONEY, 1600).cap(10).resource(BAUXITE).continents(SOUTH_AMERICA, AFRICA, AUSTRALIA);
    public final static ResourceBuilding IRON_MINE = new BuildingBuilder("impIronmine").cost(MONEY, 9500).pollution(12).upkeep(MONEY, 1600).cap(10).resource(IRON).continents(NORTH_AMERICA, EUROPE, ASIA);
    public final static ResourceBuilding LEAD_MINE = new BuildingBuilder("impLeadmine").cost(MONEY, 7500).pollution(12).upkeep(MONEY, 1500).cap(10).resource(LEAD).continents(SOUTH_AMERICA, EUROPE, AUSTRALIA);
    public final static ResourceBuilding URANIUM_MINE = new BuildingBuilder("impUramine").cost(MONEY, 25000).pollution(20).upkeep(MONEY, 5000).cap(5).resource(URANIUM).continents(NORTH_AMERICA, AFRICA, ASIA, ANTARCTICA);
    public final static ResourceBuilding FARM = new FarmBuilding(new BuildingBuilder("impFarm").cost(MONEY, 1000).pollution(2).upkeep(MONEY, 300).cap(20)) {
        @Override
        public int pollution(Predicate hasProject) {
            int result = super.pollution(hasProject);
            if ((hasProject.test(Projects.GREEN_TECHNOLOGIES))) return result / 2;
            return result;
        }
    };

    public final static ResourceBuilding GAS_REFINERY = new AResourceBuilding(new BuildingBuilder("impGasrefinery").cost(MONEY, 45000).pollution(32).upkeep(MONEY, 4000).cap(5), GASOLINE) {
        @Override
        public int pollution(Predicate hasProject) {
            int result = super.pollution(hasProject);
            if ((hasProject.test(Projects.GREEN_TECHNOLOGIES))) result = (result * 3) / 4;
            return result;
        }
    };
    public final static ResourceBuilding STEEL_MILL = new AResourceBuilding(new BuildingBuilder("impSteelmill").cost(MONEY, 45000).pollution(40).upkeep(MONEY, 4000).cap(5), STEEL) {
        @Override
        public int pollution(Predicate hasProject) {
            int result = super.pollution(hasProject);
            if ((hasProject.test(Projects.GREEN_TECHNOLOGIES))) result = (result * 3) / 4;
            return result;
        }
    };
    public final static ResourceBuilding ALUMINUM_REFINERY = new AResourceBuilding(new BuildingBuilder("impAluminumrefinery").cost(MONEY, 30000).pollution(40).upkeep(MONEY, 2500).cap(5), ALUMINUM) {
        @Override
        public int pollution(Predicate hasProject) {
            int result = super.pollution(hasProject);
            if ((hasProject.test(Projects.GREEN_TECHNOLOGIES))) result = (result * 3) / 4;
            return result;
        }
    };
    public final static ResourceBuilding MUNITIONS_FACTORY = new AResourceBuilding(new BuildingBuilder("impMunitionsfactory").cost(MONEY, 35000).pollution(32).upkeep(MONEY, 3500).cap(5), MUNITIONS) {
        @Override
        public int pollution(Predicate hasProject) {
            int result = super.pollution(hasProject);
            if ((hasProject.test(Projects.GREEN_TECHNOLOGIES))) result = (result * 3) / 4;
            return result;
        }
    };

    public final static CommerceBuilding SUBWAY = new ACommerceBuilding(new BuildingBuilder("impSubway").cost(MONEY, 250000).cost(STEEL, 50).cost(ALUMINUM, 25).pollution(-45).upkeep(MONEY, 3250).cap(1), 8) {
        @Override
        public int pollution(Predicate hasProject) {
            return super.pollution(hasProject) + (hasProject.test(Projects.GREEN_TECHNOLOGIES) ? -25 : 0);
        }
    };
    public final static CommerceBuilding MALL = new BuildingBuilder("impMall").cost(MONEY, 45000).cost(ALUMINUM, 50).cost(STEEL, 20).pollution(2).upkeep(MONEY, 5400).cap(4).commerce(9);
    public final static CommerceBuilding STADIUM = new BuildingBuilder("impStadium").cost(MONEY, 100000).cost(STEEL, 40).cost(ALUMINUM, 50).pollution(5).upkeep(MONEY, 12150).cap(3).commerce(12);
    public final static CommerceBuilding BANK = new BuildingBuilder("impBank").cost(MONEY, 15000).cost(ALUMINUM, 10).cost(STEEL, 5).upkeep(MONEY, 1800).cap(5).commerce(5);
    public final static CommerceBuilding SUPERMARKET = new BuildingBuilder("impSupermarket").cost(MONEY, 5000).upkeep(MONEY, 600).cap(6).commerce(3);

    public final static ServiceBuilding POLICE_STATION = new AServiceBuilding(new BuildingBuilder("impPolicestation").cost(MONEY, 75000).cost(STEEL, 20).pollution(1).upkeep(MONEY, 750).cap(5)) {
        @Override
        public int cap(Predicate<Project> hasProject) {
            return super.cap(hasProject) + (hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 1 : 0);
        }
    };
    public final static ServiceBuilding HOSPITAL = new AServiceBuilding(new BuildingBuilder("impHospital").cost(MONEY, 100000).cost(ALUMINUM, 25).pollution(4).upkeep(MONEY, 1000).cap(5)) {
        @Override
        public int cap(Predicate hasProject) {
            int val = super.cap(hasProject);
            if (hasProject.test(Projects.CLINICAL_RESEARCH_CENTER)) val++;
            return val;
        }
    };
    public final static ServiceBuilding RECYCLING_CENTER = new AServiceBuilding(new BuildingBuilder("impRecyclingcenter").cost(MONEY, 125000).pollution(-70).upkeep(MONEY, 2500).cap(3)) {
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

    public final static MilitaryBuilding BARRACKS = new BuildingBuilder("impBarracks").cost(MONEY, 3000).cap(5).unit(MilitaryUnit.SOLDIER, 3000, 1000, 6.67);
    public final static MilitaryBuilding FACTORY = new BuildingBuilder("impFactory").cost(MONEY, 15000).cost(ALUMINUM, 5).cap(5).unit(MilitaryUnit.TANK, 250, 50, 66.67);
    public final static MilitaryBuilding HANGAR = new BuildingBuilder("impHangars").cost(MONEY, 100000).cost(STEEL, 10).cap(5).unit(MilitaryUnit.AIRCRAFT, 15, 3, 1000);
    public final static MilitaryBuilding DRYDOCK = new BuildingBuilder("impDrydock").cost(MONEY, 250000).cost(ALUMINUM, 20).cap(3).unit(MilitaryUnit.SHIP, 5, 1, 10000);

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
