package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.update.WarUpdateProcessor;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public interface IAttack {
    @Command
    boolean isAttackerIdGreater();
    @Command
    int getAttacker_id();
    @Command
    int getDefender_id();
    @Command
    int getWar_attack_id();
    @Command
    long getDate();
    // skip war
    // skip attacker
    // skip defender
    @Command
    AttackType getAttack_type();
    @Command
    SuccessType getSuccess();
    @Command
    int getAttcas1();
    @Command
    int getAttcas2();
    @Command
    int getDefcas1();
    @Command
    int getDefcas2();
    @Command
    int getDefcas3(); // ?
    @Command
    double getInfra_destroyed();
    @Command
    int getImprovements_destroyed(); // ?

    void addBuildingsDestroyed(int[] destroyedBuffer);
    void addBuildingsDestroyed(char[] destroyedBuffer);
    /**
     * Valid for victory when all cities receive damage
     * @return number between 0 and 100
     */
    @Command
    double getInfra_destroyed_percent();

    @Command
    int getCity_id();
    @Command
    default int getAllianceIdLooted() {
        return 0;
    }
    @Command
    double getMoney_looted();
    double[] getLoot();
    @Command
    double getLootPercent();
    // skip getLooted
    @Command
    double getCity_infra_before();
    @Command
    double getInfra_destroyed_value();
    @Command
    double getAtt_gas_used();
    @Command
    double getAtt_mun_used();
    @Command
    double getDef_gas_used();
    @Command
    double getDef_mun_used();
    @Command
    default int getVictor() {
        return getSuccess() != SuccessType.UTTER_FAILURE ? getAttacker_id() : getDefender_id();
    }
    @Command
    int getWar_id();
    @Command
    default String toUrl() {
        return Settings.PNW_URL() + "/nation/war/timeline/war=" + getWar_id();
    }
    @Command
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
        return war.getAttacks3(load);
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
    @Command
    double getAttLossValue(DBWar war);
    @Command
    double getDefLossValue(DBWar war);
    double[] addAttLosses(double[] buffer, DBWar war);
    double[] addDefLosses(double[] buffer, DBWar war);

    double[] addAttUnitCosts(double[] buffer, DBWar war);
    double[] addDefUnitCosts(double[] buffer, DBWar war);
    @Command
    double getAttUnitLossValue(DBWar war);
    @Command
    double getDefUnitLossValue(DBWar war);
    double[] addAttUnitLossValueByUnit(double[] valueByUnit, DBWar war);
    double[] addDefUnitLossValueByUnit(double[] valueByUnit, DBWar war);

    double[] addInfraCosts(double[] buffer);
    double[] addAttConsumption(double[] buffer);
    double[] addDefConsumption(double[] buffer);
    @Command
    double getAttConsumptionValue();
    @Command
    double getDefConsumptionValue();
    double[] addAttLoot(double[] buffer);
    double[] addDefLoot(double[] buffer);
    @Command
    double getAttLootValue();
    @Command
    double getDefLootValue();
    double[] addBuildingCosts(double[] buffer);
    double getBuildingLossValue();

    int[] addAttUnitLosses(int[] buffer);
    int[] addDefUnitLosses(int[] buffer);

    @Command
    int getAttUnitLosses(MilitaryUnit unit);
    @Command
    int getDefUnitLosses(MilitaryUnit unit);

    default int[] addUnitLosses(int[] buffer, boolean isAttacker) {
        return isAttacker ? addAttUnitLosses(buffer) : addDefUnitLosses(buffer);
    }

    @Command
    default DBWar getWar() {
        Locutus lc = Locutus.imp();
        if (lc != null) {
            return getWar(lc.getWarDb());
        }
        return null;
    }

    public DBWar getWar(WarDB db);

    default AttackTypeSubCategory getSubCategory(BiFunction<DBNation, Long, Integer> checkActiveM) {
        return WarUpdateProcessor.subCategorize(this, checkActiveM);
    }

    @Deprecated
    default double getLossesConverted(DBWar war, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        double value = 0;
        if (attacker) {
            if (units) {
                value += getAttUnitLossValue(war);
            }
            if (consumption) {
                value += getAttConsumptionValue();
            }
            if (includeLoot) {
                value += getAttLootValue();
            }
        } else {
            if (units) {
                value += getDefUnitLossValue(war);
            }
            if (infra) {
                value += getInfra_destroyed_value();
            }
            if (consumption) {
                value += getDefConsumptionValue();
            }
            if (includeLoot) {
                value += getDefLootValue();
            }
            if (includeBuildings) {
                value += getBuildingLossValue();
            }
        }
        return value;
    }

    default double[] addLosses(double[] rssBuffer, DBWar war, boolean attacker, boolean units, boolean infra, boolean consumption, boolean includeLoot, boolean includeBuildings) {
        if (attacker) {
            if (units) {
                addAttUnitCosts(rssBuffer, war);
            }
            if (consumption) {
                addAttConsumption(rssBuffer);
            }
            if (includeLoot) {
                addAttLoot(rssBuffer);
            }
        } else {
            if (units) {
                addDefUnitCosts(rssBuffer, war);
            }
            if (infra) {
                addInfraCosts(rssBuffer);
            }
            if (consumption) {
                addDefConsumption(rssBuffer);
            }
            if (includeLoot) {
                addDefLoot(rssBuffer);
            }
            if (includeBuildings) {
                addBuildingCosts(rssBuffer);
            }
        }
        return rssBuffer;
    }
}
