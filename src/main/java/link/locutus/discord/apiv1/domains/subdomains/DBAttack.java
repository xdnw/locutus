package link.locutus.discord.apiv1.domains.subdomains;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import com.google.common.collect.BiMap;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static link.locutus.discord.util.TimeUtil.YYYY_MM_DD_HH_MM_SS;

public class DBAttack {
    public int war_attack_id;
    public long epoch;
    public int war_id;
    public int attacker_nation_id;
    public int defender_nation_id;
    public AttackType attack_type;
    public int victor;
    public int success;
    public int attcas1;
    public int attcas2;
    public int defcas1;
    public int defcas2;
    public int defcas3;
    public int city_id;
    public double infra_destroyed;
    public int improvements_destroyed;
    public double money_looted;
    public double[] loot;
    public int looted;
    public double lootPercent;
    public double city_infra_before;
    public double infra_destroyed_value;
    public double att_gas_used;
    public double att_mun_used;
    public double def_gas_used;
    public double def_mun_used;

    public DBAttack() {}

    public DBAttack(int war_attack_id, long epoch, int war_id, int attacker_nation_id, int defender_nation_id, AttackType attack_type, int victor, int success, int attcas1, int attcas2, int defcas1, int defcas2, int defcas3, int city_id,
                    double infra_destroyed, int improvements_destroyed, double money_looted, String note, double city_infra_before, double infra_destroyed_value, double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
        this.war_attack_id = war_attack_id;
        this.epoch = epoch;
        this.war_id = war_id;
        this.attacker_nation_id = attacker_nation_id;
        this.defender_nation_id = defender_nation_id;
        this.attack_type = attack_type;
        this.victor = victor;
        this.success = success;
        this.attcas1 = attcas1;
        this.attcas2 = attcas2;
        this.defcas1 = defcas1;
        this.defcas2 = defcas2;
        this.defcas3 = defcas3;
        this.city_id = city_id;
        this.infra_destroyed = infra_destroyed;
        this.improvements_destroyed = improvements_destroyed;
        this.money_looted = money_looted;

        if (note != null) {
            switch (attack_type) {
                case VICTORY:
                case A_LOOT:
                    loot = parseLootLegacy(note);
                    break;
            }
        }

        this.city_infra_before = city_infra_before;
        this.infra_destroyed_value = infra_destroyed_value;
        this.att_gas_used = att_gas_used;
        this.att_mun_used = att_mun_used;
        this.def_gas_used = def_gas_used;
        this.def_mun_used = def_mun_used;
    }

    public Map<ResourceType, Double> getLoot() {
        if (loot == null) {
            if (money_looted != 0) {
                return Collections.singletonMap(ResourceType.MONEY, money_looted);
            }
            return Collections.emptyMap();
        }
        return PnwUtil.resourcesToMap(loot);
    }

    public int getLoser() {
        return victor == defender_nation_id ? attacker_nation_id : victor == attacker_nation_id ? defender_nation_id : 0;
    }

