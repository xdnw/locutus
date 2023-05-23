package link.locutus.discord.db.entities;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class AttackCost {
    private final String nameB;
    private final String nameA;

    private Map<ResourceType, Double> loot1 = new HashMap<>();
    private Map<ResourceType, Double> loot2 = new HashMap<>();

    private Map<ResourceType, Double> total1 = new HashMap<>();
    private Map<ResourceType, Double> total2 = new HashMap<>();

    private double infrn1 = 0;
    private double infrn2 = 0;

    private Map<MilitaryUnit, Integer> unit1 = new HashMap<>();
    private Map<MilitaryUnit, Integer> unit2 = new HashMap<>();

    private Map<ResourceType, Double> consumption1 = new HashMap<>();
    private Map<ResourceType, Double> consumption2 = new HashMap<>();

    private Set<Integer> ids1 = new LinkedHashSet<>();
    private Set<Integer> ids2 = new LinkedHashSet<>();

    private Set<Integer> victories1 = new LinkedHashSet<>();
    private Set<Integer> victories2 = new LinkedHashSet<>();

//    private boolean profit = false;
    private Set<Integer> wars = new LinkedHashSet<>();
    private Set<DBAttack> attacks = new LinkedHashSet<>();
    private Set<DBAttack> primaryAttacks = new LinkedHashSet<>();
    private Set<DBAttack> secondaryAttacks = new LinkedHashSet<>();


    public AttackCost() {
        nameA = "";
        nameB = "";
    }

    public AttackCost(String nameA, String nameB) {
        this.nameA = nameA;
        this.nameB = nameB;
    }

    public Map<MilitaryUnit, Integer> getUnitsLost(boolean isPrimary) {
        return isPrimary ? unit1 : unit2;
    }

    public Map<ResourceType, Double> getLoot(boolean isPrimary) {
        return isPrimary ? loot1 : loot2;
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
        return isPrimary ? total1 : total2;
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
        return isPrimary ? consumption1 : consumption2;
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
        return isPrimary ? ids1 : ids2;
    }

    public Set<Integer> getWarIds() {
        return wars;
    }

    public int getNumWars() {
        return wars.size();
    }

    public Set<DBAttack> getAttacks() {
        return attacks;
    }

    public Set<DBAttack> getAttacks(boolean isPrimary) {
        return isPrimary ? primaryAttacks : secondaryAttacks;
    }

    public Set<Integer> getVictories(boolean attacker) {
        return attacker ? victories1 : victories2;
    }

    public void addCost(Collection<DBAttack> attacks, Function<DBAttack, Boolean> isPrimary, Function<DBAttack, Boolean> isSecondary) {
        for (DBAttack attack : attacks) {
            addCost(attack, isPrimary, isSecondary);
        }
    }

    public void addCost(DBAttack attack, boolean isAttacker) {
        addCost(attack, p -> isAttacker, p -> !isAttacker);
    }

    public void addCost(DBAttack attack, Function<DBAttack, Boolean> isPrimary, Function<DBAttack, Boolean> isSecondary) {
        boolean primary = isPrimary.apply(attack);
        boolean secondary = isSecondary.apply(attack);

        if (primary || secondary) {
            if (attack.getAttack_type() == AttackType.VICTORY) {
                if (primary) victories1.add(attack.getWar_id());
                else victories2.add(attack.getWar_id());
            }
            this.attacks.add(attack);
            if (primary) primaryAttacks.add(attack);
            if (secondary) secondaryAttacks.add(attack);

            wars.add(attack.getWar_id());
            Map<MilitaryUnit, Integer> attUnit = attack.getUnitLosses(true);
            Map<MilitaryUnit, Integer> defUnit = attack.getUnitLosses(false);

            Map<ResourceType, Double> attLoot = attack.getLosses(true, false, false, false, true);
            Map<ResourceType, Double> defLoot = attack.getLosses(false, false, false, false, true);

            Map<ResourceType, Double> attConsume = attack.getLosses(true, false, false, true, false);
            Map<ResourceType, Double> defConsume = attack.getLosses(false, false, false, true, false);

            double attInfra = PnwUtil.convertedTotal(attack.getLosses(true, false, true, false, false));
            double defInfra = PnwUtil.convertedTotal(attack.getLosses(false, false, true, false, false));

            Map<ResourceType, Double> attTotal = attack.getLosses(true, true, true, true, true);
            Map<ResourceType, Double> defTotal = attack.getLosses(false, true, true, true, true);

            if (primary) {
                ids1.add(attack.getAttacker_nation_id());
                ids2.add(attack.getDefender_nation_id());
                unit1 = PnwUtil.add(unit1, attUnit);
                unit2 = PnwUtil.add(unit2, defUnit);
                loot1 = PnwUtil.addResourcesToA(loot1, attLoot);
                loot2 = PnwUtil.addResourcesToA(loot2, defLoot);
                consumption1 = PnwUtil.addResourcesToA(consumption1, attConsume);
                consumption2 = PnwUtil.addResourcesToA(consumption2, defConsume);
                total1 = PnwUtil.addResourcesToA(total1, attTotal);
                total2 = PnwUtil.addResourcesToA(total2, defTotal);
                infrn1 += attInfra;
                infrn2 += defInfra;
            } else if (secondary) {
                ids2.add(attack.getAttacker_nation_id());
                ids1.add(attack.getDefender_nation_id());
                unit1 = PnwUtil.add(unit1, defUnit);
                unit2 = PnwUtil.add(unit2, attUnit);
                loot1 = PnwUtil.addResourcesToA(loot1, defLoot);
                loot2 = PnwUtil.addResourcesToA(loot2, attLoot);
                consumption1 = PnwUtil.addResourcesToA(consumption1, defConsume);
                consumption2 = PnwUtil.addResourcesToA(consumption2, attConsume);
                total1 = PnwUtil.addResourcesToA(total1, defTotal);
                total2 = PnwUtil.addResourcesToA(total2, attTotal);
                infrn1 += defInfra;
                infrn2 += attInfra;
            }
        }
    }

    @Override
    public String toString() {
        return toString(true, true, true, true);
    }

    public String toString(boolean units, boolean infra, boolean consumption, boolean loot) {
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
        response.append("Total: ```" + PnwUtil.resourcesToString(totalB)).append("```");
        response.append("Converted Total: `$" + MathMan.format(PnwUtil.convertedTotal(totalB))).append("`\n");

        return response.toString();
    }
}
