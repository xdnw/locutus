package link.locutus.discord.apiv1.enums;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public enum MilitaryUnit {
    SOLDIER("soldiers", 0.0004,
            ResourceType.MONEY.builder(5).build(),
            ResourceType.MONEY.builder(1.25).add(ResourceType.FOOD, 1/750d).build(),
            true,
            MUNITIONS.toArray(1 / 5000d)
    ),
    TANK("tanks", 0.025,
            ResourceType.MONEY.builder(60).add(STEEL, 0.5d).build(),
            ResourceType.MONEY.toArray(50),
            true,
            GASOLINE.builder(1 / 100d).add(MUNITIONS, 1 / 100d).build()
        ),
    AIRCRAFT("aircraft", 0.3,
            ResourceType.MONEY.builder(4000).add(ALUMINUM, 5).build(),
            ResourceType.MONEY.toArray(500),
            true,
            GASOLINE.builder(1 / 4d).add(MUNITIONS, 1 / 4d).build()
    ),
    SHIP("navy", 1,
            ResourceType.MONEY.builder(50000).add(STEEL, 30).build(),
            ResourceType.MONEY.toArray(3375),
            true,
            GASOLINE.builder(1.5).add(MUNITIONS, 2.5).build()
    ),

    MONEY(null, 0,
            // Is a unit type because you can airstrike money
            ResourceType.MONEY.builder(1).build(),
            ResourceType.MONEY.toArray(0),
            false,
            ResourceType.getBuffer()
    ),

    MISSILE("missiles", 5,
            ResourceType.MONEY.builder(150000).add(ALUMINUM, 100).add(GASOLINE, 75).add(MUNITIONS, 75).build(),
            ResourceType.MONEY.toArray(21000),
            true,
            ResourceType.getBuffer()
    ) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },
    NUKE("nukes", 15,
             ResourceType.MONEY.builder(1750000).add(ALUMINUM, 750).add(GASOLINE, 500).add(URANIUM, 250).build(),
            ResourceType.MONEY.toArray(35000),
            true,
            ResourceType.getBuffer()
    ) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },

    SPIES("spies", 0,
            ResourceType.MONEY.builder(50_000).build(),
            ResourceType.MONEY.toArray(2400),
            true,
            ResourceType.MONEY.toArray(3500)
    ),
    ;

    private final double[] cost;

    protected final double score;
    private final double[] consumption;
    private double costConverted = -1;
    private final String name;
    private final double[] upkeepPeace;
    private final double[] upkeepWar;

    public static MilitaryUnit[] values = values();

    MilitaryUnit(String name, double score, double[] cost, double[] peacetimeUpkeep, boolean multiplyWartimeUpkeep, double[] consumption) {
        this.name = name;
        this.cost = cost;
        this.upkeepPeace = peacetimeUpkeep;
        if (multiplyWartimeUpkeep) {
            this.upkeepWar = PnwUtil.multiply(peacetimeUpkeep, 1.5);
        } else {
            this.upkeepWar = peacetimeUpkeep;
        }
        this.consumption = consumption;
        this.score = score;
    }

    public int getMaxPerDay(int cities, Predicate<Project> hasProject) {
        MilitaryBuilding building = getBuilding();
        int cap;
        if (building != null) {
            cap = building.cap() * building.perDay() * cities;
            if (hasProject.test(Projects.PROPAGANDA_BUREAU)) {
                cap *= 1.1;
            }
        } else {
            cap = 0;
            if (this == MISSILE) {
                if (hasProject.test(Projects.MISSILE_LAUNCH_PAD)) cap++;
                if (hasProject.test(Projects.MOON_LANDING)) cap++;
            } else if (this == NUKE && hasProject.test(Projects.NUCLEAR_RESEARCH_FACILITY)) cap++;
        }
        return cap;
    }

    public int getCap(DBNation nation, boolean update) {
        return getCap(() -> nation.getCityMap(update).values(), nation::hasProject);
    }
    public int getCap(Supplier<Collection<JavaCity>> citiesSupplier, Predicate<Project> hasProject) {
        switch (this) {
            case MONEY:
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
                    amt += city.get(building);
                    if (building.requiredCitizens() > 0) {
                        pop += city.getPopulation(hasProject);
                    }
                }
                int cap = building.cap() * amt;
                if (cap > 0 && building.requiredCitizens() > 0) {
                    cap = (int) Math.min(pop / building.requiredCitizens(), cap);
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

    public double[] getUpkeep(boolean war) {
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
            if (value instanceof MilitaryBuilding && ((MilitaryBuilding) value).unit() == this) {
                return (MilitaryBuilding) value;
            }
        }
        return null;
    }

    public double getConvertedCost() {
        if (costConverted == -1) {
            costConverted = PnwUtil.convertedTotal(cost);
        }
        return costConverted;
    }

    public double[] getCost() {
        return this.cost;
    }

    public double[] getCost(int amt) {
        if (amt > 0) {
            if (amt == 1) return cost;
            return PnwUtil.multiply(cost.clone(), amt);
        } else if (amt < 0) {
            // 0% of money + 75% of resources
            double[] copy = cost.clone();
            copy[0] = 0;
            PnwUtil.multiply(copy, amt);
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
            throw new IllegalArgumentException("Invalid MilitaryUnit type: `" + arg + "`. options: " + StringMan.getString(values()));
        }
    }
}
