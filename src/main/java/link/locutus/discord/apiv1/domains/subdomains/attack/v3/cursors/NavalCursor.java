package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public class NavalCursor extends UnitCursor {
    private int attcas1;
    private int defcas1;


    @Override
    public AttackType getAttack_type() {
        return AttackType.NAVAL;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        this.attcas1 = attack.getAtt_ships_lost();
        this.defcas1 = attack.getDef_ships_lost();
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.SHIP};
    @Override
    public double getMoney_looted() {
        return 0;
    }
    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getUnitLosses(MilitaryUnit unit, boolean attacker) {
        if (unit == MilitaryUnit.SHIP) {
            return attacker ? attcas1 : defcas1;
        }
        return 0;
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);

        if (input.readBit()) attcas1 = input.readVarInt();
        else attcas1 = 0;

        if (input.readBit()) defcas1 = input.readVarInt();
        else defcas1 = 0;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);

        output.writeBit(attcas1 > 0);
        if (attcas1 > 0) output.writeVarInt(attcas1);

        output.writeBit(defcas1 > 0);
        if (defcas1 > 0) output.writeVarInt(defcas1);
    }
}

