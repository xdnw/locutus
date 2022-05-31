package com.boydti.discord.apiv1.enums.city.project;

import com.boydti.discord.util.StringMan;
import com.boydti.discord.apiv1.domains.Nation;
import com.boydti.discord.apiv1.enums.ResourceType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.boydti.discord.apiv1.enums.ResourceType.*;

public class Projects {
    public static final Project ADVANCED_URBAN_PLANNING = new Builder("advanced_urban_planning")
            .cost(URANIUM, 10000)
            .cost(ALUMINUM, 40000)
            .cost(STEEL, 20000)
            .cost(MUNITIONS, 20000)
            .cost(FOOD, 2500000)
            .get(nation -> Integer.parseInt(nation.getAdv_city_planning()))
            .build();

    public static final Project ARMS_STOCKPILE = new Builder("arms_stockpile")
            .image("armsstockpile.png")
            .cost(ALUMINUM, 125)
            .cost(STEEL, 125)
            .cost(MONEY, 4000000)
            .get(nation -> Integer.parseInt(nation.getArmsstockpile()))
            .build();

    public static final Project BAUXITEWORKS = new Builder("bauxite_works")
            .image("bauxiteworks.png")
            .cost(STEEL, 750)
            .cost(GASOLINE, 1500)
            .cost(MONEY, 5000000)
            .output(ALUMINUM)
            .get(nation -> Integer.parseInt(nation.getBauxiteworks()))
            .build();

    public static final Project CENTER_FOR_CIVIL_ENGINEERING = new Builder("center_for_civil_engineering")
            .image("cfce.png")
            .cost(OIL, 1000)
            .cost(IRON, 1000)
            .cost(BAUXITE, 1000)
            .cost(MONEY, 3000000)
            .get(nation -> Integer.parseInt(nation.getCenciveng()))
            .build();

