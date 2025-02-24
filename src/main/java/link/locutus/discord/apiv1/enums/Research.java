package link.locutus.discord.apiv1.enums;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.util.Map;

public enum Research {
    // Increase max soldier count by 3000 and max tank count by 250
    GROUND_CAPACITY(ResearchGroup.GROUND, Map.of(MilitaryUnit.SOLDIER, 3000, MilitaryUnit.TANK, 250), null, null),
    // Decrease soldier cost by $0.1 and tank cost by $1 and 0.01 Steel, Reduce Soldier upkeep cost by $0.02 at peace, and $0.03 at war, Increase the number of soldiers each ton of food feeds by 10 at peace, and 15 at war. It also reduces Tank upkeep by $1 at peace, and $1.5 at war
    GROUND_COST(ResearchGroup.GROUND, null, Map.of(
            MilitaryUnit.SOLDIER, Map.of(ResourceType.MONEY, 0.1, ResourceType.STEEL, 0.01),
            MilitaryUnit.TANK, Map.of(ResourceType.MONEY, 1.0, ResourceType.STEEL, 0.01)
    ), Map.of(
            MilitaryUnit.SOLDIER, Map.of(ResourceType.MONEY, 0.02, ResourceType.FOOD, 10.0),
            MilitaryUnit.TANK, Map.of(ResourceType.MONEY, 1.0)
    )),
    // Increase max plane count by 15
    AIR_CAPACITY(ResearchGroup.AIR, Map.of(MilitaryUnit.AIRCRAFT, 15), null, null),
    // Decrease plane cost by $50 and 0.2 Aluminium, Reduce plane upkeep cost by $15 at peace, and $10 at war
    AIR_COST(ResearchGroup.AIR, null, Map.of(
            MilitaryUnit.AIRCRAFT, Map.of(ResourceType.MONEY, 50.0, ResourceType.ALUMINUM, 0.2)
    ), Map.of(
            MilitaryUnit.AIRCRAFT, Map.of(ResourceType.MONEY, 15.0)
    )),
    // Increase max ship count by 5
    NAVAL_CAPACITY(ResearchGroup.NAVAL, Map.of(MilitaryUnit.SHIP, 5), null, null),
    // Decrease ship cost by $500 and 500 Steel, Reduce ship upkeep cost by $30 at peace, and $50 at war
    NAVAL_COST(ResearchGroup.NAVAL, null, Map.of(
            MilitaryUnit.SHIP, Map.of(ResourceType.MONEY, 500.0, ResourceType.STEEL, 500.0)
    ), Map.of(
            MilitaryUnit.SHIP, Map.of(ResourceType.MONEY, 30.0)
    ));

    public static final Research[] values = values();

    private final ResearchGroup group;
    private final Map<MilitaryUnit, Integer> capacityIncrease;
    private final Map<MilitaryUnit, Map<ResourceType, Double>> costDecrease;
    private final Map<MilitaryUnit, Map<ResourceType, Double>> upkeepDecrease;

    Research(ResearchGroup group, Map<MilitaryUnit, Integer> capacityIncrease, Map<MilitaryUnit, Map<ResourceType, Double>> costDecrease, Map<MilitaryUnit, Map<ResourceType, Double>> upkeepDecrease) {
        this.group = group;
        this.capacityIncrease = capacityIncrease;
        this.costDecrease = costDecrease;
        this.upkeepDecrease = upkeepDecrease;
    }

    @Command
    public ResearchGroup getGroup() {
        return group;
    }

    @Command
    public Map<MilitaryUnit, Integer> getCapacity() {
        return capacityIncrease;
    }

    @Command
    public Map<MilitaryUnit, Map<ResourceType, Double>> getSavings() {
        return costDecrease;
    }

    @Command
    public Map<MilitaryUnit, Map<ResourceType, Double>> getUpkeep() {
        return upkeepDecrease;
    }

    @Command
    public Map<ResourceType, Double> getCost(int treeUpgrades, int totalUpgrades, int startLevel, int endLevel) {
        if (endLevel > 20) throw new IllegalArgumentException("End level cannot be greater than 20");
        if (startLevel < 0) throw new IllegalArgumentException("Start level cannot be less than 0");

        int numUpgrades = endLevel - startLevel;
        double[] cost = ResourceType.getBuffer();

        for (int i = 0; i < numUpgrades; i++) {
            cost[ResourceType.MONEY.ordinal()] += 1000000 * (totalUpgrades + i) + (75000 * Math.pow((totalUpgrades + i), 1.75) * (totalUpgrades + i) / 20);

            int treeCost = 100 * (treeUpgrades + i) + (Math.round((treeUpgrades + i) / 5) / 5) * 500 + (Math.round((treeUpgrades + i) / 10) / 10) * 1000 + (Math.round((treeUpgrades + i) / 20) / 20) * 1000;
            cost[ResourceType.GASOLINE.ordinal()] += treeCost;
            cost[ResourceType.MUNITIONS.ordinal()] += treeCost;
            cost[ResourceType.STEEL.ordinal()] += treeCost;
            cost[ResourceType.ALUMINUM.ordinal()] += 4 * treeCost;

            cost[ResourceType.FOOD.ordinal()] += (startLevel + i) * 10000;
        }
        return ResourceType.resourcesToMap(cost);
    }
}