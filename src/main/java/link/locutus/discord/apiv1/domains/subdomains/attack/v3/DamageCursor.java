package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.bytes.Byte2ByteArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.BitBuffer;

import java.util.*;

public abstract class DamageCursor extends AbstractCursor {

    protected SuccessType success;
    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private Map<Byte, Byte> buildingsDestroyed = null;
    private int num_improvements;

    @Override
    public int getCity_id() {
        return city_id;
    }

    @Override
    public double getInfra_destroyed_percent() {
        return 0;
    }

    @Override
    public Set<Integer> getCityIdsDamaged() {
        if (city_id == 0) return Collections.emptySet();
        return Collections.singleton(city_id);
    }

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        success = SuccessType.values[legacy.getSuccess()];
        city_id = legacy.city_cached;
        city_infra_before_cents = (int) ArrayUtil.toCents(legacy.getCity_infra_before());
        infra_destroyed_cents = (int) ArrayUtil.toCents(legacy.getInfra_destroyed());
        num_improvements = legacy.getImprovements_destroyed();
        buildingsDestroyed = null;
        if (num_improvements > 0) {
            // default to Buildings.FARM.ordinal()
//            buildingsDestroyed.put((byte) Buildings.FARM.ordinal(), (byte) num_improvements);
        }
    }

    @Override
    public final SuccessType getSuccess() {
        return success;
    }

    @Override
    public double getInfra_destroyed() {
        return infra_destroyed_cents * 0.01;
    }

    @Override
    public int getImprovements_destroyed() {
        return num_improvements;
    }

    public abstract MilitaryUnit[] getUnits();

    @Override
    public Map<MilitaryUnit, Integer> getUnitLosses2(boolean isAttacker) {
        MilitaryUnit[] units = getUnits();
        Map<MilitaryUnit, Integer> result = new Object2IntArrayMap<>(units.length);
        for (MilitaryUnit unit : units) {
            int amt = isAttacker ? getAttUnitLosses(unit) : getDefUnitLosses(unit);
            if (amt > 0) {
                result.put(unit, amt);
            }
        }
        return result;
    }

    @Override
    public abstract int getAttUnitLosses(MilitaryUnit unit);

    @Override
    public abstract int getDefUnitLosses(MilitaryUnit unit);

    @Override
    public int[] addAttUnitLosses(int[] buffer) {
        for (MilitaryUnit unit : getUnits()) {
            int losses = getAttUnitLosses(unit);
            if (losses > 0) {
                buffer[unit.ordinal()] += losses;
            }
        }
        return buffer;
    }

    @Override
    public int[] addDefUnitLosses(int[] buffer) {
        for (MilitaryUnit unit : getUnits()) {
            int losses = getDefUnitLosses(unit);
            if (losses > 0) {
                buffer[unit.ordinal()] += losses;
            }
        }
        return buffer;
    }

    @Override
    public double[] addAttLosses(double[] buffer, DBWar war) {
        addAttUnitCosts(buffer, war);
        return buffer;
    }

    @Override
    public double[] addDefLosses(double[] buffer, DBWar war) {
        addInfraCosts(buffer);
        addBuildingCosts(buffer);
        addDefUnitCosts(buffer, war);
        return buffer;
    }

    @Override
    public double getDefLossValue(DBWar war) {
        // Infra
        double value = getInfra_destroyed_value();
        // Building
        if (num_improvements != 0 && buildingsDestroyed != null) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                Building building = Buildings.get(typeId);
                value += building.getNMarketCost(amt);
            }
        }
        value += getDefUnitLossValue(war);
        return value;
    }

    @Override
    public double getAttLossValue(DBWar war) {
        return getAttUnitLossValue(war);
    }

    @Override
    public double[] addDefUnitCosts(double[] buffer, DBWar war) {
        MilitaryUnit[] units = getUnits();
        int research = getDefResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getDefUnitLosses(unit);
            if (amt > 0) unit.addCost(buffer, amt, research);
        }
        return buffer;
    }

    @Override
    public double getDefUnitLossValue(DBWar war) {
        double value = 0;
        MilitaryUnit[] units = getUnits();
        int research = getDefResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getDefUnitLosses(unit);
            if (amt > 0) {
                value += unit.getConvertedCost(research) * amt;
            }
        }
        return value;
    }

    @Override
    public double getAttUnitLossValue(DBWar war) {
        double value = 0;
        MilitaryUnit[] units = getUnits();
        int research = war == null ? 0 : getAttResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getAttUnitLosses(unit);
            if (amt > 0) {
                value += unit.getConvertedCost(research) * amt;
            }
        }
        return value;
    }

    @Override
    public double[] addAttUnitCosts(double[] buffer, DBWar war) {
        MilitaryUnit[] units = getUnits();
        int research = war == null ? 0 : getAttResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getAttUnitLosses(unit);
            if (amt > 0) unit.addCost(buffer, amt, research);
        }
        return buffer;
    }

    @Override
    public double[] addAttUnitLossValueByUnit(double[] valueByUnit, DBWar war) {
        MilitaryUnit[] units = getUnits();
        int research = war == null ? 0 : getAttResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getAttUnitLosses(unit);
            if (amt > 0) {
                valueByUnit[unit.ordinal()] += unit.getConvertedCost(research) * amt;
            }
        }
        return valueByUnit;
    }

    @Override
    public double[] addDefUnitLossValueByUnit(double[] valueByUnit, DBWar war) {
        MilitaryUnit[] units = getUnits();
        int research = getDefResearchBits(war);
        for (MilitaryUnit unit : units) {
            int amt = getDefUnitLosses(unit);
            if (amt > 0) {
                valueByUnit[unit.ordinal()] += unit.getConvertedCost(research) * amt;
            }
        }
        return valueByUnit;
    }

    @Override
    public double getInfra_destroyed_value() {
        double before = getCity_infra_before();
        if (before > 0) {
            double destroyed = getInfra_destroyed();
            if (destroyed == 0) return 0;
            return PW.City.Infra.calculateInfra(before - destroyed, before);
        }
        return 0;
    }

    @Override
    public double[] addInfraCosts(double[] buffer) {
        buffer[ResourceType.MONEY.ordinal()] += getInfra_destroyed_value();
        return buffer;
    }

    @Override
    public double[] addBuildingCosts(double[] buffer) {
        if (num_improvements != 0 && buildingsDestroyed != null) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                Building building = Buildings.get(typeId);
                building.cost(buffer, amt);
            }
        }
        return buffer;
    }

    @Override
    public double getBuildingLossValue() {
        double value = 0;
        if (num_improvements != 0 && buildingsDestroyed != null) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                Building building = Buildings.get(typeId);
                value += building.getNMarketCost(amt);
            }
        }
        return value;
    }

    @Override
    public int getAttcas1() {
        MilitaryUnit[] units = getUnits();
        return getAttUnitLosses(units[0]);
    }

    @Override
    public int getDefcas1() {
        MilitaryUnit[] units = getUnits();
        return getDefUnitLosses(units[0]);
    }

    @Override
    public int getAttcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getAttUnitLosses(units[1]) : 0;
    }

    @Override
    public int getDefcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getDefUnitLosses(units[1]) : 0;
    }

    @Override
    public int getDefcas3() {
        MilitaryUnit[] units = getUnits();
        return units.length > 2 ? getDefUnitLosses(units[2]) : 0;
    }

    @Override
    public Map<Building, Integer> getBuildingsDestroyed() {
        if (num_improvements == 0 || buildingsDestroyed == null) {
            return Collections.emptyMap();
        }
        Map<Building, Integer> result = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
            byte typeId = entry.getKey();
            byte amt = entry.getValue();
            result.put(Buildings.get(typeId), (int) amt);
        }
        return result;
    }

    @Override
    public void addBuildingsDestroyed(int[] destroyedBuffer) {
        if (num_improvements > 0 && buildingsDestroyed != null) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                destroyedBuffer[typeId] += amt;
            }
        }
    }

    @Override
    public void addBuildingsDestroyed(char[] destroyedBuffer) {
        if (num_improvements > 0 && buildingsDestroyed != null) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                destroyedBuffer[typeId] += amt;
            }
        }
    }

    @Override
    public double getCity_infra_before() {
        return city_infra_before_cents * 0.01;
    }

    @Override
    public void serialize(BitBuffer output) {
        super.serialize(output);
        output.writeBits(success.ordinal(), 2);

        if (success != SuccessType.UTTER_FAILURE) {
            output.writeVarInt(city_id);
            output.writeVarInt(city_infra_before_cents);
            output.writeVarInt(infra_destroyed_cents);
            output.writeBits(num_improvements, 4);
            int size = buildingsDestroyed == null ? 0 : buildingsDestroyed.size();
            if (size == 0) {
                output.writeBit(false);
            } else {
                output.writeBit(true);
                if (buildingsDestroyed != null) {
                    for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                        byte typeId = entry.getKey();
                        byte amt = entry.getValue();
                        for (int i = 0; i < amt; i++) {
                            output.writeBits(typeId, 5);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (success != SuccessType.UTTER_FAILURE) {
            city_id = input.readVarInt();
            city_infra_before_cents = (int) input.readVarInt();
            infra_destroyed_cents = (int) input.readVarInt();
            num_improvements = (int) input.readBits(4);

            buildingsDestroyed = num_improvements == 0 ? null : new Byte2ByteArrayMap();
            if (input.readBit()) {
                for (int i = 0; i < num_improvements; i++) {
                    byte typeId = (byte) input.readBits(5);
                    buildingsDestroyed.compute(typeId, (k, v) -> v == null ? (byte) 1 : (byte) (v + 1));
                }
            }
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            num_improvements = 0;
            buildingsDestroyed = null;
        }
    }


    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);
        success = SuccessType.values[attack.getSuccess()];
        if (getSuccess() != SuccessType.UTTER_FAILURE) {
            city_id = attack.getCity_id();
            city_infra_before_cents = (int) (attack.getCity_infra_before() * 100);
            infra_destroyed_cents = (int) (attack.getInfra_destroyed() * 100);
            num_improvements = 0;
            List<String> destroyedNames = attack.getImprovements_destroyed();
            buildingsDestroyed = destroyedNames.isEmpty() ? null : new Byte2ByteArrayMap();
            for (String impName : destroyedNames) {
                Building building = Buildings.fromV3(impName);
                if (building == null) {
                    throw new IllegalStateException("Unknown improvement: " + impName);
                }
                num_improvements++;
                buildingsDestroyed.compute((byte) building.ordinal(), (k, v) -> v == null ? (byte) 1 : (byte) (v + 1));
            }
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            num_improvements = 0;
            buildingsDestroyed = null;
        }
    }

    @Override
    public final double[] getLoot() {
        return null;
    }

    @Override
    public final double getLootPercent() {
        return 0;
    }
}
