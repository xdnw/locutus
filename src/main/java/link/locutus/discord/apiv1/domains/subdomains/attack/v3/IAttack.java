package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.update.WarUpdateProcessor;

import java.util.Collections;
import java.util.List;

public interface IAttack {
    /*
    "war_attack_id=" + getWar_attack_id() +
                ", epoch=" + getDate() +
                ", war_id=" + getWar_id() +
                ", attacker_nation_id=" + getAttacker_id() +
                ", defender_nation_id=" + getDefender_id() +
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
                ", loot=" + getLoot() +
                ", lootPercent=" + getLootPercent() +
                ", looted=" + getLooted() +
                ", city_infra_before=" + getCity_infra_before() +
                ", infra_destroyed_value=" + getInfra_destroyed_value() +
                ", att_gas_used=" + getAtt_gas_used() +
                ", att_mun_used=" + getAtt_mun_used() +
                ", def_gas_used=" + getDef_gas_used() +
                ", def_mun_used=" + getDef_mun_used() +
                */
    boolean isAttackerIdGreater();
    int getAttacker_id();
    int getDefender_id();
    int getWar_attack_id();
    long getDate();
    // skip war
    // skip attacker
    // skip defender
    AttackType getAttack_type();
    SuccessType getSuccess();
    int getAttcas1();
    int getAttcas2();
    int getDefcas1();
    int getDefcas2();
    int getDefcas3(); // ?
    double getInfra_destroyed();
    int getImprovements_destroyed(); // ?
    void addBuildingsDestroyed(int[] destroyedBuffer);
    void addBuildingsDestroyed(char[] destroyedBuffer);
    /**
     * Valid for victory when all cities receive damage
     * @return number between 0 and 100
     */
    double getInfra_destroyed_percent();

    int getCity_id();
    default int getAllianceIdLooted() {
        return 0;
    }
    double getMoney_looted();
    double[] getLoot();
    double getLootPercent();
    // skip getLooted
    double getCity_infra_before();

    default double getInfra_destroyed_value() {
        double before = getCity_infra_before();
        if (before > 0) {
            double destroyed = getInfra_destroyed();
            if (destroyed == 0) return 0;
            return PW.City.Infra.calculateInfra(before - destroyed, before);
        }
        return 0;
    }

    double getAtt_gas_used();
    double getAtt_mun_used();
    double getDef_gas_used();
    double getDef_mun_used();

    default int getVictor() {
        return getSuccess() != SuccessType.UTTER_FAILURE ? getAttacker_id() : getDefender_id();
    }

    int getWar_id();

    default String toUrl() {
        return "" + Settings.INSTANCE.PNW_URL() + "/nation/war/timeline/war=" + getWar_id();
    }

    default double getLossesConverted(double[] buffer, boolean attacker) {
        return getLossesConverted(buffer, attacker, true, true, true, true, true);
    }

    default double getLossesConverted(double[] buffer, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        return ResourceType.convertedTotal(getLosses(buffer, attacker, units, infra, consumption, includeLoot, includeBuildings));
    }

    default double[] getLosses(double[] buffer, boolean attacker) {
        return getLosses(buffer, attacker, true, true, true, true, true);
    }

    default int getResistance() {
        if (getSuccess() == SuccessType.UTTER_FAILURE) return 0;
        int damage;
        switch (getAttack_type()) {
            default: {
                return 0;
            }
            case FORTIFY:
                return 0;
            case GROUND:
                damage = 10;
                break;
            case AIRSTRIKE_INFRA:
            case AIRSTRIKE_SOLDIER:
            case AIRSTRIKE_TANK:
            case AIRSTRIKE_MONEY:
            case AIRSTRIKE_SHIP:
            case AIRSTRIKE_AIRCRAFT:
                damage = 12;
                break;
            case NAVAL:
                damage = 14;
                break;
            case MISSILE:
                damage = 24;
                break;
            case NUKE:
                damage = 31;
                break;
        }
        damage -= (9 - getSuccess().ordinal() * 3);
        return damage;
    }

    default List<AbstractCursor> getWarAttacks(boolean load) {
        DBWar war = getWar();
        if (war == null) return Collections.emptyList();
        return war.getAttacks2(load);
    }

    default List<AbstractCursor> getPriorAttacks(boolean load) {
        return getWarAttacks(load).stream().filter(f -> f.war_attack_id < getWar_attack_id()).toList();
    }

    default AbstractCursor getPriorAttack(boolean onlySameAttacker, boolean load) {
        List<AbstractCursor> attacks = getWarAttacks(load);
        if (attacks.isEmpty()) return null;
        for (int i = attacks.size() - 1; i >= 0; i--) {
            AbstractCursor attack = attacks.get(i);
            if (attack.getAttack_type() == AttackType.PEACE) continue;
            if (attack.war_attack_id < getWar_attack_id() && (!onlySameAttacker || attack.attacker_id == getAttacker_id())) return attack;
        }
        return null;
    }

    double[] getLosses(double[] buffer, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings);

    DBWar getWar();

    default AttackTypeSubCategory getSubCategory(boolean checkActive) {
        return WarUpdateProcessor.subCategorize(this, checkActive);
    }
}
