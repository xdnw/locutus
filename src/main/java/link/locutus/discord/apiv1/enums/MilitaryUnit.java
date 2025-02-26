package link.locutus.discord.apiv1.enums;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public enum MilitaryUnit {
    SOLDIER("soldiers", "\uD83D\uDC82", 0.0004,
            ResourceType.MONEY.builder(5).build(),
            ResourceType.MONEY.builder(1.25).add(ResourceType.FOOD, 1/750d).build(),
            1.5,
            MUNITIONS.toArray(1 / 5000d),
            Research.GROUND_COST
    ),
    TANK("tanks", "\u2699",0.025,
            ResourceType.MONEY.builder(60).add(STEEL, 0.5d).build(),
            ResourceType.MONEY.toArray(50),
            1.5,
            GASOLINE.builder(1 / 100d).add(MUNITIONS, 1 / 100d).build(),
            Research.GROUND_COST
        ),
    AIRCRAFT("aircraft", "\u2708", 0.3,
            ResourceType.MONEY.builder(4000).add(ALUMINUM, 10).build(),
            ResourceType.MONEY.toArray(750),
            1.3333333333333333333333333333333,
            GASOLINE.builder(1 / 4d).add(MUNITIONS, 1 / 4d).build(),
            Research.AIR_COST
    ),
    SHIP("navy", "\uD83D\uDEA2", 1,
            ResourceType.MONEY.builder(50000).add(STEEL, 30).build(),
            ResourceType.MONEY.toArray(3300),
            1.5151515151515151515151515151515,
            GASOLINE.builder(1).add(MUNITIONS, 1.75).build(),
            Research.NAVAL_COST
    ),

    MONEY(null, "\uD83D\uDCB2", 0,
            // Is a unit type because you can airstrike money
            ResourceType.MONEY.builder(1).build(),
            ResourceType.MONEY.toArray(0),
            1,
            ResourceType.getBuffer(),
            null
    ),

    MISSILE("missiles", "\uD83D\uDE80", 5,
            ResourceType.MONEY.builder(150000).add(ALUMINUM, 150).add(GASOLINE, 100).add(MUNITIONS, 100).build(),
            ResourceType.MONEY.toArray(21000),
            1.5,
            ResourceType.getBuffer(),
            null
    ) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },
    NUKE("nukes", "\u2622", 15,
             ResourceType.MONEY.builder(1750000).add(ALUMINUM, 1000).add(GASOLINE, 500).add(URANIUM, 500).build(),
            ResourceType.MONEY.toArray(35000),
            1.5,
            ResourceType.getBuffer(),
            null
    ) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },

    SPIES("spies", "\uD83D\uDD0E", 0,
            ResourceType.MONEY.builder(50_000).build(),
            ResourceType.MONEY.toArray(2400),
            1,
            ResourceType.MONEY.toArray(3500),
            null
    ),

    INFRASTRUCTURE(null, "\uD83C\uDFD7", 1 / 40d,
            // Is a unit type because you can airstrike infra
            ResourceType.MONEY.toArray(0),
            ResourceType.MONEY.toArray(0),
            1,
            ResourceType.getBuffer(),
            null
    ),
    ;

    public static double NUKE_RADIATION = 5;

    private final double[] cost;
    private final Map<ResourceType, Double> costMap;

    protected final double score;
    private final double[] consumption;
    private final Research costReducer;
    private double costConverted = -1;
    private final String name, emoji;
    private final double[] upkeepPeace;
    private final double[] upkeepWar;

    public static MilitaryUnit[] values = values();

    MilitaryUnit(String name, String emoji, double score, double[] cost, double[] peacetimeUpkeep, double multiplyWartimeUpkeep, double[] consumption, Research costReducer) {
        this.name = name;
        this.emoji = emoji;
        this.cost = cost;
        this.costMap = new EnumMap<>(ResourceType.class);
        costMap.putAll(resourcesToMap(cost));
        this.upkeepPeace = peacetimeUpkeep;
        if (multiplyWartimeUpkeep != 1) {
            this.upkeepWar = PW.multiply(peacetimeUpkeep.clone(), multiplyWartimeUpkeep);
        } else {
            this.upkeepWar = peacetimeUpkeep;
        }
        this.consumption = consumption;
        this.score = score;
        this.costReducer = costReducer;
    }

    @Command(desc = "Get the emoji for this unit")
    public static int[] getBuffer() {
        return new int[MilitaryUnit.values.length];
    }

    public String getEmoji() {
        return emoji;
    }

    @Command(desc = "Get the max unit buys per day for a number of cities")
    public int getMaxPerDay(int cities) {
        return getMaxPerDay(cities, f -> false);
    }

    public int getMaxPerDay(int cities, Predicate<Project> hasProject) {
        MilitaryBuilding building = getBuilding();
        int cap;
        if (building != null) {
            cap = building.cap(hasProject) * building.getUnitDailyBuy() * cities;
            if (hasProject.test(Projects.PROPAGANDA_BUREAU)) {
                cap = (int) Math.round(cap * 1.1);
            }
        } else {
            cap = 0;
            switch (this) {
                case MISSILE -> {
                    if (hasProject.test(Projects.MISSILE_LAUNCH_PAD)) cap = 2;
                    if (hasProject.test(Projects.SPACE_PROGRAM)) cap = 3;
                }
                case NUKE -> {
                    if (hasProject.test(Projects.NUCLEAR_RESEARCH_FACILITY)) cap++;
                    if (hasProject.test(Projects.NUCLEAR_LAUNCH_FACILITY)) cap++;
                }
                case SPIES -> {
                    cap = 2;
                    if (hasProject.test(Projects.INTELLIGENCE_AGENCY)) cap++;
                    if (hasProject.test(Projects.SPY_SATELLITE)) cap++;
                }
            }
            return cap;
        }
        return cap;
    }

    public int getCap(DBNation nation, boolean update) {
        return getCap(() -> nation.getCityMap(update).values(), nation::hasProject);
    }

    /**
     * Maximum units that can be held for a given city count and projects
     * @param numCities
     * @param hasProject
     * @return
     */
    public int getMaxMMRCap(int numCities, Predicate<Project> hasProject) {
        switch (this) {
            case MONEY,INFRASTRUCTURE:
                return Integer.MAX_VALUE;
            case SOLDIER:
            case TANK:
            case AIRCRAFT:
            case SHIP:
                MilitaryBuilding building = getBuilding();
                if (building == null) throw new IllegalArgumentException("Unknown building: " + this);
                return building.getUnitCap() * building.cap(hasProject) * numCities;
            case MISSILE:
            case NUKE:
                return Integer.MAX_VALUE;
            case SPIES:
                return hasProject.test(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
            default:
                throw new IllegalArgumentException("Unknown cap: " + this);
        }
    }

    public int getCap(Supplier<Collection<JavaCity>> citiesSupplier, Predicate<Project> hasProject) {
        switch (this) {
            case MONEY,INFRASTRUCTURE:
                return Integer.MAX_VALUE;
            case SOLDIER:
            case TANK:
            case AIRCRAFT:
            case SHIP:
                MilitaryBuilding building = getBuilding();
                if (building == null) throw new IllegalArgumentException("Unknown building: " + this);

                int amt = 0;
                double pop = 0;
                Collection<JavaCity> cities = citiesSupplier.get();
                for (JavaCity city : cities) {
                    amt += city.getBuilding(building);
                    if (building.getCitizensPerUnit() > 0) {
                        pop += city.calcPopulation(hasProject);
                    }
                }
                int cap = building.cap(hasProject) * amt;
                if (cap > 0 && building.getCitizensPerUnit() > 0) {
                    cap = (int) Math.min(pop / building.getCitizensPerUnit(), cap);
                }
                return cap;
            case MISSILE:
                return hasProject.test(Projects.MISSILE_LAUNCH_PAD) ? Integer.MAX_VALUE : 0;
            case NUKE:
                return hasProject.test(Projects.NUCLEAR_RESEARCH_FACILITY) ? Integer.MAX_VALUE : 0;
            case SPIES:
                return hasProject.test(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
            default:
                throw new IllegalArgumentException("Unknown cap: " + this);
        }
    }

    public double getScore(int amt) {
        return score * amt;
    }

    public double[] getUpkeep(boolean war, Function<Research, Integer> research) {
        return war ? upkeepWar : upkeepPeace;
    }

    public String getName() {
        return name;
    }

    public static MilitaryUnit get(String arg) {
        for (MilitaryUnit unit : values()) {
            if (unit.name().equalsIgnoreCase(arg) || (unit.name != null && unit.name.equalsIgnoreCase(arg))) {
                return unit;
            }
        }
        return null;
    }

    public MilitaryBuilding getBuilding() {
        for (Building value : Buildings.values()) {
            if (value instanceof MilitaryBuilding && ((MilitaryBuilding) value).getMilitaryUnit() == this) {
                return (MilitaryBuilding) value;
            }
        }
        return null;
    }

    public double getConvertedCost() {
        if (costConverted == -1) {
            costConverted = convertedTotal(cost);
        }
        return costConverted;
    }

    public double[] getCost(Function<Research, Integer> research) {
        return this.cost;
    }

    public Map<ResourceType, Double> getCostMap(Function<Research, Integer> research) {
        return costMap;
    }

    public double[] getCost(Function<Research, Integer> research, int amt) {
        if (amt > 0) {
            if (amt == 1) return cost;
            return PW.multiply(cost.clone(), amt);
        } else if (amt < 0) {
            // 0% of money + 75% of resources
            double[] copy = cost.clone();
            copy[0] = 0;
            PW.multiply(copy, amt);
            for (int i = 1; i < copy.length; i++) {
                copy[i] *= 0.75;
            }
            return copy;
        }
        return ResourceType.getBuffer();
    }

    public double[] getConsumption() {
        return consumption;
    }

    public static MilitaryUnit valueOfVerbose(String arg) {
        try {
            return valueOf(arg);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid MilitaryUnit type: `" + arg + "`. Options: " + StringMan.getString(values()));
        }
    }


}
