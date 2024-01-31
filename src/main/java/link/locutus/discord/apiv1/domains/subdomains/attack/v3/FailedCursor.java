package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;

import java.util.Collections;
import java.util.Map;

public abstract class FailedCursor extends AbstractCursor {
    @Override
    public SuccessType getSuccess() {
        return SuccessType.UTTER_FAILURE;
    }

    @Override
    public int[] getUnitLosses(int[] buffer, boolean isAttacker) {
        return buffer;
    }

    @Override
    public double[] getUnitLossCost(double[] buffer, boolean isAttacker) {
        return buffer;
    }

    @Override
    public int getUnitLosses(MilitaryUnit unit, boolean attacker) {
        return 0;
    }

    @Override
    public int getVictor() {
        return getAttacker_id();
    }

    @Override
    public int getCity_id() {
        return 0;
    }

    @Override
    public double getInfra_destroyed_percent() {
        return 0;
    }

    @Override
    public int getAttcas1() {
        return 0;
    }

    @Override
    public int getAttcas2() {
        return 0;
    }

    @Override
    public int getDefcas1() {
        return 0;
    }

    @Override
    public int getDefcas2() {
        return 0;
    }

    @Override
    public int getDefcas3() {
        return 0;
    }

    @Override
    public double getInfra_destroyed() {
        return 0;
    }

    @Override
    public int getImprovements_destroyed() {
        return 0;
    }

    @Override
    public void addBuildingsDestroyed(int[] destroyedBuffer) {

    }

    @Override
    public void addBuildingsDestroyed(char[] destroyedBuffer) {

    }

    @Override
    public Map<Building, Integer> getBuildingsDestroyed2() {
        return Collections.emptyMap();
    }

    public double[] getBuildingCost(double[] buffer) {
        return buffer;
    }

    @Override
    public double getMoney_looted() {
        return 0;
    }

    @Override
    public double[] getLoot() {
        return null;
    }

    @Override
    public double getLootPercent() {
        return 0;
    }

    @Override
    public double getCity_infra_before() {
        return 0;
    }

    @Override
    public double getAtt_gas_used() {
        return 0;
    }

    @Override
    public double getAtt_mun_used() {
        return 0;
    }

    @Override
    public double getDef_gas_used() {
        return 0;
    }

    @Override
    public double getDef_mun_used() {
        return 0;
    }
}