    public double[] parseLootLegacy(String note) {
        if (attack_type == AttackType.A_LOOT) {
            loot = new double[ResourceType.values.length];
            AtomicInteger allianceId = new AtomicInteger();
            lootPercent = parseBankLoot(note, allianceId, loot);
            looted = allianceId.get();

            String looterStr = note.split(" looted [0-9]+\\.[0-9]+% of ")[0];
            for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
                if (entry.getValue().getNation().equals(looterStr)) {
                    victor = entry.getValue().getNation_id();
                    break;
                }
            }
        } else if (attack_type == AttackType.VICTORY) {
            loot = new double[ResourceType.values.length];
            loot = parseNationLoot(note, loot);
            looted = victor == attacker_nation_id ? defender_nation_id : attacker_nation_id;
            lootPercent = 0.1;
        }
        if (loot != null) {
            optimizeLoot();
        }
        return loot;
    }

    public boolean optimizeLoot() {
        if (loot == null) return false;
        boolean hasLoot = false;
        boolean hasNonMoney = false;
        for (int i = 0; i < loot.length; i++) {
            double val = loot[i];
            if (val > 0) {
                hasLoot = true;
                if (i > 0) {
                    hasNonMoney = true;
                    break;
                }
            }
        }
        if (!hasLoot) {
            loot = null;
            return true;
        } else if (!hasNonMoney) {
            money_looted = loot[0];
            loot = null;
            return true;
        }
        return false;
    }

    public String toUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + war_id;
    }

    public Integer getLooted() {
        return looted;
    }

    public Integer getLooter() {
        return victor;
    }

    public Double getLootPercent() {
        return lootPercent;
    }

    private static double parseBankLoot(String input, AtomicInteger allianceIdOutput, double[] resourceOutput) {
        String[] split = input.split(" looted [0-9]+\\.[0-9]+% of ", 2);
        String attacker = split[0];

        split = split[1].split("'s alliance bank, taking: \\$", 2);
        String bank = split[0];

        double[] rss = parseRss(split[1], resourceOutput);

        Matcher matcher = PERCENT_PATTERN.matcher(input);
        matcher.matches();
        matcher.groupCount();
        matcher.find();
        double percent = Math.max(0.01, Double.parseDouble(matcher.group(1))) / 100d;

        BiMap<String, Integer> alliances = Locutus.imp().getNationDB().getAlliances().inverse();
        allianceIdOutput.set(alliances.getOrDefault(bank, 0));;
        return percent;
    }

    private static double[] parseNationLoot(String input, double[] resourceOutput) {
        String[] split = input.split(" won the war and looted ", 2);
        String attacker = split[0];

        return parseRss(split[1], resourceOutput);
    }

    private static final Pattern RSS_PATTERN;
    private static final Pattern PERCENT_PATTERN;


    static {
        String regex = "([0-9|,]+) ([0-9|,]+) coal, ([0-9|,]+) oil,[ |\\r?\\n]+" +
                "([0-9|,]+) uranium, ([0-9|,]+) iron, ([0-9|,]+) bauxite, ([0-9|,]+) lead, ([0-9|,]+)[ |\\r?\\n]+" +
                "gasoline, ([0-9|,]+) munitions, ([0-9|,]+) steel, ([0-9|,]+) aluminum, and[ |\\r?\\n]+" +
                "([0-9|,]+) food";
        RSS_PATTERN = Pattern.compile(regex);

        PERCENT_PATTERN = Pattern.compile("([0-9]+\\.[0-9]+)%");
    }

    public static synchronized double[] parseRss(String input, double[] resourceOutput) {
        if (resourceOutput == null) {
            resourceOutput = new double[ResourceType.values.length];
        }

        Matcher matcher = RSS_PATTERN.matcher(input.toLowerCase());
        matcher.matches();
        matcher.groupCount();
        matcher.find();
        String moneyStr;
        try {
            moneyStr = matcher.group(1);
        } catch (Throwable e) {
            e.printStackTrace();
//            throw e;
            return resourceOutput;
        }
        long money = MathMan.parseInt(moneyStr.substring(0, moneyStr.length() - 1));
        long coal = MathMan.parseInt(matcher.group(2));
        long oil = MathMan.parseInt(matcher.group(3));
        long uranium = MathMan.parseInt(matcher.group(4));
        long iron = MathMan.parseInt(matcher.group(5));
        long bauxite = MathMan.parseInt(matcher.group(6));
        long lead = MathMan.parseInt(matcher.group(7));
        long gasoline = MathMan.parseInt(matcher.group(8));
        long munitions = MathMan.parseInt(matcher.group(9));
        long steel = MathMan.parseInt(matcher.group(10));
        long aluminum = MathMan.parseInt(matcher.group(11));
        long food = MathMan.parseInt(matcher.group(12));

        resourceOutput[ResourceType.MONEY.ordinal()] = money;
        resourceOutput[ResourceType.COAL.ordinal()] = coal;
        resourceOutput[ResourceType.OIL.ordinal()] = oil;
        resourceOutput[ResourceType.URANIUM.ordinal()] = uranium;
        resourceOutput[ResourceType.IRON.ordinal()] = iron;
        resourceOutput[ResourceType.BAUXITE.ordinal()] = bauxite;
        resourceOutput[ResourceType.LEAD.ordinal()] = lead;
        resourceOutput[ResourceType.GASOLINE.ordinal()] = gasoline;
        resourceOutput[ResourceType.MUNITIONS.ordinal()] = munitions;
        resourceOutput[ResourceType.STEEL.ordinal()] = steel;
        resourceOutput[ResourceType.ALUMINUM.ordinal()] = aluminum;
        resourceOutput[ResourceType.FOOD.ordinal()] = food;
        for (int i = 0; i < resourceOutput.length; i++) {
            if (resourceOutput[i] < 0) resourceOutput[i] = 0;
        }

        return resourceOutput;
    }

    public DBAttack(WarAttacksContainer container) {
        this(Integer.parseInt(container.getWarAttackId()),
                TimeUtil.parseDate(YYYY_MM_DD_HH_MM_SS, container.getDate()),
                Integer.parseInt(container.getWarId()),
                Integer.parseInt(container.getAttackerNationId()),
                Integer.parseInt(container.getDefenderNationId()),
                        AttackType.get(container.getAttackType().toUpperCase()),
                        Integer.parseInt(container.getVictor()),
                        Integer.parseInt(container.getSuccess()),
                        Integer.parseInt(container.getAttcas1()),
                        Integer.parseInt(container.getAttcas2()),
                        Integer.parseInt(container.getDefcas1()),
                        Integer.parseInt(container.getDefcas2()),
                        (int) container.getAircraftKilledByTanks(),
                        Integer.parseInt(container.getCityId()),
                        MathMan.parseDoubleDef0(container.getInfraDestroyed()),
                        MathMan.parseIntDef0(container.getImprovementsDestroyed()),
                        MathMan.parseDoubleDef0(container.getMoneyLooted()),
                        container.getNote(),
                        MathMan.parseDoubleDef0(container.getCityInfraBefore()),
                        MathMan.parseDoubleDef0(container.getInfraDestroyedValue()),
                        MathMan.parseDoubleDef0(container.getAttGasUsed()),
                        MathMan.parseDoubleDef0(container.getAttMunUsed()),
                        MathMan.parseDoubleDef0(container.getDefGasUsed()),
                        MathMan.parseDoubleDef0(container.getDefMunUsed())
        );
    }

    public Map<MilitaryUnit, Integer> getUnitLosses(boolean attacker) {
        if (attack_type == AttackType.NUKE || attack_type == AttackType.MISSILE) {
            victor = attacker_nation_id;
            attcas1 = 1;
        }
        if (attcas1 == 0 && attcas2 == 0 && defcas1 == 0 && defcas2 == 0 && defcas3 == 0) return Collections.emptyMap();
        if (attacker) {
            return attack_type.getLosses(attcas1, attcas2, 0);
        } else {
            return attack_type.getLosses(defcas1, defcas2, defcas3);
        }
    }

    public double getLossesConverted(boolean attacker) {
        return PnwUtil.convertedTotal(getLosses(attacker));
    }

    public double getLossesConverted(boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot) {
        return PnwUtil.convertedTotal(getLosses(attacker, units, infra, consumption, includeLoot));
    }

    public Map<ResourceType, Double> getLosses(boolean attacker) {
        return getLosses(attacker, true, true, true, true);
    }

    private static double intOverflow = 2147483647 / 100d;

    public Map<ResourceType, Double> getLosses(boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot) {
        if ((attack_type == AttackType.NUKE || attack_type == AttackType.MISSILE) && success == 0) {
            infra_destroyed_value = 0;
        }
        Map<ResourceType, Double> losses = new HashMap<>();
        if (units) {
            Map<MilitaryUnit, Integer> unitLosses = getUnitLosses(attacker);
            for (Map.Entry<MilitaryUnit, Integer> entry : unitLosses.entrySet()) {
                MilitaryUnit unit = entry.getKey();
                losses.put(ResourceType.MONEY, losses.getOrDefault(ResourceType.MONEY, 0d) + unit.getCost() * entry.getValue());
                for (ResourceType rss : unit.getResources()) {
                    losses.put(rss, losses.getOrDefault(rss, 0d) + unit.getRssAmt(rss) * entry.getValue());
                }
            }
        }

        if (includeLoot) {
            if (victor != 0) {
                if (loot != null) {
                    Map<ResourceType, Double> lootDouble = PnwUtil.resourcesToMap(loot);
                    if (attacker ? victor == attacker_nation_id : victor == defender_nation_id) {
                        losses = PnwUtil.subResourcesToA(losses, lootDouble);
                    } else if (attacker ? victor == defender_nation_id : victor == attacker_nation_id) {
                        losses = PnwUtil.addResourcesToA(losses, lootDouble);
                    }
                }
                else if (money_looted != 0) {
                    int sign = (victor == (attacker ? attacker_nation_id : defender_nation_id)) ? -1 : 1;
                    losses.put(ResourceType.MONEY, losses.getOrDefault(ResourceType.MONEY, 0d) + money_looted * sign);
                }
            }
        }
        if ((attack_type == AttackType.NUKE || attack_type == AttackType.MISSILE) && success == 1) {
            victor = attacker_nation_id;
        }
        if (attacker ? victor == defender_nation_id : victor == attacker_nation_id) {
            if (infra && infra_destroyed_value != 0) {
                if (infra_destroyed_value == intOverflow) {
                    infra_destroyed_value = PnwUtil.calculateInfra(this.city_infra_before - infra_destroyed, this.city_infra_before);
                }
                losses.put(ResourceType.MONEY, (losses.getOrDefault(ResourceType.MONEY, 0d) + infra_destroyed_value));
            }
            if (includeLoot) {
            }
        }

        if (consumption) {
            Double mun = attacker ? att_mun_used : def_mun_used;
            Double gas = attacker ? att_gas_used : def_gas_used;
            if (mun != null) {
                losses.put(ResourceType.MUNITIONS, (losses.getOrDefault(ResourceType.MUNITIONS, 0d) + mun));
            }
            if (gas != null) {
                losses.put(ResourceType.GASOLINE, (losses.getOrDefault(ResourceType.GASOLINE, 0d) + gas));
            }
        }
        return losses;
    }

    public int getWar_id() {
        return war_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DBAttack attack = (DBAttack) o;

        return attack.war_attack_id == war_attack_id;
    }

    @Override
    public int hashCode() {
        return war_attack_id;
    }

    @Override
    public String toString() {
        return "DBAttack{" +
                "war_attack_id=" + war_attack_id +
                ", epoch=" + epoch +
                ", war_id=" + war_id +
                ", attacker_nation_id=" + attacker_nation_id +
                ", defender_nation_id=" + defender_nation_id +
                ", attack_type=" + attack_type +
                ", victor=" + victor +
                ", success=" + success +
                ", attcas1=" + attcas1 +
                ", attcas2=" + attcas2 +
                ", defcas1=" + defcas1 +
                ", defcas2=" + defcas2 +
                ", defcas3=" + defcas3 +
                ", city_id=" + city_id +
                ", infra_destroyed=" + infra_destroyed +
                ", improvements_destroyed=" + improvements_destroyed +
                ", money_looted=" + money_looted +
                ", loot=" + loot +
                ", lootPercent=" + lootPercent +
                ", looted=" + looted +
                ", city_infra_before=" + city_infra_before +
                ", infra_destroyed_value=" + infra_destroyed_value +
                ", att_gas_used=" + att_gas_used +
                ", att_mun_used=" + att_mun_used +
                ", def_gas_used=" + def_gas_used +
                ", def_mun_used=" + def_mun_used +
                '}';
    }
}
