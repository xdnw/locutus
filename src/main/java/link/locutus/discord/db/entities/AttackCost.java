package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AttackCost {
    private final String nameB;
    private final String nameA;

    private final double[] loot1 = ResourceType.getBuffer();
    private final double[] loot2 = ResourceType.getBuffer();

    private final double[] total1 = ResourceType.getBuffer();
    private final double[] total2 = ResourceType.getBuffer();

    private double infrn1 = 0;
    private double infrn2 = 0;

    private final int[] unit1 = new int[MilitaryUnit.values.length];
    private final int[] unit2 = new int[MilitaryUnit.values.length];

    private final double[] consumption1 = ResourceType.getBuffer();
    private final double[] consumption2 = ResourceType.getBuffer();

    private final int[] buildings1;// = new Object2IntOpenHashMap<>();
    private final int[] buildings2;// = new Object2IntOpenHashMap<>();

    private final Set<Integer> ids1;// = new IntOpenHashSet();
    private final Set<Integer> ids2;// = new IntOpenHashSet();

    private final Set<Integer> victories1;// = new IntOpenHashSet();
    private final Set<Integer> victories2;// = new IntOpenHashSet();
    private final Set<Integer> wars;// = new IntOpenHashSet();
    private final Set<AbstractCursor> attacks;// = new ObjectOpenHashSet<>();
    private final Set<AbstractCursor> primaryAttacks;// = new ObjectOpenHashSet<>();
    private final Set<AbstractCursor> secondaryAttacks;// = new ObjectOpenHashSet<>();


//    public AttackCost() {
//        this(null, null, true, true, true, true, true);
//    }

    public AttackCost(String nameA, String nameB, boolean buildings, boolean ids, boolean victories, boolean wars, boolean attacks) {
        this.nameA = nameA;
        this.nameB = nameB;
        if (buildings) {
            buildings1 = Buildings.getBuffer();
            buildings2 = Buildings.getBuffer();
        } else {
            buildings1 = null;
            buildings2 = null;
        }
        if (ids) {
            ids1 = new IntOpenHashSet();
            ids2 = new IntOpenHashSet();
        } else {
            ids1 = null;
            ids2 = null;
        }
        if (victories) {
            victories1 = new IntOpenHashSet();
            victories2 = new IntOpenHashSet();
        } else {
            victories1 = null;
            victories2 = null;
        }
        if (wars) {
            this.wars = new IntOpenHashSet();
        } else {
            this.wars = null;
        }
        if (attacks) {
            this.attacks = new ObjectOpenHashSet<>();
            this.primaryAttacks = new ObjectOpenHashSet<>();
            this.secondaryAttacks = new ObjectOpenHashSet<>();
        } else {
            this.attacks = null;
            this.primaryAttacks = null;
            this.secondaryAttacks = null;
        }
    }

    public Map<MilitaryUnit, Integer> getUnitsLost(boolean isPrimary) {
        return ArrayUtil.toMap(isPrimary ? unit1 : unit2, MilitaryUnit.values);
    }

    public Map<ResourceType, Double> getLoot(boolean isPrimary) {
        return PnwUtil.resourcesToMap(isPrimary ? loot1 : loot2);
    }

    public Map<ResourceType, Double> getTotal(boolean isPrimary, boolean units, boolean infra, boolean consumption, boolean loot) {
        Map<ResourceType, Double> result = new HashMap<>();
        if (units) result = PnwUtil.add(result, getUnitCost(isPrimary));
        if (infra) result = PnwUtil.add(result, Collections.singletonMap(ResourceType.MONEY, getInfraLost(isPrimary)));
        if (consumption) result = PnwUtil.add(result, getConsumption(isPrimary));
        if (loot) result = PnwUtil.add(result, getLoot(isPrimary));
        return result;
    }

    public Map<ResourceType, Double> getTotal(boolean isPrimary) {
        return PnwUtil.resourcesToMap(isPrimary ? total1 : total2);
    }

    private Map<ResourceType, Double> getNetCost(Map<ResourceType, Double> map1, Map<ResourceType, Double> map2) {
//        Map<ResourceType, Double> map1 = isPrimary ? total1 : total2;
//        Map<ResourceType, Double> map2 = !isPrimary ? total1 : total2;
        HashMap<ResourceType, Double> result = new HashMap<>();
        for (ResourceType type : ResourceType.values) {
            double value = map1.getOrDefault(type, 0d) - map2.getOrDefault(type, 0d);
            if (value != 0) result.put(type, value);
        }
        return result;
    }

    public Map<ResourceType, Double> getNetCost(boolean isPrimary) {
        Map<ResourceType, Double> map1 = getTotal(isPrimary);
        Map<ResourceType, Double> map2 = getTotal(!isPrimary);
//        PnwUtil.subResourcesToA(map1, getLoot(isPrimary));
        return getNetCost(map1, map2);
    }

    public Map<ResourceType, Double> getNetUnitCost(boolean isPrimary) {
        return getNetCost(getUnitCost(isPrimary), getUnitCost(!isPrimary));
    }

    public Map<ResourceType, Double> getNetConsumptionCost(boolean isPrimary) {
        return getNetCost(getConsumption(isPrimary), getConsumption(!isPrimary));
    }

    public Map<ResourceType, Double> getNetInfraCost(boolean isPrimary) {
        Map<ResourceType, Double> cost1 = Collections.singletonMap(ResourceType.MONEY, getInfraLost(isPrimary));
        Map<ResourceType, Double> cost2 = Collections.singletonMap(ResourceType.MONEY, getInfraLost(!isPrimary));
        return getNetCost(cost1, cost2);
    }

    public double convertedTotal(boolean isPrimary) {
        return PnwUtil.convertedTotal(getTotal(isPrimary));
    }

    public double getInfraLost(boolean isPrimary) {
        return isPrimary ? infrn1 : infrn2;
    }

    public Map<ResourceType, Double> getConsumption(boolean isPrimary) {
        return PnwUtil.resourcesToMap(isPrimary ? consumption1 : consumption2);
    }

    public Map<ResourceType, Double> getUnitCost(boolean isPrimary) {
        Map<MilitaryUnit, Integer> units = getUnitsLost(isPrimary);
        Map<ResourceType, Double> unitCost = new HashMap<>();

        for (Map.Entry<MilitaryUnit, Integer> unitAmt : units.entrySet()) {
            MilitaryUnit unit = unitAmt.getKey();
            int amt = unitAmt.getValue();
            if (amt > 0) {
                double[] cost = unit.getCost(amt);
                for (int i = 0; i < cost.length; i++) {
                    unitCost.put(ResourceType.values[i], unitCost.getOrDefault(ResourceType.values[i], 0d) + cost[i]);
                }
            }
        }

        return unitCost;
    }

    public Set<Integer> getIds(boolean isPrimary) {
        if (ids1 == null) return Collections.emptySet();
        return isPrimary ? ids1 : ids2;
    }

    public Set<Integer> getWarIds() {
        if (wars == null) return Collections.emptySet();
        return wars;
    }

    public int getNumWars() {
        return wars == null ? 0 : wars.size();
    }

    public Set<AbstractCursor> getAttacks() {
        if (attacks == null) return Collections.emptySet();
        return attacks;
    }

    public Set<AbstractCursor> getAttacks(boolean isPrimary) {
        if (attacks == null) return Collections.emptySet();
        return isPrimary ? primaryAttacks : secondaryAttacks;
    }

    public Set<Integer> getVictories(boolean attacker) {
        if (victories1 == null) return Collections.emptySet();
        return attacker ? victories1 : victories2;
    }

    public void addCost(Collection<AbstractCursor> attacks, Function<AbstractCursor, Boolean> isPrimary, Function<AbstractCursor, Boolean> isSecondary) {
        for (AbstractCursor attack : attacks) {
            addCost(attack, isPrimary, isSecondary);
        }
    }

    public void addCost(AbstractCursor attack, boolean isAttacker) {
        addCost(attack, p -> isAttacker, p -> !isAttacker);
    }

    public void addCost(AbstractCursor attack, Function<AbstractCursor, Boolean> isPrimary, Function<AbstractCursor, Boolean> isSecondary) {
        boolean primary = isPrimary.apply(attack);
        boolean secondary = isSecondary.apply(attack);

        if (primary || secondary) {
            if (victories1 != null && attack.getAttack_type() == AttackType.VICTORY) {
                if (primary) victories1.add(attack.getWar_id());
                else victories2.add(attack.getWar_id());
            }
            if (this.attacks != null) {
                this.attacks.add(attack);
                if (primary) primaryAttacks.add(attack);
                if (secondary) secondaryAttacks.add(attack);
            }

            if (this.wars != null) {
                wars.add(attack.getWar_id());
            }
            double attInfra = 0;
            double defInfra = attack.getInfra_destroyed_value();

            if (primary) {
                if (ids1 != null) {
                    ids1.add(attack.getAttacker_id());
                    ids2.add(attack.getDefender_id());
                }
                attack.getUnitLosses(unit1, true);
                attack.getUnitLosses(unit2, false);
                attack.getLosses(loot1, true, false, false, false, true, false);
                attack.getLosses(loot2, false, false, false, false, true, false);
                attack.getLosses(consumption1, true, false, false, true, false, false);
                attack.getLosses(consumption2, false, false, false, true, false, false);
                attack.getLosses(total1, true, true, true, true, true, true);
                attack.getLosses(total2, false, true, true, true, true, true);
                infrn1 += attInfra;
                infrn2 += defInfra;
                if (buildings2 != null) attack.addBuildingsDestroyed(buildings2);
            } else if (secondary) {
                if (ids1 != null) {
                    ids2.add(attack.getAttacker_id());
                    ids1.add(attack.getDefender_id());
                }
                attack.getUnitLosses(unit1, false);
                attack.getUnitLosses(unit2, true);
                attack.getLosses(loot1, false, false, false, false, true, false);
                attack.getLosses(loot2, true, false, false, false, true, false);
                attack.getLosses(consumption1, false, false, false, true, false, false);
                attack.getLosses(consumption2, true, false, false, true, false, false);
                attack.getLosses(total1, false, true, true, true, true, true);
                attack.getLosses(total2, true, true, true, true, true, true);
                infrn1 += defInfra;
                infrn2 += attInfra;
                if (buildings1 != null) attack.addBuildingsDestroyed(buildings1);
            }
        }
    }

    @Override
    public String toString() {
        return toString(true, true, true, true, true);
    }

    public String toString(boolean units, boolean infra, boolean consumption, boolean loot, boolean buildings) {
        StringBuilder response = new StringBuilder();
        response.append("**" + nameA + " losses:** (" + getIds(true).size() + " nations)\n");
        Map<ResourceType, Double> totalA = new HashMap<>();

        if (units) {
            response.append("Units: ```" + StringMan.getString(getUnitsLost(true))).append("```");
            totalA = PnwUtil.add(totalA, getUnitCost(true));
        }
        if (infra) {
            response.append("Infra: `$" + MathMan.format(getInfraLost(true))).append("`\n");
            totalA = PnwUtil.add(totalA, Collections.singletonMap(ResourceType.MONEY, getInfraLost(true)));
        }
        if (consumption) {
            response.append("Consumption: ```" + PnwUtil.resourcesToString(getConsumption(true))).append("```");
            totalA = PnwUtil.add(totalA, getConsumption(true));
        }
        if (loot) {
            response.append("Loot: ```" + PnwUtil.resourcesToString(getLoot(true))).append("```");
            totalA = PnwUtil.add(totalA, getLoot(true));
        }
        if (buildings) {
            response.append("Buildings: ```" + StringMan.getString(getBuildingsDestroyed(true))).append("```");
        }
        response.append("Total: ```" + PnwUtil.resourcesToString(totalA)).append("```");
        response.append("Converted Total: `$" + MathMan.format(PnwUtil.convertedTotal(totalA))).append("`\n\n");


        response.append("**" + nameB + " losses:** (" + getIds(false).size() + " nations)\n");
        Map<ResourceType, Double> totalB = new HashMap<>();
        if (units) {
            response.append("Units: ```" + StringMan.getString(getUnitsLost(false))).append("```");
            totalB = PnwUtil.add(totalB, getUnitCost(false));
        }
        if (infra) {
            response.append("Infra: `$" + MathMan.format(getInfraLost(false))).append("`\n");
            totalB = PnwUtil.add(totalB, Collections.singletonMap(ResourceType.MONEY, getInfraLost(false)));
        }
        if (consumption) {
            response.append("Consumption: ```" + PnwUtil.resourcesToString(getConsumption(false))).append("```");
            totalB = PnwUtil.add(totalB, getConsumption(false));
        }
        if (loot) {
            response.append("Loot: ```" + PnwUtil.resourcesToString(getLoot(false))).append("```");
            totalB = PnwUtil.add(totalB, getLoot(false));
        }
        if (buildings) {
            response.append("Buildings: ```" + StringMan.getString(getBuildingsDestroyed(false))).append("```");
        }
        response.append("Total: ```" + PnwUtil.resourcesToString(totalB)).append("```");
        response.append("Converted Total: `$" + MathMan.format(PnwUtil.convertedTotal(totalB))).append("`\n");

        return response.toString();
    }

    private Map<Building, Integer> getBuildingsDestroyed(boolean isAttacker) {
        if (isAttacker) {
            return buildings1 == null ? Collections.emptyMap() : ArrayUtil.toMap(buildings1, Buildings.values());
        } else {
            return buildings2 == null ? Collections.emptyMap() : ArrayUtil.toMap(buildings2, Buildings.values());
        }
    }
}
