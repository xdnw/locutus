package link.locutus.discord.apiv1.domains.subdomains.attack;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.WarAttacksContainer;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
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
    private int war_attack_id;
    private long date;
    private int war_id;
    private int attacker_nation_id;
    private int defender_nation_id;
    private AttackType attack_type;
    private int victor;
    private int success;
    private int attcas1;
    private int attcas2;
    private int defcas1;
    private int defcas2;
    private int defcas3;
    private double infra_destroyed;
    private int improvements_destroyed;
    private double money_looted;
    public double[] loot;
    private int looted;
    private double lootPercent;
    private double city_infra_before;
    private double infra_destroyed_value;
    private double att_gas_used;
    private double att_mun_used;
    private double def_gas_used;
    private double def_mun_used;

    public double infraPercent_cached;
    public int city_cached;

    public DBAttack() {}

    public DBAttack(int war_attack_id, long epoch, int war_id, int attacker_nation_id, int defender_nation_id, AttackType attack_type, int victor, int success, int attcas1, int attcas2, int defcas1, int defcas2, int defcas3,
                    double infra_destroyed, int improvements_destroyed, double money_looted, String note, double city_infra_before, double infra_destroyed_value, double att_gas_used, double att_mun_used, double def_gas_used, double def_mun_used) {
        this.setWar_attack_id(war_attack_id);
        this.setDate(epoch);
        this.setWar_id(war_id);
        this.setAttacker_nation_id(attacker_nation_id);
        this.setDefender_nation_id(defender_nation_id);
        this.setAttack_type(attack_type);
        this.setVictor(victor);
        this.setSuccess(success);
        this.setAttcas1(attcas1);
        this.setAttcas2(attcas2);
        this.setDefcas1(defcas1);
        this.setDefcas2(defcas2);
        this.setDefcas3(defcas3);
        this.setInfra_destroyed(infra_destroyed);
        this.setImprovements_destroyed(improvements_destroyed);
        this.setMoney_looted(money_looted);

        if (note != null) {
            switch (attack_type) {
                case VICTORY:
                case A_LOOT:
                    setLoot(parseLootLegacy(note));
                    break;
            }
        }

        this.setCity_infra_before(city_infra_before);
        this.setInfra_destroyed_value(infra_destroyed_value);
        this.setAtt_gas_used(att_gas_used);
        this.setAtt_mun_used(att_mun_used);
        this.setDef_gas_used(def_gas_used);
        this.setDef_mun_used(def_mun_used);
    }

    public DBAttack(WarAttack a) {
        this(a.getId(),
        a.getDate().toEpochMilli(),
        a.getWar_id(),
        a.getAtt_id(),
        a.getDef_id(),
        AttackType.fromV3(a.getType()),
        a.getVictor(),
        a.getSuccess(),
        a.getAttcas1(),
        a.getAttcas2(),
        a.getDefcas1(),
        a.getDefcas2(),
        a.getAircraft_killed_by_tanks(),
        a.getInfra_destroyed(),
        a.getImprovements_lost(),
        a.getMoney_stolen(),
        a.getLoot_info(),
        a.getCity_infra_before(),
        a.getInfra_destroyed_value(),
        a.getAtt_gas_used(),
        a.getAtt_mun_used(),
        a.getDef_gas_used(),
        a.getDef_mun_used());

        if (a.getCity_id() != null) this.city_cached = a.getCity_id();
    }

    public Map<ResourceType, Double> getLoot() {
        if (loot == null) {
            if (getMoney_looted() != 0) {
                return Collections.singletonMap(ResourceType.MONEY, getMoney_looted());
            }
            return Collections.emptyMap();
        }
        return PnwUtil.resourcesToMap(loot);
    }

    public int getLoser() {
        return getVictor() == getDefender_nation_id() ? getAttacker_nation_id() : getVictor() == getAttacker_nation_id() ? getDefender_nation_id() : 0;
    }

    public double[] parseLootLegacy(String note) {
        if (getAttack_type() == AttackType.A_LOOT) {
            setLoot(new double[ResourceType.values.length]);
            AtomicInteger allianceId = new AtomicInteger();
            setLootPercent(parseBankLoot(note, allianceId, loot));
            setLooted(allianceId.get());

            String looterStr = note.split(" looted [0-9]+\\.[0-9]+% of ")[0];
            for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
                if (entry.getValue().getNation().equals(looterStr)) {
                    setVictor(entry.getValue().getNation_id());
                    break;
                }
            }
        } else if (getAttack_type() == AttackType.VICTORY) {
            setLoot(new double[ResourceType.values.length]);
            setLoot(parseNationLoot(note, loot));
            setLooted(getVictor() == getAttacker_nation_id() ? getDefender_nation_id() : getAttacker_nation_id());
            setLootPercent(0.1);

            String end = "% of the infrastructure in each of their cities.";
            String[] split = note.substring(0, note.length() - end.length()).split(" ");
            infraPercent_cached = Double.parseDouble(split[split.length - 1]);
        }
        if (getLoot() != null) {
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
            setLoot(null);
            return true;
        } else if (!hasNonMoney) {
            setMoney_looted(loot[0]);
            setLoot(null);
            return true;
        }
        return false;
    }

    public String toUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + getWar_id();
    }

    public Integer getLooted() {
        return looted;
    }

    public Integer getLooter() {
        return getVictor();
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

        DBAlliance alliance = Locutus.imp().getNationDB().getAllianceByName(bank);
        allianceIdOutput.set(alliance != null ? alliance.getId() : 0);
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
        double money = MathMan.parseDouble(moneyStr.substring(0, moneyStr.length() - 1));
        double coal = MathMan.parseDouble(matcher.group(2));
        double oil = MathMan.parseDouble(matcher.group(3));
        double uranium = MathMan.parseDouble(matcher.group(4));
        double iron = MathMan.parseDouble(matcher.group(5));
        double bauxite = MathMan.parseDouble(matcher.group(6));
        double lead = MathMan.parseDouble(matcher.group(7));
        double gasoline = MathMan.parseDouble(matcher.group(8));
        double munitions = MathMan.parseDouble(matcher.group(9));
        double steel = MathMan.parseDouble(matcher.group(10));
        double aluminum = MathMan.parseDouble(matcher.group(11));
        double food = MathMan.parseDouble(matcher.group(12));

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
//                        Integer.parseInt(container.getCityId()),
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
        if (getAttack_type() == AttackType.NUKE || getAttack_type() == AttackType.MISSILE) {
            setVictor(getAttacker_nation_id());
            setAttcas1(1);
        }
        if (getAttcas1() == 0 && getAttcas2() == 0 && getDefcas1() == 0 && getDefcas2() == 0 && getDefcas3() == 0) return Collections.emptyMap();
        if (attacker) {
            return getAttack_type().getLosses(getAttcas1(), getAttcas2(), 0);
        } else {
            return getAttack_type().getLosses(getDefcas1(), getDefcas2(), getDefcas3());
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
        if ((getAttack_type() == AttackType.NUKE || getAttack_type() == AttackType.MISSILE) && getSuccess() == 0) {
            setInfra_destroyed_value(0);
        }
        Map<ResourceType, Double> losses = new HashMap<>();
        if (units) {
            Map<MilitaryUnit, Integer> unitLosses = getUnitLosses(attacker);
            for (Map.Entry<MilitaryUnit, Integer> entry : unitLosses.entrySet()) {
                MilitaryUnit unit = entry.getKey();
                int amt = entry.getValue();
                if (amt > 0) {
                    double[] cost = unit.getCost(amt);
                    for (ResourceType type : ResourceType.values) {
                        double rssCost = cost[type.ordinal()];
                        if (rssCost > 0) {
                            losses.put(type, losses.getOrDefault(type, 0d) + rssCost);
                        }
                    }
                }
            }
        }

        if (includeLoot) {
            if (getVictor() != 0) {
                if (loot != null) {
                    Map<ResourceType, Double> lootDouble = PnwUtil.resourcesToMap(loot);
                    if (attacker ? getVictor() == getAttacker_nation_id() : getVictor() == getDefender_nation_id()) {
                        losses = PnwUtil.subResourcesToA(losses, lootDouble);
                    } else if (attacker ? getVictor() == getDefender_nation_id() : getVictor() == getAttacker_nation_id()) {
                        losses = PnwUtil.addResourcesToA(losses, lootDouble);
                    }
                }
                else if (getMoney_looted() != 0) {
                    int sign = (getVictor() == (attacker ? getAttacker_nation_id() : getDefender_nation_id())) ? -1 : 1;
                    losses.put(ResourceType.MONEY, losses.getOrDefault(ResourceType.MONEY, 0d) + getMoney_looted() * sign);
                }
            }
        }
        if ((getAttack_type() == AttackType.NUKE || getAttack_type() == AttackType.MISSILE) && getSuccess() == 1) {
            setVictor(getAttacker_nation_id());
        }
        if (attacker ? getVictor() == getDefender_nation_id() : getVictor() == getAttacker_nation_id()) {
            if (infra && getInfra_destroyed_value() != 0) {
                if (getInfra_destroyed_value() == intOverflow) {
                    setInfra_destroyed_value(PnwUtil.calculateInfra(this.getCity_infra_before() - getInfra_destroyed(), this.getCity_infra_before()));
                }
                losses.put(ResourceType.MONEY, (losses.getOrDefault(ResourceType.MONEY, 0d) + getInfra_destroyed_value()));
            }
        }

        if (consumption) {
            Double mun = attacker ? getAtt_mun_used() : getDef_mun_used();
            Double gas = attacker ? getAtt_gas_used() : getDef_gas_used();
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

        return attack.getWar_attack_id() == getWar_attack_id();
    }

    @Override
    public int hashCode() {
        return getWar_attack_id();
    }

    @Override
    public String toString() {
        return "DBAttack{" +
                "war_attack_id=" + getWar_attack_id() +
                ", epoch=" + getDate() +
                ", war_id=" + getWar_id() +
                ", attacker_nation_id=" + getAttacker_nation_id() +
                ", defender_nation_id=" + getDefender_nation_id() +
                ", attack_type=" + getAttack_type() +
                ", victor=" + getVictor() +
                ", success=" + getSuccess() +
                ", attcas1=" + getAttcas1() +
                ", attcas2=" + getAttcas2() +
                ", defcas1=" + getDefcas1() +
                ", defcas2=" + getDefcas2() +
                ", defcas3=" + getDefcas3() +
                ", infra_destroyed=" + getInfra_destroyed() +
                ", improvements_destroyed=" + getImprovements_destroyed() +
                ", money_looted=" + getMoney_looted() +
                ", loot=" + loot +
                ", lootPercent=" + getLootPercent() +
                ", looted=" + getLooted() +
                ", city_infra_before=" + getCity_infra_before() +
                ", infra_destroyed_value=" + getInfra_destroyed_value() +
                ", att_gas_used=" + getAtt_gas_used() +
                ", att_mun_used=" + getAtt_mun_used() +
                ", def_gas_used=" + getDef_gas_used() +
                ", def_mun_used=" + getDef_mun_used() +
                '}';
    }

    public DBNation getNation(boolean attacker) {
        return DBNation.getById(attacker ? getAttacker_nation_id() : getDefender_nation_id());
    }

    public int getWar_attack_id() {
        return war_attack_id;
    }

    public void setWar_attack_id(int war_attack_id) {
        this.war_attack_id = war_attack_id;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public void setWar_id(int war_id) {
        this.war_id = war_id;
    }

    public int getAttacker_nation_id() {
        return attacker_nation_id;
    }

    public void setAttacker_nation_id(int attacker_nation_id) {
        this.attacker_nation_id = attacker_nation_id;
    }

    public int getDefender_nation_id() {
        return defender_nation_id;
    }

    public void setDefender_nation_id(int defender_nation_id) {
        this.defender_nation_id = defender_nation_id;
    }

    public AttackType getAttack_type() {
        return attack_type;
    }

    public void setAttack_type(AttackType attack_type) {
        this.attack_type = attack_type;
    }

    public int getVictor() {
        return victor;
    }

    public void setVictor(int victor) {
        this.victor = victor;
    }

    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    public int getAttcas1() {
        return attcas1;
    }

    public void setAttcas1(int attcas1) {
        this.attcas1 = attcas1;
    }

    public int getAttcas2() {
        return attcas2;
    }

    public void setAttcas2(int attcas2) {
        this.attcas2 = attcas2;
    }

    public int getDefcas1() {
        return defcas1;
    }

    public void setDefcas1(int defcas1) {
        this.defcas1 = defcas1;
    }

    public int getDefcas2() {
        return defcas2;
    }

    public void setDefcas2(int defcas2) {
        this.defcas2 = defcas2;
    }

    public int getDefcas3() {
        return defcas3;
    }

    public void setDefcas3(int defcas3) {
        this.defcas3 = defcas3;
    }

    public double getInfra_destroyed() {
        return infra_destroyed;
    }

    public void setInfra_destroyed(double infra_destroyed) {
        this.infra_destroyed = infra_destroyed;
    }

    public int getImprovements_destroyed() {
        return improvements_destroyed;
    }

    public void setImprovements_destroyed(int improvements_destroyed) {
        this.improvements_destroyed = improvements_destroyed;
    }

    public double getMoney_looted() {
        return money_looted;
    }

    public void setMoney_looted(double money_looted) {
        this.money_looted = money_looted;
    }

    public void setLoot(double[] loot) {
        this.loot = loot;
    }

    public void setLooted(int looted) {
        this.looted = looted;
    }

    public void setLootPercent(double lootPercent) {
        this.lootPercent = lootPercent;
    }

    public double getCity_infra_before() {
        return city_infra_before;
    }

    public void setCity_infra_before(double city_infra_before) {
        this.city_infra_before = city_infra_before;
    }

    public double getInfra_destroyed_value() {
        return infra_destroyed_value;
    }

    public void setInfra_destroyed_value(double infra_destroyed_value) {
        this.infra_destroyed_value = infra_destroyed_value;
    }

    public double getAtt_gas_used() {
        return att_gas_used;
    }

    public void setAtt_gas_used(double att_gas_used) {
        this.att_gas_used = att_gas_used;
    }

    public double getAtt_mun_used() {
        return att_mun_used;
    }

    public void setAtt_mun_used(double att_mun_used) {
        this.att_mun_used = att_mun_used;
    }

    public double getDef_gas_used() {
        return def_gas_used;
    }

    public void setDef_gas_used(double def_gas_used) {
        this.def_gas_used = def_gas_used;
    }

    public double getDef_mun_used() {
        return def_mun_used;
    }

    public void setDef_mun_used(double def_mun_used) {
        this.def_mun_used = def_mun_used;
    }

    public DBWar getWar() {
        return Locutus.imp().getWarDb().getWar(war_id);
    }
}
