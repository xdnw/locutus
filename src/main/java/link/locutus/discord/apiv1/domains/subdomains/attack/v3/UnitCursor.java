package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.bytes.Byte2ByteArrayMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public abstract class UnitCursor extends AbstractCursor {
    private int att_mun_used_cents;
    private int att_gas_used_cents;
    private int def_mun_used_cents;
    private int def_gas_used_cents;

    protected SuccessType success;

    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private Map<Byte, Byte> buildingsDestroyed = new Byte2ByteArrayMap();
    private int num_improvements;

    @Override
    public final SuccessType getSuccess() {
        return success;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        output.writeBits(success.ordinal(), 2);

        output.writeBit(att_mun_used_cents > 0);
        if (att_mun_used_cents > 0) {
            output.writeVarInt(att_mun_used_cents);

            output.writeBit(att_gas_used_cents > 0);
            if (att_gas_used_cents > 0) {
                output.writeVarInt(att_gas_used_cents);
            }
        }

        output.writeBit(def_mun_used_cents > 0);
        if (def_mun_used_cents > 0) {
            output.writeVarInt(def_mun_used_cents);

            output.writeBit(def_gas_used_cents > 0);
            if (def_gas_used_cents > 0) {
                output.writeVarInt(def_gas_used_cents);
            }
        }

        if (success != SuccessType.UTTER_FAILURE) {
            output.writeInt(city_id);
            output.writeVarInt(city_infra_before_cents);
            output.writeVarInt(infra_destroyed_cents);
            output.writeBits(num_improvements, 4);

            // 26 types of buildings (2^5)
            for (Map.Entry<Byte, Byte> entry : buildingsDestroyed.entrySet()) {
                byte typeId = entry.getKey();
                byte amt = entry.getValue();
                for (int i = 0; i < amt; i++) {
                    output.writeBits(typeId, 5);
                }
            }
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (input.readBit()) {
            att_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                att_gas_used_cents = (int) input.readVarInt();
            }
        }

        if (input.readBit()) {
            def_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                def_gas_used_cents = (int) input.readVarInt();
            }
        }

        if (success != SuccessType.UTTER_FAILURE) {
            city_id = input.readInt();
            city_infra_before_cents = (int) input.readVarInt();
            infra_destroyed_cents = (int) input.readVarInt();
            num_improvements = (int) input.readBits(4);

            buildingsDestroyed.clear();
            for (int i = 0; i < num_improvements; i++) {
                byte typeId = (byte) input.readBits(5);
                buildingsDestroyed.compute(typeId, (k, v) -> v == null ? (byte) 1 : (byte) (v + 1));
            }
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            num_improvements = 0;
            buildingsDestroyed.clear();
        }
    }

    public abstract MilitaryUnit[] getUnits();
    public abstract int getUnitLosses(MilitaryUnit unit, boolean attacker);

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = SuccessType.values[attack.getSuccess()];

        att_mun_used_cents = (int) (attack.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) (attack.getDef_mun_used() * 100);
        att_gas_used_cents = (int) (attack.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) (attack.getDef_gas_used() * 100);
        if (getSuccess() != SuccessType.UTTER_FAILURE) {
            city_id = attack.getCity_id();
            city_infra_before_cents = (int) (attack.getCity_infra_before() * 100);
            infra_destroyed_cents = (int) (attack.getInfra_destroyed() * 100);
            num_improvements = 0;
            buildingsDestroyed.clear();
            for (String impName : attack.getImprovements_destroyed()) {
                Building building = Buildings.get(impName);
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


}
