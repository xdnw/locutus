package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.*;

import static link.locutus.discord.apiv1.enums.ResourceType.*;

public enum MilitaryUnit {
    SOLDIER("soldiers", "\uD83D\uDC82", 0.0004,
            ResourceType.MONEY.builder(5).build(),
            ResourceType.MONEY.builder(1.25).add(ResourceType.FOOD, 1/750d).build(),
            1.5,
            MUNITIONS.toArray(1 / 5000d)
    ),
    TANK("tanks", "\u2699",0.025,
            ResourceType.MONEY.builder(60).add(STEEL, 0.5d).build(),
            ResourceType.MONEY.toArray(50),
            1.5,
            GASOLINE.builder(1 / 100d).add(MUNITIONS, 1 / 100d).build()
        ),
    AIRCRAFT("aircraft", "\u2708", 0.3,
            ResourceType.MONEY.builder(4000).add(ALUMINUM, 10).build(),
            ResourceType.MONEY.toArray(750),
            1.3333333333333333333333333333333,
            GASOLINE.builder(1 / 4d).add(MUNITIONS, 1 / 4d).build()
    ),
    SHIP("navy", "\uD83D\uDEA2", 1,
            ResourceType.MONEY.builder(50000).add(STEEL, 30).build(),
            ResourceType.MONEY.toArray(3300),
            1.5151515151515151515151515151515,
            GASOLINE.builder(1).add(MUNITIONS, 1.75).build()
    ),

    MONEY(null, "\uD83D\uDCB2", 0,
            // Is a unit type because you can airstrike money
            ResourceType.MONEY.builder(1).build(),
            ResourceType.MONEY.toArray(0),
            1,
            ResourceType.getBuffer()
    ),

    MISSILE("missiles", "\uD83D\uDE80", 5,
            ResourceType.MONEY.builder(150000).add(ALUMINUM, 150).add(GASOLINE, 100).add(MUNITIONS, 100).build(),
            ResourceType.MONEY.toArray(21000),
            1.5,
            ResourceType.getBuffer()
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
            ResourceType.getBuffer()
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
            ResourceType.MONEY.toArray(3500)
    ),

    INFRASTRUCTURE(null, "\uD83C\uDFD7", 1 / 40d,
            // Is a unit type because you can airstrike infra
            ResourceType.MONEY.toArray(0),
            ResourceType.MONEY.toArray(0),
            1,
            ResourceType.getBuffer()
    ),
    ;

    public static final double NUKE_RADIATION = 5;

    private final double[] cost;
    private final Map<ResourceType, Double> costMap;

    private final Supplier<Double> costConverted;
    private final Supplier<Double> costSalvageConverted;
    private final ResourceType[] costRss;
    private final ResourceType[] consumeRss;

    protected final double score;
    private final double[] consumption;
    private final String name, emoji;
    private final double[] upkeepPeace;
    private final double[] upkeepWar;
    private final ResourceType[] upkeepRss;

    public static MilitaryUnit[] values = values();

    private Research costReducer;
    private Research upkeepReducer;

    private double[] costReduction;
    private ResourceType[] costReductionRss;
    private Supplier<Double> costReductionConverted;
    private Supplier<Double> costReductionSalvageConverted;

    private TriConsumer<Integer, Integer, double[]> warUpkeepRed;
    private TriConsumer<Integer, Integer, double[]> peaceUpkeepRed;
    private Int2DoubleFunction warUpkeepRedConv;
    private Int2DoubleFunction peaceUpkeepRedConv;
    private Research capacityResearch;
    private int capacityAmount;
    private Research rebuyResearch;
    private int rebuyAmount;

    MilitaryUnit(String name, String emoji, double score, double[] cost, double[] peacetimeUpkeep, double multiplyWartimeUpkeep, double[] consumption) {
        this.name = name;
        this.emoji = emoji;
        this.cost = cost;
        this.costConverted = ResourceType.convertedCostLazy(cost);
        double[] costSalvage = ResourceType.getBuffer();
        costSalvage[ALUMINUM.ordinal()] = cost[ALUMINUM.ordinal()] * 0.05;
        costSalvage[STEEL.ordinal()] = cost[STEEL.ordinal()] * 0.05;
        this.costSalvageConverted = ResourceType.convertedCostLazy(costSalvage);

        this.costMap = new EnumMap<>(ResourceType.class);
        costMap.putAll(resourcesToMap(cost));
        this.upkeepPeace = peacetimeUpkeep;
        if (multiplyWartimeUpkeep != 1) {
            this.upkeepWar = PW.multiply(peacetimeUpkeep.clone(), multiplyWartimeUpkeep);
        } else {
            this.upkeepWar = peacetimeUpkeep;
        }
        this.upkeepRss = resourcesToMap(peacetimeUpkeep).keySet().toArray(new ResourceType[0]);

        this.consumption = consumption;
        this.score = score;

        this.costRss = costMap.keySet().toArray(new ResourceType[0]);
        this.consumeRss = resourcesToMap(consumption).keySet().toArray(new ResourceType[0]);
    }

