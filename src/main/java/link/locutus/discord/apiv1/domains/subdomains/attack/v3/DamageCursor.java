package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.bytes.Byte2ByteArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public abstract class DamageCursor extends AbstractCursor{

    protected SuccessType success;
    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private Map<Byte, Byte> buildingsDestroyed = new Byte2ByteArrayMap();
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
        city_infra_before_cents = (int) Math.round(legacy.getCity_infra_before() * 100);
        infra_destroyed_cents = (int) Math.round(legacy.getInfra_destroyed() * 100);
        num_improvements = legacy.getImprovements_destroyed();
        buildingsDestroyed.clear();
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
    public abstract int getUnitLosses(MilitaryUnit unit, boolean attacker);

    @Override
    public double[] getUnitLossCost(double[] buffer, boolean isAttacker) {
        for (MilitaryUnit unit : getUnits()) {
            int losses = getUnitLosses(unit, isAttacker);
            if (losses > 0) {
                for (Map.Entry<ResourceType, Double> entry : unit.getCostMap().entrySet()) {
                    buffer[entry.getKey().ordinal()] += entry.getValue() * losses;
                }
            }
        }
        return buffer;
    }

    @Override
    public int[] getUnitLosses(int[] buffer, boolean isAttacker) {
        for (MilitaryUnit unit : getUnits()) {
            int losses = getUnitLosses(unit, isAttacker);
            if (losses > 0) {
                buffer[unit.ordinal()] += losses;
            }
        }
        return buffer;
    }

    @Override
    public int getAttcas1() {
        MilitaryUnit[] units = getUnits();
        return getUnitLosses(units[0], true);
    }

    @Override
    public int getDefcas1() {
        MilitaryUnit[] units = getUnits();
        return getUnitLosses(units[0], false);
    }

    @Override
    public int getAttcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getUnitLosses(units[1], true) : 0;
    }

    @Override
    public int getDefcas2() {
        MilitaryUnit[] units = getUnits();
        return units.length > 1 ? getUnitLosses(units[1], false) : 0;
    }

    @Override
    public int getDefcas3() {
        MilitaryUnit[] units = getUnits();
        return units.length > 2 ? getUnitLosses(units[2], false) : 0;
    }

    @Override
    public Map<Building, Integer> getBuildingsDestroyed2() {
        if (num_improvements == 0 || buildingsDestroyed.isEmpty()) {
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
    public double[] getBuildingCost(double[] buffer) {
        if (num_improvements != 0 && !buildingsDestroyed.isEmpty()) {
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                Building building = Buildings.get(typeId);
                for (ResourceType type : ResourceType.values) {
                    double rssCost = building.cost(type);
                    if (rssCost > 0) {
                        buffer[type.ordinal()] += rssCost * amt;
                    }
                }
            }
        }
        return buffer;
    }

    @Override
    public void addBuildingsDestroyed(int[] destroyedBuffer) {
        if (num_improvements > 0) {
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
    public void serialze(BitBuffer output) {
        super.serialze(output);
        output.writeBits(success.ordinal(), 2);

        if (success != SuccessType.UTTER_FAILURE) {
            output.writeVarInt(city_id);
            output.writeVarInt(city_infra_before_cents);
            output.writeVarInt(infra_destroyed_cents);
            output.writeBits(num_improvements, 4);
            if (buildingsDestroyed.size() != num_improvements) {
                output.writeBit(false);
            } else {
                // 26 types of buildings (2^5)
                output.writeBit(true);
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

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (success != SuccessType.UTTER_FAILURE) {
            city_id = input.readVarInt();
            city_infra_before_cents = (int) input.readVarInt();
            infra_destroyed_cents = (int) input.readVarInt();
            num_improvements = (int) input.readBits(4);

            buildingsDestroyed.clear();
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
            buildingsDestroyed.clear();
        }
    }


    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = SuccessType.values[attack.getSuccess()];
        if (getSuccess() != SuccessType.UTTER_FAILURE) {
            city_id = attack.getCity_id();
            city_infra_before_cents = (int) (attack.getCity_infra_before() * 100);
            infra_destroyed_cents = (int) (attack.getInfra_destroyed() * 100);
            num_improvements = 0;
            buildingsDestroyed.clear();
            for (String impName : attack.getImprovements_destroyed()) {
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
            buildingsDestroyed.clear();
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
