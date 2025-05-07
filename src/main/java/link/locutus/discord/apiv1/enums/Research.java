package link.locutus.discord.apiv1.enums;

import com.politicsandwar.graphql.model.MilitaryResearch;
import it.unimi.dsi.fastutil.ints.Int2DoubleFunction;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum Research {
    // Decrease soldier cost by $0.1 and tank cost by $1 and 0.01 Steel, Reduce Soldier upkeep cost by $0.02 at peace, and $0.03 at war,
    // Increase the number of soldiers each ton of food feeds by 10 at peace, and 15 at war. It also reduces Tank upkeep by $1 at peace, and $1.5 at war
    GROUND_COST(ResearchGroup.GROUND, null, Map.of(
            MilitaryUnit.SOLDIER, Map.of(ResourceType.MONEY, 0.1, ResourceType.STEEL, 0.01),
            MilitaryUnit.TANK, Map.of(ResourceType.MONEY, 1.0, ResourceType.STEEL, 0.01)
    ), Map.of(
            MilitaryUnit.SOLDIER, Map.of(ResourceType.MONEY, 0.02),
            MilitaryUnit.TANK, Map.of(ResourceType.MONEY, 1.0)
    ), Map.of(
            MilitaryUnit.SOLDIER, Map.of(ResourceType.MONEY, 0.03),
            MilitaryUnit.TANK, Map.of(ResourceType.MONEY, 1.5)
    )) {
        @Override
        protected TriConsumer<Integer, Integer, double[]> applyUpkeepReduction(MilitaryUnit unit, boolean war) {
            if (unit == MilitaryUnit.SOLDIER) {
                if (war) {
                    double[] food = new double[20];
                    for (int i = 1; i < food.length; i++) {
                        food[i - 1] = (1d/500d) - (1d/(500d + i * 15d));
                    }
                    return (level, amt, rss) -> {
                        rss[ResourceType.MONEY.ordinal()] -= 0.03 * level * amt;
                        rss[ResourceType.FOOD.ordinal()] -= food[level - 1] * amt;
                    };
                } else {
                    double[] food = new double[20];
                    for (int i = 1; i < food.length; i++) {
                        food[i - 1] = (1d/750d) - (1d/(750d + i * 10d));
                    }
                    return (level, amt, rss) -> {
                        rss[ResourceType.MONEY.ordinal()] -= 0.02 * level * amt;
                        rss[ResourceType.FOOD.ordinal()] -= food[level - 1] * amt;
                    };
                }

            }
            return super.applyUpkeepReduction(unit, war);
        }

        @Override
        protected Int2DoubleFunction applyUpkeepReductionConverted(MilitaryUnit unit, boolean war) {
            Supplier<Double> foodValue = ResourceType.convertedCostLazy(ResourceType.FOOD, 1d);
            if (unit == MilitaryUnit.SOLDIER) {
                if (war) {
                    double[] food = new double[20];
                    for (int i = 1; i < food.length; i++) {
                        food[i - 1] = (1d/500d) - (1d/(500d + i * 15d));
                    }
                    return level -> {
                        double total = -0.03 * level;
                        total -= foodValue.get() * food[level - 1];
                        return total;
                    };
                } else {
                    double[] food = new double[20];
                    for (int i = 1; i < food.length; i++) {
                        food[i - 1] = (1d/750d) - (1d/(750d + i * 10d));
                    }
                    return level -> {
                        double total = -0.02 * level;
                        total -= foodValue.get() * food[level - 1];
                        return total;
                    };
                }
            }
            return super.applyUpkeepReductionConverted(unit, war);
        }
    },
    // Decrease plane cost by $50 and 0.2 Aluminium, Reduce plane upkeep cost by $15 at peace, and $10 at war
    AIR_COST(ResearchGroup.AIR, null, Map.of(
            MilitaryUnit.AIRCRAFT, Map.of(ResourceType.MONEY, 50.0, ResourceType.ALUMINUM, 0.2)
    ), Map.of(
            MilitaryUnit.AIRCRAFT, Map.of(ResourceType.MONEY, 15.0)
    ), Map.of(
            MilitaryUnit.AIRCRAFT, Map.of(ResourceType.MONEY, 10.0)
    )),
    // Decrease ship cost by $500 and 500 Steel, Reduce ship upkeep cost by $30 at peace, and $50 at war
    NAVAL_COST(ResearchGroup.NAVAL, null, Map.of(
            MilitaryUnit.SHIP, Map.of(ResourceType.MONEY, 500.0, ResourceType.STEEL, 500.0)
    ), Map.of(
            MilitaryUnit.SHIP, Map.of(ResourceType.MONEY, 30.0)
    ), Map.of(
            MilitaryUnit.SHIP, Map.of(ResourceType.MONEY, 50.0)
    )),
    // Increase max soldier count by 3000 and max tank count by 250
    GROUND_CAPACITY(ResearchGroup.GROUND, Map.of(MilitaryUnit.SOLDIER, 3000, MilitaryUnit.TANK, 250), null, null, null),
    // Increase max plane count by 15
    AIR_CAPACITY(ResearchGroup.AIR, Map.of(MilitaryUnit.AIRCRAFT, 15), null, null, null),
    // Increase max ship count by 5
    NAVAL_CAPACITY(ResearchGroup.NAVAL, Map.of(MilitaryUnit.SHIP, 5), null, null, null),

    ;

    public static final Research[] values = values();
    public static final Function<Research, Integer> ZERO = r -> 0;

    private final ResearchGroup group;
    private final Map<MilitaryUnit, Integer> capacityIncrease;
    private final Map<MilitaryUnit, Map<ResourceType, Double>> costDecrease;
    private final Map<MilitaryUnit, Map<ResourceType, Double>> upkeepPeaceDecrease;
    private final Map<MilitaryUnit, Map<ResourceType, Double>> upkeepWarDecrease;

    // 1 Food per +10	1 Food per +15

    Research(ResearchGroup group,
             Map<MilitaryUnit, Integer> capacityIncrease,
             Map<MilitaryUnit, Map<ResourceType, Double>> costDecrease,
             Map<MilitaryUnit, Map<ResourceType, Double>> upkeepPeaceDecrease,
                Map<MilitaryUnit, Map<ResourceType, Double>> upkeepWarDecrease) {
        this.group = group;
        this.capacityIncrease = capacityIncrease;
        this.costDecrease = costDecrease;
        this.upkeepPeaceDecrease = upkeepPeaceDecrease;
        this.upkeepWarDecrease = upkeepWarDecrease;

        if (costDecrease != null) {
            for (Map.Entry<MilitaryUnit, Map<ResourceType, Double>> entry : costDecrease.entrySet()) {
                double[] costDecreaseArr = ResourceType.resourcesToArray(entry.getValue());
                MilitaryUnit unit = entry.getKey();

                TriConsumer<Integer, Integer, double[]> upkeepWar = applyUpkeepReduction(unit, true);
                TriConsumer<Integer, Integer, double[]> upkeepPeace = applyUpkeepReduction(unit, false);
                Int2DoubleFunction upkeepWarConverted = applyUpkeepReductionConverted(unit, true);
                Int2DoubleFunction upkeepPeaceConverted = applyUpkeepReductionConverted(unit, false);

                unit.setResearch(this, costDecreaseArr, upkeepWar, upkeepPeace, upkeepWarConverted, upkeepPeaceConverted);
            }
        }
    }

    protected TriConsumer<Integer, Integer, double[]> applyUpkeepReduction(MilitaryUnit unit, boolean war) {
        Map<ResourceType, Double> upkeep = war ? upkeepWarDecrease.get(unit) : upkeepPeaceDecrease.get(unit);
        if (upkeep == null) return null;

        double[] upkeepArr = ResourceType.resourcesToArray(upkeep);
        ResourceType[] resources = upkeep.keySet().toArray(new ResourceType[0]);
        return (level, amt,arr) -> {
            for (ResourceType resource : resources) {
                int index = resource.ordinal();
                arr[index] -= upkeepArr[index] * level * amt;
            }
        };
    }

    protected Int2DoubleFunction applyUpkeepReductionConverted(MilitaryUnit unit, boolean war) {
        Map<ResourceType, Double> upkeep = war ? upkeepWarDecrease.get(unit) : upkeepPeaceDecrease.get(unit);
        if (upkeep == null) return null;
        double[] upkeepArr = ResourceType.resourcesToArray(upkeep);

        Supplier<Double> converted = ResourceType.convertedCostLazy(upkeepArr);
        return level -> {
            return -(converted.get() * level);
        };
    }

    public static double costFactor(boolean militaryDoctrine) {
         return militaryDoctrine ? 0.95 : 1;
    }

    public int getLevel(int researchBits) {
        return (researchBits >> (ordinal() * 5)) & 0b11111;
    }

    @Command
    public ResearchGroup getGroup() {
        return group;
    }

    @Command
    public Map<ResourceType, Double> getCost(int treeUpgrades, int totalUpgrades, int startLevel, int endLevel, double factor) {
        if (endLevel > 20) throw new IllegalArgumentException("End level cannot be greater than 20");
        if (startLevel < 0) throw new IllegalArgumentException("Start level cannot be less than 0");

        int numUpgrades = endLevel - startLevel;
        double[] cost = ResourceType.getBuffer();

        for (int i = 1; i < numUpgrades + 1; i++) {
            cost[ResourceType.MONEY.ordinal()] += (600_000 * (totalUpgrades + i) + (45_000 * Math.pow((totalUpgrades + i), 1.75) * (totalUpgrades + i) / 20d)) * factor;

            int treeCost = (int) (100 * (treeUpgrades + i) +
                    (Math.round((treeUpgrades + i) / 5d) * 500) +
                    (Math.round((treeUpgrades + i) / 10d) * 1000) +
                    (Math.round((treeUpgrades + i) / 20d) * 2000));
            cost[ResourceType.GASOLINE.ordinal()] += treeCost * factor;
            cost[ResourceType.MUNITIONS.ordinal()] += treeCost * factor;
            cost[ResourceType.STEEL.ordinal()] += treeCost * factor;

            int aluTreeCost = (int) (400 * (treeUpgrades + i) +
                    (Math.round((treeUpgrades + i) / 5d) * 1000) +
                    (Math.round((treeUpgrades + i) / 10d) * 2000) +
                    (Math.round((treeUpgrades + i) / 20d) * 4000));

            cost[ResourceType.ALUMINUM.ordinal()] += aluTreeCost * factor;

            // Individual cost
            cost[ResourceType.FOOD.ordinal()] += (startLevel + i) * 10000 * factor;
        }
        return ResourceType.resourcesToMap(cost);
    }

    public static void main(String[] args) {

    }

    @Command(desc = "Name of research")
    public String getName() {
        return name();
    }

    public static Map<Research, Integer> parseMap(String input) {
        return PW.parseEnumMap(input, Research.class, Integer.class);
    }

    public static Research parse(String input) {
        return valueOf(input.toUpperCase().replace(' ', '_'));
    }

    public static Map<Research, Integer> fromBits(int bits) {
        Map<Research, Integer> levels = new EnumMap<>(Research.class);
        for (Research r : values) {
            int level = r.getLevel(bits);
            if (level > 0) {
                levels.put(r, level);
            }
        }
        return levels;
    }

    public static Map<Research, Integer> parseResearch(Document doc) {
        Elements tr = doc.select("tr:contains(Military Research)");

        Map<Research, Integer> values = new EnumMap<>(Research.class);

        if (!tr.isEmpty()) {
            Elements td = tr.select("td");
            if (td.size() > 1) {
                List<String> elems = Arrays.asList(td.get(1).select("i").html()
                                .split("<br>"))
                        .stream().map(s -> s.replaceAll("[^0-9]", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
                List<String> names = Arrays.asList(td.get(0).select("i").html()
                                .split("<br>"))
                        .stream().map(s -> s.replaceAll("[^a-zA-Z ]", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();

                for (int i = 0; i < elems.size(); i++) {
                    Research research = Research.parse(names.get(i));
                    int value = Integer.parseInt(elems.get(i));
                    if (value > 0) {
                        values.put(research, value);
                    }
                }
            }
        }
        return values;
    }

    public static Map<ResourceType, Double> cost(Map<Research, Integer> end_level) {
        return cost(Collections.emptyMap(), end_level, 1);
    }

    public static Map<ResourceType, Double> cost(Map<Research, Integer> start_level, Map<Research, Integer> end_level, double factor) {
        Map<ResourceType, Double> cost = new EnumMap<>(ResourceType.class);

        int totalUpgrades = start_level.values().stream().mapToInt(Integer::intValue).sum();
        Map<ResearchGroup, Integer> byGroup = start_level.entrySet().stream().collect(
                Collectors.groupingBy(e -> e.getKey().getGroup(), Collectors.summingInt(Map.Entry::getValue)));

        for (Map.Entry<Research, Integer> entry : end_level.entrySet()) {
            Research r = entry.getKey();
            int startValue = start_level.getOrDefault(entry.getKey(), 0);
            int endValue = entry.getValue();
            if (startValue == endValue) continue;
            if (startValue > endValue) throw new IllegalArgumentException("Cannot decrease research level");

            int treeUpgrades = byGroup.getOrDefault(r.getGroup(), 0);

            Map<ResourceType, Double> addCost = r.getCost(treeUpgrades, totalUpgrades, startValue, endValue, factor);
            cost = ResourceType.add(cost, addCost);

            totalUpgrades += endValue - startValue;
            byGroup.put(r.getGroup(), treeUpgrades + endValue - startValue);
        }
        return cost;
    }

    public static int toBits(MilitaryResearch apiResearch) {
        int bits = 0;
        bits += apiResearch.getGround_capacity() << (GROUND_CAPACITY.ordinal() * 5);
        bits += apiResearch.getGround_cost() << (GROUND_COST.ordinal() * 5);
        bits += apiResearch.getAir_capacity() << (AIR_CAPACITY.ordinal() * 5);
        bits += apiResearch.getAir_cost() << (AIR_COST.ordinal() * 5);
        bits += apiResearch.getNaval_capacity() << (NAVAL_CAPACITY.ordinal() * 5);
        bits += apiResearch.getNaval_cost() << (NAVAL_COST.ordinal() * 5);
        return bits;
    }

    public static int toBits(Map<Research, Integer> levels) {
        int bits = 0;
        for (Map.Entry<Research, Integer> entry : levels.entrySet()) {
            bits |= entry.getValue() << (entry.getKey().ordinal() * 5);
        }
        return bits;
    }
}