    public void setCapacityResearch(Research research, int capacity) {
        this.capacityResearch = research;
        this.capacityAmount = capacity;
    }

    public void setRebuyResearch(Research research, int amt) {
        this.rebuyResearch = research;
        this.rebuyAmount = amt;
    }

    public void setCostResearch(Research research, double[] costReduction) {
        this.costReducer = research;
        this.upkeepReducer = research;

        this.costReduction = costReduction;
        this.costReductionRss = ResourceType.getTypes(costReduction);

        this.costReductionConverted = ResourceType.convertedCostLazy(costReduction);
        double[] costReductionSalvage = ResourceType.getBuffer();
        costReductionSalvage[ALUMINUM.ordinal()] = costReduction[ALUMINUM.ordinal()] * 0.05;
        costReductionSalvage[STEEL.ordinal()] = costReduction[STEEL.ordinal()] * 0.05;
        this.costReductionSalvageConverted = ResourceType.convertedCostLazy(costReductionSalvage);
    }

    public void setUpkeepResearch(TriConsumer<Integer, Integer, double[]> warUpkeepRed, TriConsumer<Integer, Integer, double[]> peaceUpkeepRed, Int2DoubleFunction warUpkeepRedConv, Int2DoubleFunction peaceUpkeepRedConv) {
        this.warUpkeepRed = warUpkeepRed;
        this.peaceUpkeepRed = peaceUpkeepRed;
        this.warUpkeepRedConv = warUpkeepRedConv;
        this.peaceUpkeepRedConv = peaceUpkeepRedConv;
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
        return getMaxPerDay(cities, f -> false, f -> 0);
    }

    public int getMaxPerDay(int cities, Predicate<Project> hasProject, Function<Research, Integer> getResearch) {
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
        Research research = this.rebuyResearch;
        return cap;
    }

    public int getCap(DBNation nation, boolean update) {
        int researchBits = nation.getResearchBits();
        return getCap(() -> nation.getCityMap(update).values(), nation::hasProject, researchBits);
    }

    /**
     * Maximum units that can be held for a given city count and projects
     * @param numCities
     * @param hasProject
     * @return
     */
    public int getMaxMMRCap(int numCities, int research, Predicate<Project> hasProject) {
        switch (this) {
            case MONEY,INFRASTRUCTURE:
                return Integer.MAX_VALUE;
            case SOLDIER:
            case TANK:
            case AIRCRAFT:
            case SHIP:
                MilitaryBuilding building = getBuilding();
                if (building == null) throw new IllegalArgumentException("Unknown building: " + this);
                int cap = building.getUnitCap() * building.cap(hasProject) * numCities;
                if (this.capacityResearch != null) {
                    int level = this.capacityResearch.getLevel(research);
                    if (level > 0) {
                        cap += this.capacityAmount * level;
                    }
                }
                return cap;
            case MISSILE:
            case NUKE:
                return Integer.MAX_VALUE;
            case SPIES:
                return hasProject.test(Projects.INTELLIGENCE_AGENCY) ? 60 : 50;
            default:
                throw new IllegalArgumentException("Unknown cap: " + this);
        }
    }

