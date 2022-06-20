package link.locutus.discord.apiv1.enums;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import views.grant.nation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public enum MilitaryUnit {
    SOLDIER("soldiers", 0.0004, 5),
    TANK("tanks", 0.025,60, new double[]{0.5d}, ResourceType.STEEL),
    AIRCRAFT("aircraft", 0.3,4000, new double[]{5}, ResourceType.ALUMINUM),
    SHIP("navy", 1, 50000, new double[]{30}, ResourceType.STEEL),

    MONEY(null, 0, 1), // Is a unit type because you can airstrike money

    MISSILE("missiles", 5, 150000, new double[]{100, 75, 75}, ResourceType.ALUMINUM, ResourceType.GASOLINE, ResourceType.MUNITIONS) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },
    NUKE("nukes", 15, 1750000, new double[]{750, 500, 250}, ResourceType.ALUMINUM, ResourceType.GASOLINE, ResourceType.URANIUM) {
        @Override
        public double getScore(int amt) {
            return this.score * Math.min(50, amt);
        }
    },

    SPIES("spies", 0, 50000),
    ;

    static {
        SOLDIER.setUpkeep(ResourceType.MONEY, 1.25, 1.88).setUpkeep(ResourceType.FOOD, 1 / 750d, 1 / 500d);
        TANK.setUpkeep(ResourceType.MONEY, 50, 75);
        AIRCRAFT.setUpkeep(ResourceType.MONEY, 500, 750);
        SHIP.setUpkeep(ResourceType.MONEY, 3750, 5625);
        SPIES.setUpkeep(ResourceType.MONEY, 2400, 2400);
        MISSILE.setUpkeep(ResourceType.MONEY, 21000, 31500);
        NUKE.setUpkeep(ResourceType.MONEY, 35000, 52500);
    }

    private final int cost;
    protected final double score;
    private double costConverted = 0;
    private final ResourceType[] resources;
    private final double[] rssAmt;
    private final String name;
    private final double[] upkeepPeace = new double[ResourceType.values.length];
    private final double[] upkeepWar = new double[ResourceType.values.length];

    public static MilitaryUnit[] values = values();

    MilitaryUnit(String name, double score, int cost) {
        this(name, score, cost, new double[]{});
    }

    MilitaryUnit(String name, double score, int cost, double[] rssAmt, ResourceType... resource) {
        this.name = name;
        this.cost = cost;
        this.resources = resource;
        this.rssAmt = rssAmt;
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

    private MilitaryUnit setUpkeep(ResourceType type, double peace, double war) {
        upkeepPeace[type.ordinal()] += peace;
        upkeepWar[type.ordinal()] += war;
        return this;
    }

    public double getScore(int amt) {
        return score * amt;
    }

    public double[] getUpkeep(boolean war) {
        return war ? upkeepWar : upkeepPeace;
    }

    public int getCost() {
        return cost;
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

    public Map<ResourceType, Double> getResourceCost() {
        Map<ResourceType, Double> result = new HashMap<>();
        result.put(ResourceType.MONEY, (double) cost);
        for (ResourceType type : resources) {
            result.put(type, (double) getRssAmt(type));
        }
        return result;
    }

    public double getConvertedCost() {
        if (costConverted == 0) {
            double total = this.cost;
            TradeManager tradeDb = Locutus.imp().getTradeManager();
            for (ResourceType type : resources) {
                total += tradeDb.getLowAvg(type) * getRssAmt(type);
            }
            costConverted = total;
        }
        return costConverted;
    }

    public ResourceType[] getResources() {
        return resources;
    }

    public static MilitaryUnit valueOfVerbose(String arg) {
        try {
            return valueOf(arg);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid MilitaryUnit type: `" + arg + "`. options: " + StringMan.getString(values()));
        }
    }

    public double getRssAmt(ResourceType type) {
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] == type) {
                return rssAmt[i];
            }
        }
        return 0;
    }
}
