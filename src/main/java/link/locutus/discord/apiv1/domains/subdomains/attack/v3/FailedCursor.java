package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.db.entities.DBWar;

import java.util.Collections;
import java.util.Map;

public abstract class FailedCursor extends AbstractCursor {
    @Override
    public SuccessType getSuccess() {
        return SuccessType.UTTER_FAILURE;
    }

    @Override
    public int[] addAttUnitLosses(int[] buffer) {
        return buffer;
    }

    @Override
    public int[] addDefUnitLosses(int[] buffer) {
        return buffer;
    }

    @Override
    public double[] addAttUnitLossValueByUnit(double[] valueByUnit, DBWar war) {
        return valueByUnit;
    }

    @Override
    public double[] addDefUnitLossValueByUnit(double[] valueByUnit, DBWar war) {
        return valueByUnit;
    }

    @Override
    public int getAttUnitLosses(MilitaryUnit unit) {
        return 0;
    }

    @Override
    public int getDefUnitLosses(MilitaryUnit unit) {
        return 0;
    }

    @Override public double getAttLossValue(DBWar war) {
        return 0;
    }
    @Override public double getDefLossValue(DBWar war) {
        return 0;
    }

    @Override public double[] addAttLosses(double[] buffer, DBWar war) {
        return buffer;
    }

    @Override public double[] addDefLosses(double[] buffer, DBWar war) {
        return buffer;
    }
    @Override public double[] addAttUnitCosts(double[] buffer, DBWar war) {
        return buffer;
    }
    @Override
    public double getAttUnitLossValue(DBWar war) {
        return 0;
    }
    @Override
    public double getDefUnitLossValue(DBWar war) {
        return 0;
    }
    @Override public double[] addDefUnitCosts(double[] buffer, DBWar war) {
        return buffer;
    }
    @Override public double[] addInfraCosts(double[] buffer) {
        return buffer;
    }
    @Override public double[] addAttConsumption(double[] buffer) {
        return buffer;
    }

    @Override
    public double getAttConsumptionValue() {
        return 0;
    }

    @Override
    public double getDefConsumptionValue() {
        return 0;
    }

    @Override public double[] addDefConsumption(double[] buffer) {
        return buffer;
    }
    @Override
    public double[] addAttLoot(double[] buffer) {
        return buffer;
    }

    @Override
    public double getAttLootValue() {
        return 0;
    }

    @Override
    public double getDefLootValue() {
        return 0;
    }

    @Override
    public double[] addDefLoot(double[] buffer) {
        return buffer;
    }

    @Override
    public double[] addBuildingCosts(double[] buffer) {
        return buffer;
    }

    @Override
    public double getBuildingLossValue() {
        return 0;
    }

    @Override
    public double getInfra_destroyed_value() {
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
    public Map<Building, Integer> getBuildingsDestroyed() {
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
