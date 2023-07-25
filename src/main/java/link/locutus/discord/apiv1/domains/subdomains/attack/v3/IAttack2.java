package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.util.PnwUtil;

import java.util.Map;

public interface IAttack2 {
    /*
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
            return PnwUtil.calculateInfraFast(before, before - destroyed);
        }
        return 0;
    }

    double getAtt_gas_used();
    double getAtt_mun_used();
    double getDef_gas_used();
    double getDef_mun_used();


}