    public static final Project URBAN_PLANNING = new Builder("urban_planning")
            .cost(COAL, 10000)
            .cost(OIL, 10000)
            .cost(ALUMINUM, 20000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(FOOD, 1000000)
            .get(nation -> Integer.parseInt(nation.getCity_planning()))
            .build();

    public static final Project EMERGENCY_GASOLINE_RESERVE = new Builder("emergency_gasoline_reserve")
            .image("emergencygasolinereserve.png")
            .cost(ALUMINUM, 125)
            .cost(STEEL, 125)
            .cost(MONEY, 4000000)
            .output(GASOLINE)
            .get(nation -> Integer.parseInt(nation.getEmgasreserve()))
            .build();

    public static final Project INTELLIGENCE_AGENCY = new Builder("central_intelligence_agency")
            .image("cia.png")
            .cost(STEEL, 500)
            .cost(GASOLINE, 500)
            .cost(MONEY, 5000000)
            .get(nation -> Integer.parseInt(nation.getIntagncy()))
            .build();

    public static final Project INTERNATIONAL_TRADE_CENTER = new Builder("international_trade_center")
            .image("internationaltradecenter.png")
            .cost(ALUMINUM, 2500)
            .cost(STEEL, 2500)
            .cost(GASOLINE, 5000)
            .cost(MONEY, 45000000)
            .output(MONEY)
            .get(nation -> Integer.parseInt(nation.getInttradecenter()))
            .build();

    public static final Project IRON_DOME = new Builder("iron_dome")
            .image("irondome.png")
            .cost(ALUMINUM, 500)
            .cost(STEEL, 1250)
            .cost(GASOLINE, 500)
            .cost(MONEY, 6000000)
            .get(nation -> Integer.parseInt(nation.getIrondome()))
            .build();

    public static final Project IRON_WORKS = new Builder("iron_works")
            .image("ironworks.png")
            .cost(ALUMINUM, 750)
            .cost(GASOLINE, 1500)
            .cost(MONEY, 5000000)
            .output(STEEL)
            .get(nation -> Integer.parseInt(nation.getIronworks()))
            .build();

    public static final Project MASS_IRRIGATION = new Builder("mass_irrigation")
            .image("massirrigation.png")
            .cost(ALUMINUM, 500)
            .cost(STEEL, 500)
            .cost(MONEY, 3000000)
            .output(FOOD)
            .get(nation -> Integer.parseInt(nation.getMassirrigation()))
            .build();

    public static final Project MISSILE_LAUNCH_PAD = new Builder("missile_launch_pad")
            .image("missilelaunchpad.png")
            .cost(STEEL, 1000)
            .cost(GASOLINE, 350)
            .cost(MONEY, 8000000)
            .get(nation -> Integer.parseInt(nation.getMissilelpad()))
            .build();

    public static final Project MOON_LANDING = new Builder("moon_landing")
            .image("moon_landing.png")
            .cost(OIL, 5000)
            .cost(MUNITIONS, 5000)
            .cost(GASOLINE, 5000)
            .cost(STEEL, 5000)
            .cost(ALUMINUM, 5000)
            .cost(URANIUM, 10000)
            .cost(MONEY, 50000000)
            .get(nation -> Integer.parseInt(nation.getMoon_landing()))
            .build();

    public static final Project NUCLEAR_RESEARCH_FACILITY = new Builder("nuclear_research_facility")
            .image("nrf.png")
            .cost(STEEL, 5000)
            .cost(GASOLINE, 7500)
            .cost(MONEY, 50000000)
            .get(nation -> Integer.parseInt(nation.getNuclearresfac()))
            .build();

    public static final Project PROPAGANDA_BUREAU = new Builder("propaganda_bureau")
            .image("pb.png")
            .cost(ALUMINUM, 1500)
            .cost(MONEY, 15000000)
            .get(nation -> Integer.parseInt(nation.getPropbureau()))
            .build();

    public static final Project SPACE_PROGRAM = new Builder("space_program")
            .image("space_program.png")
            .cost(URANIUM, 20000)
            .cost(OIL, 20000)
            .cost(IRON, 10000)
            .cost(GASOLINE, 5000)
            .cost(STEEL, 1000)
            .cost(ALUMINUM, 1000)
            .cost(MONEY, 40000000)
            .get(nation -> Integer.parseInt(nation.getSpace_program()))
            .build();

    public static final Project SPY_SATELLITE = new Builder("spy_satellite")
            .image("spy_satellite.png")
            .cost(OIL, 10000)
            .cost(IRON, 10000)
            .cost(LEAD, 10000)
            .cost(BAUXITE, 10000)
            .cost(URANIUM, 10000)
            .cost(MONEY, 20000000)
            .get(nation -> Integer.parseInt(nation.getSpy_satellite()))
            .build();

    public static final Project URANIUM_ENRICHMENT_PROGRAM = new Builder("uranium_enrichment_program")
            .image("uap.png")
            .cost(ALUMINUM, 1000)
            .cost(GASOLINE, 1000)
            .cost(URANIUM, 500)
            .cost(MONEY, 21000000)
            .output(URANIUM)
            .get(nation -> Integer.parseInt(nation.getUraniumenrich()))
            .build();

    public static final Project VITAL_DEFENSE_SYSTEM = new Builder("vital_defense_system")
            .image("vds.png")
            .cost(ALUMINUM, 3000)
            .cost(STEEL, 6500)
            .cost(GASOLINE, 5000)
            .cost(MONEY, 40000000)
            .get(nation -> Integer.parseInt(nation.getVitaldefsys()))
            .build();

    public static final Project RECYCLING_INITIATIVE = new Builder("recycling_initiative")
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .get(nation -> 0)
            .build();

    public static final Project PIRATE_ECONOMY = new Builder("pirate_economy")
            .cost(ALUMINUM, 10000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(STEEL, 10000)
            .cost(MONEY, 40000000)
            .get(nation -> 0)
            .build();

    public static final Project GREEN_TECHNOLOGIES = new Builder("green_tech")
            .cost(ALUMINUM, 3000)
            .cost(STEEL, 6500)
            .cost(GASOLINE, 5000)
            .cost(MONEY, 40000000)
            .get(nation -> 0)
            .build();

    public static final Project TELECOMMUNICATIONS_SATELLITE = new Builder("telecommunications_satellite")
            .cost(URANIUM, 10000)
            .cost(IRON, 10000)
            .cost(OIL, 10000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 300000000)
            .get(nation -> Integer.parseInt(nation.getTelecommunications_satellite()))
            .build();

    public static final Project ADVANCED_ENGINEERING_CORPS = new Builder("advanced_engineering_corps")
            .image("advanced_engineering_corps.jpg")
            .cost(URANIUM, 1000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(MONEY, 50000000)
            .get(nation -> 0)
            .build();

    public static final Project ARABLE_LAND_AGENCY = new Builder("arable_land_agency")
            .cost(COAL, 1500)
            .cost(LEAD, 1500)
            .cost(MONEY, 3000000)
            .get(nation -> 0)
            .build();

    public static final Project CLINICAL_RESEARCH_CENTER = new Builder("clinical_research_center")
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .get(nation -> 0)
            .build();

    public static final Project SPECIALIZED_POLICE_TRAINING_PROGRAM = new Builder("specialized_police_training_program")
            .image("specialized_police_training_program.jpg")
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .get(nation -> 0)
            .build();

    public static final Project RESEARCH_AND_DEVELOPMENT_CENTER = new Builder("research_and_development_center")
            .image("research_and_development_center.jpg")
            .cost(URANIUM, 1000)
            .cost(ALUMINUM, 5000)
            .cost(FOOD, 100000)
            .cost(MONEY, 50000000)
            .get(nation -> 0)
            .build();

    public static final Project RESOURCE_PRODUCTION_CENTER = new Builder("resource_production_center")
            .image("resource_production_center.jpg")
            .cost(FOOD, 1000)
            .cost(MONEY, 500000)
            .get(nation -> 0)
            .build();

    public static final Project GOVERNMENT_SUPPORT_AGENCY = new Builder("government_support_agency")
            .image("government_support_agency.jpg")
            .cost(FOOD, 200000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 20000000)
            .get(nation -> 0)
            .build();


    // Recycling Initiative

    public static class Builder {
        private String apiName,imageName;
        private Map<ResourceType, Double> resources = new EnumMap<>(ResourceType.class);
        private Function<Nation, Integer> get;
        private ResourceType output;

        public Builder(String apiName) {
            this.apiName = apiName;
            this.imageName = apiName;
        }

        public Builder image(String imageName) {
            this.imageName = imageName;
            return this;
        }

        public Builder cost(ResourceType type, double amt) {
            resources.put(type, amt);
            return this;
        }

        public Builder get(Function<Nation, Integer> get) {
            this.get = get;
            return this;
        }

        public Builder output(ResourceType  output) {
            this.output = output;
            return this;
        }

        public Project build() {
            return new AProject(apiName, imageName, resources, get, output);
        }
    }

    public static final Project[] values;
    public static final Map<String, Project> PROJECTS_MAP = new HashMap<>();
    static {
        try {
            List<Project> projList = new ArrayList<>();
            int i = 0;
            for (Field field : Projects.class.getDeclaredFields()) {
                Object value = field.get(null);
                if (value != null && value instanceof Project) {
                    Project proj = (Project) value;
                    projList.add(proj);
                    ((AProject) proj).setName(field.getName(), i++);
                    PROJECTS_MAP.put(field.getName(), proj);
                }
            }
            values = projList.toArray(new Project[0]);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Project get(String name) {
        Project result = PROJECTS_MAP.get(name.replace(" ", "_").toUpperCase());
        if (result != null) return result;
        for (Map.Entry<String, Project> entry : PROJECTS_MAP.entrySet()) {
            if (StringMan.abbreviate(entry.getKey(), '_').equalsIgnoreCase(name)) {
                return entry.getValue();
            }
            if (entry.getKey().replace("_", "").equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static Predicate<Project> hasProjectCached(Predicate<Project> hasProject) {
        Map<Project, Boolean> cached = new HashMap<>();
        for (Project project : Projects.values) {
            cached.put(project, hasProject.test(project));
        }
        return cached::get;
    }
}
