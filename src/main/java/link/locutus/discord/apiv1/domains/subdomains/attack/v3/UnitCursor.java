package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;
import link.locutus.discord.util.math.ArrayUtil;

public abstract class UnitCursor extends DamageCursor {
    private int att_mun_used_cents;
    private int att_gas_used_cents;
    private int def_mun_used_cents;
    private int def_gas_used_cents;
    private boolean has_salvage;

    private static long SALVAGE_INCLUDED_DEFENDER_DATE = 1693105320000L;

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        att_mun_used_cents = (int) ArrayUtil.toCents(legacy.getAtt_mun_used());
        def_mun_used_cents = (int) ArrayUtil.toCents(legacy.getDef_mun_used());
        att_gas_used_cents = (int) ArrayUtil.toCents(legacy.getAtt_gas_used());
        def_gas_used_cents = (int) ArrayUtil.toCents(legacy.getDef_gas_used());
        has_salvage = false;
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
    public double getAttLootValue() {
        return 0;
    }

    @Override
    public double getDefLootValue() {
        return 0;
    }

    public double[] addAttConsumption(double[] buffer) {
        buffer[ResourceType.MUNITIONS.ordinal()] += att_mun_used_cents * 0.01;
        buffer[ResourceType.GASOLINE.ordinal()] += att_gas_used_cents * 0.01;
        return buffer;
    }
    public double[] addDefConsumption(double[] buffer) {
        buffer[ResourceType.MUNITIONS.ordinal()] += def_mun_used_cents * 0.01;
        buffer[ResourceType.GASOLINE.ordinal()] += def_gas_used_cents * 0.01;
        return buffer;
    }
    @Override
    public double getAttConsumptionValue() {
        return ResourceType.convertedTotal(ResourceType.MUNITIONS, att_mun_used_cents * 0.01) +
                ResourceType.convertedTotal(ResourceType.GASOLINE, att_gas_used_cents * 0.01);
    }

    @Override
    public double getDefConsumptionValue() {
        return ResourceType.convertedTotal(ResourceType.MUNITIONS, def_mun_used_cents * 0.01) +
                ResourceType.convertedTotal(ResourceType.GASOLINE, def_gas_used_cents * 0.01);
    }

    @Override
    public double[] addAttUnitCosts(double[] buffer, DBWar war) {
        if (has_salvage) {
            MilitaryUnit[] units = getUnits();
            int research = war == null ? 0 : getAttResearchBits(war);
            for (MilitaryUnit unit : units) {
                int amt = getAttUnitLosses(unit);
                if (amt > 0) unit.addCostSalvage(buffer, amt, research);
                if (date > SALVAGE_INCLUDED_DEFENDER_DATE) {
                    amt = getDefUnitLosses(unit);
                    if (amt > 0) unit.subtractSalvageCost(buffer, amt);
                }

            }
            return buffer;
        } else {
            return super.addAttUnitCosts(buffer, war);
        }
    }

    @Override
    public double[] addAttLosses(double[] buffer, DBWar war) {
        double[] value = super.addAttLosses(buffer, war);
        value = addAttConsumption(value);
        return value;
    }

    @Override
    public double[] addDefLosses(double[] buffer, DBWar war) {
        double[] value = super.addDefLosses(buffer, war);
        value = addDefConsumption(value);
        return value;
    }

    @Override
    public double getDefLossValue(DBWar war) {
        double value = super.getDefLossValue(war);
        value += getDefConsumptionValue();
        return value;
    }

    @Override
    public double getAttUnitLossValue(DBWar war) {
        if (has_salvage) {
            double value = 0;
            MilitaryUnit[] units = getUnits();
            int research = war == null ? 0 : getAttResearchBits(war);
            for (MilitaryUnit unit : units) {
                int amt = getAttUnitLosses(unit);
                if (amt > 0) {
                    value += unit.getConvertedCostPlusSalvage(research) * amt;
                }
                if (date > SALVAGE_INCLUDED_DEFENDER_DATE) {
                    amt = getDefUnitLosses(unit);
                    if (amt > 0) {
                        value -= unit.getSalvageCostPerUnit() * amt;
                    }
                }
            }
            return value;
        } else {
            return super.getAttUnitLossValue(war);
        }
    }

    @Override
    public double[] addAttUnitLossValueByUnit(double[] valueByUnit, DBWar war) {
        if (has_salvage) {
            MilitaryUnit[] units = getUnits();
            int research = war == null ? 0 : getAttResearchBits(war);
            for (MilitaryUnit unit : units) {
                int amt = getAttUnitLosses(unit);
                if (amt > 0) {
                    valueByUnit[unit.ordinal()] += unit.getConvertedCostPlusSalvage(research) * amt;
                }
                if (date > SALVAGE_INCLUDED_DEFENDER_DATE) {
                    amt = getDefUnitLosses(unit);
                    if (amt > 0) {
                        valueByUnit[unit.ordinal()] -= unit.getSalvageCostPerUnit() * amt;
                    }
                }
            }
            return valueByUnit;
        } else {
            return super.addAttUnitLossValueByUnit(valueByUnit, war);
        }
    }

    @Override
    public double getAttLossValue(DBWar war) {
        double value = super.getAttLossValue(war);
        value += getAttConsumptionValue();
        return value;
    }

    @Override
    public void serialize(BitBuffer output) {
        super.serialize(output);

        output.writeBit(att_mun_used_cents > 0);
        if (att_mun_used_cents > 0) {
            output.writeVarInt(att_mun_used_cents);

            output.writeBit(att_gas_used_cents > 0);
            if (att_gas_used_cents > 0) {
                output.writeVarInt(att_gas_used_cents);
            }
        }
        // 5 bits for length

        output.writeBit(def_mun_used_cents > 0);
        if (def_mun_used_cents > 0) {
            output.writeVarInt(def_mun_used_cents);

            output.writeBit(def_gas_used_cents > 0);
            if (def_gas_used_cents > 0) {
                output.writeVarInt(def_gas_used_cents);
            }
        }

        output.writeBit(has_salvage);
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        if (input.readBit()) {
            att_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                att_gas_used_cents = (int) input.readVarInt();
            } else {
                att_gas_used_cents = 0;
            }
        } else {
            att_mun_used_cents = 0;
            att_gas_used_cents = 0;
        }

        if (input.readBit()) {
            def_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                def_gas_used_cents = (int) input.readVarInt();
            } else {
                def_gas_used_cents = 0;
            }
        } else {
            def_mun_used_cents = 0;
            def_gas_used_cents = 0;
        }

        has_salvage = input.readBit();
    }

    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);

        att_mun_used_cents = (int) (attack.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) (attack.getDef_mun_used() * 100);
        att_gas_used_cents = (int) (attack.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) (attack.getDef_gas_used() * 100);

        has_salvage = (success != SuccessType.UTTER_FAILURE && (attack.getMilitary_salvage_aluminum() > 0 || attack.getMilitary_salvage_steel() > 0));
    }


    @Override
    public double getAtt_gas_used() {
        return att_gas_used_cents * 0.01;
    }

    @Override
    public double getAtt_mun_used() {
        return att_mun_used_cents * 0.01;
    }

    @Override
    public double getDef_gas_used() {
        return def_gas_used_cents * 0.01;
    }

    @Override
    public double getDef_mun_used() {
        return def_mun_used_cents * 0.01;
    }
}