    public int getCap(Supplier<Collection<JavaCity>> citiesSupplier, Predicate<Project> hasProject, int researchBits) {
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
                if (this.capacityResearch != null) {
                    int level = this.capacityResearch.getLevel(researchBits);
                    if (level > 0) {
                        cap += this.capacityAmount * level;
                    }
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

    public double[] addUpkeep(double[] buffer, int amt, boolean war, int researchBits, double factor) {
        if (upkeepReducer == null) {
            double[] baseUpkeep = war ? upkeepWar : upkeepPeace;
            for (ResourceType type : upkeepRss) {
                double costPer = baseUpkeep[type.ordinal()];
                buffer[type.ordinal()] += costPer * amt * factor;
            }
            return buffer;
        }
        int level = upkeepReducer.getLevel(researchBits);
        TriConsumer<Integer, Integer, double[]> upkeepReduction;
        double[] baseUpkeep;
        if (war) {
            baseUpkeep = upkeepWar;
            upkeepReduction = warUpkeepRed;
        } else {
            baseUpkeep = upkeepPeace;
            upkeepReduction = peaceUpkeepRed;
        }
        for (ResourceType type : upkeepRss) {
            double costPer = baseUpkeep[type.ordinal()];
            buffer[type.ordinal()] += costPer * amt * factor;
        }
        if (level != 0) {
            upkeepReduction.accept(level, amt, buffer);
        }
        return buffer;
    }

    public double[] addUpkeep(double[] buffer, int amt, boolean war, Function<Research, Integer> research, double factor) {
        if (upkeepReducer == null) {
            double[] baseUpkeep = war ? upkeepWar : upkeepPeace;
            for (ResourceType type : upkeepRss) {
                double costPer = baseUpkeep[type.ordinal()];
                buffer[type.ordinal()] += costPer * amt * factor;
            }
            return buffer;
        }
        double[] baseUpkeep;
        TriConsumer<Integer, Integer, double[]> upkeepReduction;
        if (war) {
            baseUpkeep = upkeepWar;
            upkeepReduction = warUpkeepRed;
        } else {
            baseUpkeep = upkeepPeace;
            upkeepReduction = peaceUpkeepRed;
        }
        int level = research.apply(upkeepReducer);
        for (ResourceType type : upkeepRss) {
            double costPer = baseUpkeep[type.ordinal()];
            buffer[type.ordinal()] += costPer * amt * factor;
        }
        if (level != 0) {
            upkeepReduction.accept(level, amt, buffer);
        }
        return buffer;
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

    public double getConvertedCost(Function<Research, Integer> research) {
        double value = costConverted.get();
        if (costReducer == null) return value;
        int level = research.apply(costReducer);
        value -= costReductionConverted.get() * level;
        return value;
    }

    public double getConvertedCost(int researchBits) {
        double value = costConverted.get();
        if (researchBits != 0 && costReducer != null) {
            int level = costReducer.getLevel(researchBits);
            value -= costReductionConverted.get() * level;
        }
        return value;
    }

    public double getConvertedCostPlusSalvage(int researchBits) {
        double value = costConverted.get();
        if (researchBits != 0 && costReducer != null) {
            int level = costReducer.getLevel(researchBits);
            value -= costReductionConverted.get() * level;
        }
//        value += costReductionSalvageConverted.get() * level;  TODO FIXME SALVAGE
        value -= costSalvageConverted.get();
        return value;
    }

    public double getSalvageCostPerUnit() {
        return costSalvageConverted.get();
    }

    @Command(desc = "Base resource cost of this unit")
    public Map<ResourceType, Double> getBaseCost(@Default Integer amount) {
        if (amount == null) amount = 1;
        else if (amount < 0) {
            double[] copy = cost.clone();
            copy[0] = 0;
            PW.multiply(copy, amount * 0.75);
            return resourcesToMap(copy);
        }
        return resourcesToMap(PW.multiply(cost.clone(), amount));
    }

    @Command
    public double getBaseMonetaryValue(@Default Integer amount) {
        if (amount == null) amount = 1;
        else if (amount < 0) {
            double value = 0;
            for (ResourceType type : costRss) {
                if (type == ResourceType.MONEY) continue;
                value += cost[type.ordinal()] * amount * 0.75;
            }
        }
        return ResourceType.convertedTotal(cost) * amount;
    }

    public double[] addCost(double[] buffer, int amt, Function<Research, Integer> research) {
        int level = costReducer == null ? 0 : research.apply(costReducer);
        if (amt < 0) {
            for (ResourceType type : costRss) {
                if (type == ResourceType.MONEY) continue;
                double costPer = this.cost[type.ordinal()] - (level == 0 ? 0 : costReduction[type.ordinal()]);
                buffer[type.ordinal()] += costPer * amt * 0.75;
            }
            return buffer;
        }
        for (ResourceType type : costRss) {
            double costPer = this.cost[type.ordinal()] - (level == 0 ? 0 : costReduction[type.ordinal()] * level);
            buffer[type.ordinal()] += costPer * amt;
        }
        return buffer;
    }

    public double[] addCostSalvage(double[] buffer, int amt, int researchBits) {
        int level = costReducer == null ? 0 : costReducer.getLevel(researchBits);
        for (ResourceType type : costRss) {
            double baseCost = this.cost[type.ordinal()];
            double costPer = baseCost - (level == 0 ? 0 : costReduction[type.ordinal()] * level);
            if (type == ALUMINUM || type == STEEL) {
                costPer -= baseCost * 0.05;
            }
            buffer[type.ordinal()] += costPer * amt;
        }
        return buffer;
    }

    public double[] subtractSalvageCost(double[] buffer, int amt) {
        for (ResourceType type : costRss) {
            if (type == ALUMINUM || type == STEEL) {
                double baseCost = this.cost[type.ordinal()];
                buffer[type.ordinal()] -= baseCost * 0.05 * amt;
            }
        }
        return buffer;
    }

    public double[] addCost(double[] buffer, int amt, int researchBits) {
        int level = costReducer == null ? 0 : costReducer.getLevel(researchBits);
        if (amt < 0) {
            for (ResourceType type : costRss) {
                if (type == ResourceType.MONEY) continue;
                double costPer = this.cost[type.ordinal()] - (level == 0 ? 0 : costReduction[type.ordinal()] * level);
                buffer[type.ordinal()] += costPer * amt * 0.75;
            }
            return buffer;
        }
        for (ResourceType type : costRss) {
            double costPer = this.cost[type.ordinal()] - (level == 0 ? 0 : costReduction[type.ordinal()] * level);
            buffer[type.ordinal()] += costPer * amt;
        }
        return buffer;
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

    public Map<ResourceType, Double> getCost(int amt, Function<Research, Integer> research) {
        return ResourceType.resourcesToMap(addCost(ResourceType.getBuffer(), amt, research));
    }
}
