package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public class Projects {
    public static final Project ADVANCED_URBAN_PLANNING = new Builder("advanced_urban_planning", 15)
            .cost(URANIUM, 10000)
            .cost(ALUMINUM, 40000)
            .cost(STEEL, 20000)
            .cost(MUNITIONS, 20000)
            .cost(FOOD, 2500000)
            .requiredCities(16)
            .build();

    public static final Project ARMS_STOCKPILE = new Builder("arms_stockpile", 2)
            .image("armsstockpile.png")
            .cost(ALUMINUM, 125)
            .cost(STEEL, 125)
            .cost(MONEY, 4000000)
            .build();

    public static final Project BAUXITEWORKS = new Builder("bauxite_works", 1)
            .image("bauxiteworks.png")
            .cost(STEEL, 750)
            .cost(GASOLINE, 1500)
            .cost(MONEY, 5000000)
            .output(ALUMINUM)
            .build();

    public static final Project CENTER_FOR_CIVIL_ENGINEERING = new Builder("center_for_civil_engineering", 11)
            .image("cfce.png")
            .cost(OIL, 1000)
            .cost(IRON, 1000)
            .cost(BAUXITE, 1000)
            .cost(MONEY, 3000000)
            .build();

    public static final Project URBAN_PLANNING = new Builder("urban_planning", 14)
            .cost(COAL, 10000)
            .cost(OIL, 10000)
            .cost(ALUMINUM, 20000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(FOOD, 1000000)
            .requiredCities(11)
            .build();

    public static final Project EMERGENCY_GASOLINE_RESERVE = new Builder("emergency_gasoline_reserve", 3)
            .image("emergencygasolinereserve.png")
            .cost(ALUMINUM, 125)
            .cost(STEEL, 125)
            .cost(MONEY, 4000000)
            .output(GASOLINE)
            .build();

    public static final Project INTELLIGENCE_AGENCY = new Builder("central_intelligence_agency", 10)
            .image("cia.png")
            .cost(STEEL, 500)
            .cost(GASOLINE, 500)
            .cost(MONEY, 5000000)
            .build();

    public static final Project INTERNATIONAL_TRADE_CENTER = new Builder("international_trade_center", 5)
            .image("internationaltradecenter.png")
            .cost(ALUMINUM, 2500)
            .cost(STEEL, 2500)
            .cost(GASOLINE, 5000)
            .cost(MONEY, 45000000)
            .output(MONEY)
            .build();

    public static final Project IRON_DOME = new Builder("iron_dome", 8)
            .image("irondome.png")
            .cost(ALUMINUM, 500)
            .cost(STEEL, 1250)
            .cost(GASOLINE, 500)
            .cost(MONEY, 6000000)
            .build();

    public static final Project IRON_WORKS = new Builder("iron_works", 0)
            .image("ironworks.png")
            .cost(ALUMINUM, 750)
            .cost(GASOLINE, 1500)
            .cost(MONEY, 5000000)
            .output(STEEL)
            .build();

    public static final Project MASS_IRRIGATION = new Builder("mass_irrigation", 4)
            .image("massirrigation.png")
            .cost(ALUMINUM, 500)
            .cost(STEEL, 500)
            .cost(MONEY, 3000000)
            .output(FOOD)
            .build();

    public static final Project MISSILE_LAUNCH_PAD = new Builder("missile_launch_pad", 6)
            .image("missilelaunchpad.png")
            .cost(STEEL, 1000)
            .cost(GASOLINE, 350)
            .cost(MONEY, 8000000)
            .build();

    public static final Project MOON_LANDING = new Builder("moon_landing", 18)
            .image("moon_landing.png")
            .cost(OIL, 5000)
            .cost(MUNITIONS, 5000)
            .cost(GASOLINE, 5000)
            .cost(STEEL, 5000)
            .cost(ALUMINUM, 5000)
            .cost(URANIUM, 10000)
            .cost(MONEY, 50000000)
            .requiredProjects(new Supplier<Project[]>() {
                @Override
                public Project[] get() {
                    return new Project[]{SPACE_PROGRAM};
                }
            })
            .build();

    public static final Project NUCLEAR_RESEARCH_FACILITY = new Builder("nuclear_research_facility", 7)
            .image("nrf.png")
            .cost(STEEL, 5000)
            .cost(GASOLINE, 7500)
            .cost(MONEY, 50000000)
            .build();

    public static final Project PROPAGANDA_BUREAU = new Builder("propaganda_bureau", 12)
            .image("pb.png")
            .cost(ALUMINUM, 1500)
            .cost(MONEY, 15000000)
            .build();

    public static final Project SPACE_PROGRAM = new Builder("space_program", 16)
            .image("space_program.png")
            .cost(URANIUM, 20000)
            .cost(OIL, 20000)
            .cost(IRON, 10000)
            .cost(GASOLINE, 5000)
            .cost(STEEL, 1000)
            .cost(ALUMINUM, 1000)
            .cost(MONEY, 40000000)
            .build();

    public static final Project SPY_SATELLITE = new Builder("spy_satellite", 17)
            .image("spy_satellite.png")
            .cost(OIL, 10000)
            .cost(IRON, 10000)
            .cost(LEAD, 10000)
            .cost(BAUXITE, 10000)
            .cost(URANIUM, 10000)
            .cost(MONEY, 20000000)
            .requiredProjects(() -> new Project[]{SPACE_PROGRAM})
            .build();

    public static final Project URANIUM_ENRICHMENT_PROGRAM = new Builder("uranium_enrichment_program", 13)
            .image("uap.png")
            .cost(ALUMINUM, 1000)
            .cost(GASOLINE, 1000)
            .cost(URANIUM, 500)
            .cost(MONEY, 21000000)
            .output(URANIUM)
            .build();

    public static final Project VITAL_DEFENSE_SYSTEM = new Builder("vital_defense_system", 9)
            .image("vds.png")
            .cost(ALUMINUM, 3000)
            .cost(STEEL, 6500)
            .cost(GASOLINE, 5000)
            .cost(MONEY, 40000000)
            .build();

    public static final Project RECYCLING_INITIATIVE = new Builder("recycling_initiative", 20)
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .requiredProjects(() -> new Project[]{CENTER_FOR_CIVIL_ENGINEERING})
            .build();

    public static final Project PIRATE_ECONOMY = new Builder("pirate_economy", 19)
            .cost(ALUMINUM, 10000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(STEEL, 10000)
            .cost(MONEY, 25_000_000)
            .otherRequirements(f -> f.getWars_won() + f.getWars_lost() >= 50)
            .build();

    public static final Project GREEN_TECHNOLOGIES = new Builder("green_tech", 22)
            .cost(MONEY, 100_000_000)
            .cost(ALUMINUM, 10_000)
            .cost(STEEL, 10_000)
            .cost(FOOD, 250_000)
            .cost(IRON, 10000)
            .requiredProjects(() -> new Project[]{URBAN_PLANNING, SPACE_PROGRAM})
            .build();

    public static final Project TELECOMMUNICATIONS_SATELLITE = new Builder("telecommunications_satellite", 21)
            .cost(URANIUM, 10000)
            .cost(IRON, 10000)
            .cost(OIL, 10000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 300000000)
            .requiredProjects(() -> new Project[]{INTERNATIONAL_TRADE_CENTER, URBAN_PLANNING, SPACE_PROGRAM})
            .build();

    public static final Project ADVANCED_ENGINEERING_CORPS = new Builder("advanced_engineering_corps", 26)
            .image("advanced_engineering_corps.jpg")
            .cost(URANIUM, 1000)
            .cost(MUNITIONS, 10000)
            .cost(GASOLINE, 10000)
            .cost(MONEY, 50000000)
            .requiredProjects(new Supplier<>() {
                @Override
                public Project[] get() {
                    return new Project[]{CENTER_FOR_CIVIL_ENGINEERING, ARABLE_LAND_AGENCY};
                }
            })
            .build();

    public static final Project ARABLE_LAND_AGENCY = new Builder("arable_land_agency", 23)
            .cost(COAL, 1500)
            .cost(LEAD, 1500)
            .cost(MONEY, 3000000)
            .build();

    public static final Project CLINICAL_RESEARCH_CENTER = new Builder("clinical_research_center", 24)
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .build();

    public static final Project SPECIALIZED_POLICE_TRAINING_PROGRAM = new Builder("specialized_police_training_program", 25)
            .image("specialized_police_training_program.jpg")
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .build();

    public static final Project RESEARCH_AND_DEVELOPMENT_CENTER = new Builder("research_and_development_center", 28)
            .image("research_and_development_center.jpg")
            .cost(URANIUM, 1000)
            .cost(ALUMINUM, 5000)
            .cost(FOOD, 100000)
            .cost(MONEY, 50000000)
            .build();

    public static final Project ACTIVITY_CENTER = new Builder("activity_center", 29)
            .image("resource_production_center.jpg")
            .cost(FOOD, 1000)
            .cost(MONEY, 500000)
            .maxCities(15)
            .build();

    public static final Project GOVERNMENT_SUPPORT_AGENCY = new Builder("government_support_agency", 27)
            .image("government_support_agency.jpg")
            .cost(FOOD, 200000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 20000000)
            .build();

    public static final Project METROPOLITAN_PLANNING = new Builder("metropolitan_planning", 30)
            .cost(ALUMINUM, 60000)
            .cost(STEEL, 40000)
            .cost(URANIUM, 30000)
            .cost(LEAD, 15000)
            .cost(IRON, 15000)
            .cost(BAUXITE, 15000)
            .cost(OIL, 10000)
            .cost(COAL, 10000)
            .requiredCities(21)
            .requiredProjects(() -> new Project[]{URBAN_PLANNING, ADVANCED_URBAN_PLANNING})
            .build();

    public static final Project MILITARY_SALVAGE = new Builder("military_salvage", 31)
            .cost(MONEY, 20000000)
            .cost(ALUMINUM, 5000)
            .cost(STEEL, 5000)
            .cost(GASOLINE, 5000)
            .build();

    public static final Project FALLOUT_SHELTER = new Builder("fallout_shelter", 32)
            .cost(MONEY, 25_000_000)
            .cost(FOOD, 100_000)
            .cost(LEAD, 10_000)
            .cost(STEEL, 10_000)
            .cost(ALUMINUM, 10_000)
            .requiredProjects(() -> new Project[]{RESEARCH_AND_DEVELOPMENT_CENTER, CLINICAL_RESEARCH_CENTER})
            .build();

    /*
        Metropolitan Planning
          Requirement: City 21 or higher, Urban Planning, Advanced Urban Planning.
          Effect: Reduces City costs by $150,000,000 per city, stacks with UP and AUP.

    Military Salvage

          Effect: When you declare an attack and are victorious, recover 5% of lost steel/aluminum from units in that attack.

    Fallout Shelter

          Requirement: Research and Development Center, Clinical Research Center
          Effect:
          -Reduces damage from nuclear missiles by 10%.
          -Reduces fall-out length in a city by 25%
          -Maximum radiation impact on food is 90% (can always produce 10% of food)
          Cost:
          Cash: $25,000,000
          Food: 100,000
          Lead: 10,000
          Steel: 10,000
          Aluminum: 10,000
     */

    public static int getScore() {
        return 20;
    }


    // Recycling Initiative

    public static class Builder {
        private final int id;
        private String apiName,imageName;
        private Map<ResourceType, Double> resources = new EnumMap<>(ResourceType.class);
        private ResourceType output;
        private Supplier<Project[]> requiredProjects;

        private int requiredCities, maxCities;
        private Predicate<DBNation> otherRequirements;

        public Builder(String apiName, int id) {
            this.apiName = apiName;
            this.imageName = apiName;
            this.id = id;
        }

        public Builder image(String imageName) {
            this.imageName = imageName;
            return this;
        }

        public Builder cost(ResourceType type, double amt) {
            resources.put(type, amt);
            return this;
        }

        public Builder output(ResourceType  output) {
            this.output = output;
            return this;
        }

        public Project build() {
            return new AProject(id, apiName, imageName, resources, output, requiredCities, maxCities, requiredProjects, otherRequirements);
        }

        public Builder requiredProjects(Supplier<Project[]> projects) {
            this.requiredProjects = projects;
            return this;
        }

        public Builder requiredCities(int cities) {
            this.requiredCities = cities;
            return this;
        }

        public Builder maxCities(int cities) {
            this.maxCities = cities;
            return this;
        }

        public Builder otherRequirements(Predicate<DBNation> requirements) {
            this.otherRequirements = requirements;
            return this;
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
