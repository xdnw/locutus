package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.QuadFunction;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public class Projects {
    public static final Project ADVANCED_URBAN_PLANNING = new Builder("advanced_urban_planning", 15)
            .cost(OIL, 8000)
            .cost(BAUXITE, 8000)
            .cost(IRON, 8000)
            .cost(LEAD, 8000)
            .cost(COAL, 8000)
            .cost(FOOD, 500000)
            .cost(URANIUM, 24000)
            .requiredCities(16)
            .build().disable();

    public static final Project ARMS_STOCKPILE = new Builder("arms_stockpile", 2)
            .image("armsstockpile.png")
            .cost(MONEY, 10000000)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(MUNITIONS)
            .roi(getCityBuildRoi())
            .build();

    private static QuadFunction<Integer, DBNation, Project, Double, RoiResult> getCityBuildRoi() {
        return (days, nation, project, originRevenue) -> {
            Continent continent = nation.getContinent();
            ResourceType output = project.getOutput();
            if (output != null && output.hasBuilding()) {
                Building building = output.getBuilding();
                if (!building.canBuild(continent)) return null;
            }
            // get first city
            DBCity first = nation._getCitiesV3().values().iterator().next();
            Predicate<Project> hasProject = Projects.optimize(nation.hasProjectPredicate().or(f -> f == project));
            Function<ICity, Double> profit = city -> {
                return PW.City.profitConverted(continent, nation.getRads(), hasProject, nation.getCities(), nation.getGrossModifier(), city);
            };
            // optimalbuild
            JavaCity optimal = new JavaCity(first).zeroNonMilitary().setOptimalPower(continent).optimalBuild(nation.getContinent(),
                    nation.getCities(),
                    INationCity::getRevenueConverted,
                    null,
                    hasProject,
                    5000,
                    nation.getRads(),
                    false,
                    true,
                    nation.getGrossModifier(),
                    null);

            double optimalProfit = profit.apply(optimal);
            double revenuePerDay = Math.max(0, optimalProfit - originRevenue) * days * nation.getCities();

            // return revenue increase over now
            return new RoiResult(
                    "Additional city revenue over the timeframe",
                    revenuePerDay,
                    project.getMarketValue()
            );
        };
    }

    public static final Project BAUXITEWORKS = new Builder("bauxite_works", 1)
            .image("bauxiteworks.png")
            .cost(MONEY, 10000000)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(ALUMINUM)
            .roi(getCityBuildRoi())
            .build();

    public static final Project CENTER_FOR_CIVIL_ENGINEERING = new Builder("center_for_civil_engineering", 11)
            .image("cfce.png")
            .cost(OIL, 1000)
            .cost(IRON, 1000)
            .cost(BAUXITE, 1000)
            .cost(MONEY, 3000000)
            .roi((days, nation, project, oldRevenue) -> {
                double value = 0;
                for (DBCity city : nation._getCitiesV3().values()) {
                    value += PW.City.Infra.calculateInfra(0, city.getInfra());
                }
                return new RoiResult(
                        "5% infra cost reduction. Value is based on current levels",
                        value * 0.05,
                        project.getMarketValue()
                );
            })
            .build();

    public static final Project URBAN_PLANNING = new Builder("urban_planning", 14)
            .cost(OIL, 4000)
            .cost(BAUXITE, 4000)
            .cost(IRON, 4000)
            .cost(LEAD, 4000)
            .cost(COAL, 4000)
            .cost(FOOD, 250000)
            .cost(URANIUM, 12000)
            .requiredCities(11)
            .build().disable();

    public static final Project EMERGENCY_GASOLINE_RESERVE = new Builder("emergency_gasoline_reserve", 3)
            .image("emergencygasolinereserve.png")
            .cost(MONEY, 10000000)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(GASOLINE)
            .roi(getCityBuildRoi())
            .build();

    public static final Project INTELLIGENCE_AGENCY = new Builder("central_intelligence_agency", 10)
            .image("cia.png")
            .cost(STEEL, 500)
            .cost(GASOLINE, 500)
            .cost(MONEY, 5000000)
            .build();

    public static final Project INTERNATIONAL_TRADE_CENTER = new Builder("international_trade_center", 5)
            .image("internationaltradecenter.png")
            .cost(MONEY, 50000000)
            .cost(ALUMINUM, 10000)
            .roi(getCityBuildRoi())
            .build();

    public static final Project IRON_DOME = new Builder("iron_dome", 8)
            .image("irondome.png")
            .cost(MONEY, 15000000)
            .cost(MUNITIONS, 5000)
            .build();

    public static final Project IRON_WORKS = new Builder("iron_works", 0)
            .image("ironworks.png")
            .cost(MONEY, 10000000)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(STEEL)
            .roi(getCityBuildRoi())
            .build();

    public static final Project MASS_IRRIGATION = new Builder("mass_irrigation", 4)
            .image("massirrigation.png")
            .cost(MONEY, 10000000)
            .cost(FOOD, 50000)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(FOOD)
            .roi(getCityBuildRoi())
            .build();

    public static final Project MISSILE_LAUNCH_PAD = new Builder("missile_launch_pad", 6)
            .image("missilelaunchpad.png")
            .cost(MONEY, 15000000)
            .cost(MUNITIONS, 5000)
            .cost(GASOLINE, 5000)
            .cost(ALUMINUM, 5000)
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
            .cost(MONEY, 75000000)
            .cost(URANIUM, 5000)
            .cost(GASOLINE, 5000)
            .cost(ALUMINUM, 5000)
            .build();

    public static final Project PROPAGANDA_BUREAU = new Builder("propaganda_bureau", 12)
            .image("pb.png")
            .cost(MONEY, 10_000_000)
            .cost(GASOLINE, 2000)
            .cost(MUNITIONS, 2000)
            .cost(ALUMINUM, 2000)
            .cost(STEEL, 2000)
            .build();

    public static final Project SPACE_PROGRAM = new Builder("space_program", 16)
            .image("space_program.png")
            .cost(MONEY, 50_000_000)
            .cost(ALUMINUM, 25_000)
            .build();

    public static final Project SPY_SATELLITE = new Builder("spy_satellite", 17)
            .image("spy_satellite.png")
            .cost(MONEY, 20_000_000)
            .cost(OIL, 10_000)
            .cost(BAUXITE, 10_000)
            .cost(IRON, 10_000)
            .cost(LEAD, 10_000)
            .cost(COAL, 10_000)
            .requiredProjects(() -> new Project[]{SPACE_PROGRAM,INTELLIGENCE_AGENCY})
            .build();

    public static final Project URANIUM_ENRICHMENT_PROGRAM = new Builder("uranium_enrichment_program", 13)
            .image("uap.png")
            .cost(MONEY, 25_000_000)
            .cost(URANIUM, 2500)
            .cost(COAL, 500)
            .cost(IRON, 500)
            .cost(OIL, 500)
            .cost(BAUXITE, 500)
            .cost(LEAD, 500)
            .output(URANIUM)
            .roi(getCityBuildRoi())
            .build();

    public static final Project VITAL_DEFENSE_SYSTEM = new Builder("vital_defense_system", 9)
            .image("vds.png")
            .cost(MONEY, 40_000_000)
            .cost(STEEL, 5000)
            .cost(ALUMINUM, 5000)
            .cost(MUNITIONS, 5000)
            .cost(GASOLINE, 5000)
            .build();

    public static final Project RECYCLING_INITIATIVE = new Builder("recycling_initiative", 20)
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .requiredProjects(() -> new Project[]{CENTER_FOR_CIVIL_ENGINEERING})
            .roi(getCityBuildRoi())
            .build();

    public static final Project PIRATE_ECONOMY = new Builder("pirate_economy", 19)
            .cost(MONEY, 25_000_000)
            .cost(COAL, 7500)
            .cost(IRON, 7500)
            .cost(OIL, 7500)
            .cost(BAUXITE, 7500)
            .cost(LEAD, 7500)
            .otherRequirements(f -> f.getWars_won() + f.getWars_lost() >= 50)
            .build();

    public static final Project GREEN_TECHNOLOGIES = new Builder("green_tech", 22)
            .cost(MONEY, 50_000_000)
            .cost(FOOD, 100_000)
            .cost(ALUMINUM, 10_000)
            .cost(IRON, 10_000)
            .cost(OIL, 10_000)
            .requiredProjects(() -> new Project[]{URBAN_PLANNING, SPACE_PROGRAM})
            .roi(getCityBuildRoi())
            .build();

    public static final Project TELECOMMUNICATIONS_SATELLITE = new Builder("telecommunications_satellite", 21)
            .cost(URANIUM, 10000)
            .cost(IRON, 10000)
            .cost(OIL, 10000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 300000000)
            .requiredProjects(() -> new Project[]{INTERNATIONAL_TRADE_CENTER, URBAN_PLANNING, SPACE_PROGRAM})
            .roi(getCityBuildRoi())
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
            .roi((days, nation, project, oldRevenue) -> {
                double infraValue = 0;
                double landValue = 0;
                for (DBCity city : nation._getCitiesV3().values()) {
                    infraValue += PW.City.Infra.calculateInfra(0, city.getInfra());
                    landValue += PW.City.Land.calculateLand(0, city.getLand());
                }
                double value = (infraValue + landValue) * 0.05;
                return new RoiResult(
                "5% infra + land cost reduction. Value is based on current levels",
                        value,
                        project.getMarketValue()
                );
            })
            .build();

    public static final Project ARABLE_LAND_AGENCY = new Builder("arable_land_agency", 23)
            .cost(COAL, 1500)
            .cost(LEAD, 1500)
            .cost(MONEY, 3000000)
            .roi((days, nation, project, oldRevenue) -> {
                double landValue = 0;
                for (DBCity city : nation._getCitiesV3().values()) {
                    landValue += PW.City.Land.calculateLand(0, city.getLand());
                }
                return new RoiResult(
                        "5% land cost reduction. Value is based on current levels",
                        landValue * 0.05,
                        project.getMarketValue()
                );
            })
            .build();

    public static final Project CLINICAL_RESEARCH_CENTER = new Builder("clinical_research_center", 24)
            .cost(FOOD, 100000)
            .cost(MONEY, 10000000)
            .roi(getCityBuildRoi())
            .build();

    public static final Project SPECIALIZED_POLICE_TRAINING_PROGRAM = new Builder("specialized_police_training_program", 25)
            .image("specialized_police_training_program.jpg")
            .cost(MONEY, 50_000_000)
            .cost(FOOD, 250_000)
            .cost(ALUMINUM, 5_000)
            .roi(getCityBuildRoi())
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
            .maxCities(20)
            .roi((days, nation, project, oldRevenue) -> {
                if (nation.getCities() > project.maxCities()) {
                     return null;
                }
                return new RoiResult(
                        "Income over timeframe, assumes daily login.",
                        days <= 0 ? 0 : 2_000_000 * (days - 1) + (1_000_000),
                        project.getMarketValue()
                );
            })
            .build();

    public static final Project GOVERNMENT_SUPPORT_AGENCY = new Builder("government_support_agency", 27)
            .image("government_support_agency.jpg")
            .cost(FOOD, 200000)
            .cost(ALUMINUM, 10000)
            .cost(MONEY, 20000000)
            .roi((days, nation, project, oldRevenue) -> {
                double value = 0;
                for (DBCity city : nation._getCitiesV3().values()) {
                    value += PW.City.Infra.calculateInfra(0, city.getInfra());
                    value += PW.City.Land.calculateLand(0, city.getLand());
                }
                value += nation.cityValue();
                value += nation.projectValue();
                return new RoiResult(
                        "5% land + infra + cities + projects. Value assumes those double over the timeframe",
                        value * 0.05,
                        project.getMarketValue()
                );
            })
            .build();

    public static final Project METROPOLITAN_PLANNING = new Builder("metropolitan_planning", 30)
            .cost(OIL, 12000)
            .cost(BAUXITE, 12000)
            .cost(IRON, 12000)
            .cost(LEAD, 12000)
            .cost(COAL, 12000)
            .cost(FOOD, 750000)
            .cost(URANIUM, 36000)
            .requiredCities(21)
            .requiredProjects(() -> new Project[]{URBAN_PLANNING, ADVANCED_URBAN_PLANNING})
            .build().disable();

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
            .cost(ALUMINUM, 15_000)
            .cost(STEEL, 10_000)
            .requiredProjects(() -> new Project[]{RESEARCH_AND_DEVELOPMENT_CENTER, CLINICAL_RESEARCH_CENTER})
            .build();

    // Bureau of Domestic Affairs:
    //Requires Government Support Agency.
    //
    //Reduces the timer for changing Domestic Policies to 1 turn.
    public static final Project BUREAU_OF_DOMESTIC_AFFAIRS = new Builder("bureau_of_domestic_affairs", 33)
            .cost(MONEY, 20_000_000)
            .cost(FOOD, 500_000)
            .cost(COAL, 8_000)
            .cost(BAUXITE, 8_000)
            .cost(LEAD, 8_000)
            .cost(IRON, 8_000)
            .cost(OIL, 8_000)
            .requiredProjects(() -> new Project[]{GOVERNMENT_SUPPORT_AGENCY})
            .roi((days, nation, project, oldRevenue) -> {
                // 2.5% of land + infra + cities + projects
                double value = 0;
                for (DBCity city : nation._getCitiesV3().values()) {
                    value += PW.City.Infra.calculateInfra(0, city.getInfra());
                    value += PW.City.Land.calculateLand(0, city.getLand());
                }
                value += nation.cityValue();
                value += nation.projectValue();
                return new RoiResult(
                        "2.5% for new land + infra + cities + projects. Value assumes those double over the timeframe",
                        value * 0.025,
                        project.getMarketValue()
                );
            })
            .build();
    //
    //
    //Added Advanced Pirate Economy:
    //Requires Pirate Economy and that the nation has won or lost 100 combined wars.
    //Adds an additional offensive war slot, 5% more loot from ground attacks, and a 1.1x modifier to loot from defeating a nation and the defeated nation’s alliance bank.
    //Pirate Economy now provides a 5% bonus to loot from ground attacks.
    public static final Project ADVANCED_PIRATE_ECONOMY = new Builder("advanced_pirate_economy", 34)
            .cost(MONEY, 50_000_000)
            .cost(COAL, 10000)
            .cost(IRON, 10000)
            .cost(OIL, 10000)
            .cost(BAUXITE, 10000)
            .cost(LEAD, 10000)
            .requiredProjects(() -> new Project[]{PIRATE_ECONOMY})
            .otherRequirements(f -> f.getWars_won() + f.getWars_lost() >= 100)
            .build();

    //
    //Added Mars Landing:
    //Requires Space Program and Moon Landing.
    //Similar to Moon Landing, provides a unique achievement to the first player to build it as well as one to every other player who builds it. All players who complete it will be tracked on a leaderboard like the Moon Landing project. Nations will also gain a daily boost to their approval rating.
    public static final Project MARS_LANDING = new Builder("mars_landing", 35)
            .cost(MONEY, 200_000_000)
            .cost(OIL, 20_000)
            .cost(ALUMINUM, 20_000)
            .cost(MUNITIONS, 20_000)
            .cost(STEEL, 20_000)
            .cost(GASOLINE, 20_000)
            .cost(URANIUM, 20_000)
            .requiredProjects(() -> new Project[]{SPACE_PROGRAM, MOON_LANDING})
            .build();

    //
    //Added Surveillance Network:
    //Requires Intelligence Agency and Advanced Urban Planning.
    //Spy attacks against your nation are 10% less likely to succeed and the attacker is 10% more likely to be identified.
    public static final Project SURVEILLANCE_NETWORK = new Builder("surveillance_network", 36)
            .cost(MONEY, 50_000_000)
            .cost(ALUMINUM, 50_000)
            .cost(BAUXITE, 15_000)
            .cost(IRON, 15_000)
            .cost(LEAD, 15_000)
            .cost(COAL, 15_000)
            .requiredProjects(() -> new Project[]{INTELLIGENCE_AGENCY, ADVANCED_URBAN_PLANNING})
            .build();

    // Guiding Satellite
    // Money 200,000,000
    //Munitions 40,000
    //Uranium 40,000
    //Gasoline 40,000
    //Aluminum 40,000
    //Steel 20,000
    // Guiding Satellite increases infrastructure damage dealt by Missles and Nuclear Weapons by 20%
    // as well as destroy an additional improvement.
    // Requires the Nuclear Research Facility, Missile Launch Pad, and Space Program National Projects to build.
    public static final Project GUIDING_SATELLITE = new Builder("guiding_satellite", 37)
            .cost(MONEY, 200_000_000)
            .cost(MUNITIONS, 40_000)
            .cost(URANIUM, 40_000)
            .cost(GASOLINE, 40_000)
            .cost(ALUMINUM, 40_000)
            .cost(STEEL, 20_000)
            .requiredProjects(() -> new Project[]{NUCLEAR_RESEARCH_FACILITY, MISSILE_LAUNCH_PAD, SPACE_PROGRAM})
            .build();

    // Nuclear Launch Facility is a national project that allows you to build an additional Nuclear Weapon each day.
    // Requires the Nuclear Research Facility, Missile Launch Pad, and Space Program National Projects to build.
    // Money
    //Money
    // 750,000,000
    //Uranium 50,000
    //Gasoline 50,000
    //Aluminum 50,000
    public static final Project NUCLEAR_LAUNCH_FACILITY = new Builder("nuclear_launch_facility", 38)
            .cost(MONEY, 750_000_000)
            .cost(URANIUM, 50_000)
            .cost(GASOLINE, 50_000)
            .cost(ALUMINUM, 50_000)
            .requiredProjects(() -> new Project[]{NUCLEAR_RESEARCH_FACILITY, MISSILE_LAUNCH_PAD, SPACE_PROGRAM})
            .build();

    // Military Research Center
    // <td>
    //                            <img src="/img/resources/money.png" data-toggle="tooltip" title="" alt="Money" data-original-title="Money">
    //            100,000,000.00        <br>            <img src="/img/resources/steel.png" data-toggle="tooltip" title="" alt="Steel" data-original-title="Steel">
    //            10,000.00        <br>            <img src="/img/resources/aluminum.png" data-toggle="tooltip" title="" alt="Aluminum" data-original-title="Aluminum">
    //            10,000.00        <br>            <img src="/img/resources/munitions.png" data-toggle="tooltip" title="" alt="Munitions" data-original-title="Munitions">
    //            10,000.00        <br>            <img src="/img/resources/gasoline.png" data-toggle="tooltip" title="" alt="Gasoline" data-original-title="Gasoline">
    //            10,000.00                    </td>
    public static final Project MILITARY_RESEARCH_CENTER = new Builder("military_research_center", 39)
            .cost(MONEY, 100_000_000)
            .cost(STEEL, 10_000)
            .cost(ALUMINUM, 10_000)
            .cost(MUNITIONS, 10_000)
            .cost(GASOLINE, 10_000)
            .requiredProjects(() -> new Project[]{PROPAGANDA_BUREAU})
            .build();

    // Military Doctrine
    // <td>
    //                            <img src="/img/resources/money.png" data-toggle="tooltip" title="" alt="Money" data-original-title="Money" aria-describedby="tooltip61962"><div class="tooltip fade top in" role="tooltip" id="tooltip61962" style="top: 6176.89px; left: 822.822px; display: block;"><div class="tooltip-arrow" style="left: 50%;"></div><div class="tooltip-inner">Money</div></div>
    //            10,000,000.00        <br>            <img src="/img/resources/steel.png" data-toggle="tooltip" title="" alt="Steel" data-original-title="Steel">
    //            10,000.00        <br>            <img src="/img/resources/aluminum.png" data-toggle="tooltip" title="" alt="Aluminum" data-original-title="Aluminum">
    //            10,000.00        <br>            <img src="/img/resources/munitions.png" data-toggle="tooltip" title="" alt="Munitions" data-original-title="Munitions">
    //            10,000.00        <br>            <img src="/img/resources/gasoline.png" data-toggle="tooltip" title="" alt="Gasoline" data-original-title="Gasoline">
    //            10,000.00                    </td>
    public static final Project MILITARY_DOCTRINE = new Builder("military_doctrine", 40)
            .cost(MONEY, 10_000_000)
            .cost(STEEL, 10_000)
            .cost(ALUMINUM, 10_000)
            .cost(MUNITIONS, 10_000)
            .cost(GASOLINE, 10_000)
            .requiredProjects(() -> new Project[]{MILITARY_RESEARCH_CENTER})
            .build();

    public static int getScore() {
        return 20;
    }

    public static Predicate<Project> optimize(Predicate<Project> projects) {
        int maxId = Arrays.stream(values).mapToInt(Project::ordinal).max().orElse(0);
        boolean[] hasProject = new boolean[maxId + 1];
        for (Project project : values) {
            hasProject[project.ordinal()] = projects.test(project);
        }
        return p -> hasProject[p.ordinal()];
    }

    public static Project[] values() {
        return values;
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
        private QuadFunction<Integer, DBNation, Project, Double, RoiResult> roi;

        public Builder(String apiName, int id) {
            this.apiName = apiName;
            this.imageName = apiName;
            this.id = id;
            this.maxCities = Integer.MAX_VALUE;
        }

        public Builder roi(QuadFunction<Integer, DBNation, Project, Double, RoiResult> roi) {
            this.roi = roi;
            return this;
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

        public AProject build() {
            return new AProject(id, apiName, imageName, resources, output, requiredCities, maxCities, requiredProjects, otherRequirements, roi);
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
    private static final Project[] PROJECTS_BY_ID;
    static {
        try {
            int maxId = 0;
            List<Project> projList = new ArrayList<>();
            int i = 0;
            for (Field field : Projects.class.getDeclaredFields()) {
                Object value = field.get(null);
                if (value != null && value instanceof Project) {
                    Project proj = (Project) value;
                    if (!proj.isDisabled()) projList.add(proj);

                    ((AProject) proj).setName(field.getName(), i++);
                    PROJECTS_MAP.put(field.getName(), proj);
                    maxId = Math.max(maxId, proj.ordinal());
                }
            }
            values = projList.toArray(new Project[0]);
            PROJECTS_BY_ID = new Project[maxId + 1];
            for (Project project : values) {
                PROJECTS_BY_ID[project.ordinal()] = project;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    public static Project get(int id) {
        return PROJECTS_BY_ID[id];
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
}
