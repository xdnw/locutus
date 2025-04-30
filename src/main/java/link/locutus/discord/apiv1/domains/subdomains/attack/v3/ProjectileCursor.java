package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public abstract class ProjectileCursor extends DamageCursor {
    @Override
    public double[] addAttConsumption(double[] buffer) {
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

    @Override
    public double[] addDefConsumption(double[] buffer) {
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
    public double[] addAttLoot(double[] buffer) {
        return buffer;
    }

    @Override
    public double[] addDefLoot(double[] buffer) {
        return buffer;
    }

    @Override
    public int getAttUnitLosses(MilitaryUnit unit) {
        return unit == getUnits()[0] ? 1 : 0;
    }

    @Override
    public int[] addDefUnitLosses(int[] buffer) {
        return buffer;
    }

    @Override
    public double[] addDefUnitCosts(double[] buffer, DBWar war) {
        return buffer;
    }

    @Override
    public int getDefUnitLosses(MilitaryUnit unit) {
        return 0;
    }

    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
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
    public double getMoney_looted() {
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
