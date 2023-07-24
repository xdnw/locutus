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

public abstract class UnitCursor extends DamageCursor {
    private int att_mun_used_cents;
    private int att_gas_used_cents;
    private int def_mun_used_cents;
    private int def_gas_used_cents;

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);

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
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
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
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);

        att_mun_used_cents = (int) (attack.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) (attack.getDef_mun_used() * 100);
        att_gas_used_cents = (int) (attack.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) (attack.getDef_gas_used() * 100);
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
