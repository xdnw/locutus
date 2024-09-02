package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.BitBuffer;

import java.util.HashMap;
import java.util.Map;

public abstract class UnitCursor extends DamageCursor {
    private int att_mun_used_cents;
    private int att_gas_used_cents;
    private int def_mun_used_cents;
    private int def_gas_used_cents;
    private boolean has_salvage;

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        att_mun_used_cents = (int) Math.round(legacy.getAtt_mun_used() * 100);
        def_mun_used_cents = (int) Math.round(legacy.getDef_mun_used() * 100);
        att_gas_used_cents = (int) Math.round(legacy.getAtt_gas_used() * 100);
        def_gas_used_cents = (int) Math.round(legacy.getDef_gas_used() * 100);
        has_salvage = false;
    }

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
            }
        }

        if (input.readBit()) {
            def_mun_used_cents = (int) input.readVarInt();
            if (input.readBit()) {
                def_gas_used_cents = (int) input.readVarInt();
            }
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

        has_salvage = (success != SuccessType.UTTER_FAILURE && attack.getMilitary_salvage_aluminum() > 0 && attack.getMilitary_salvage_steel() > 0);
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
