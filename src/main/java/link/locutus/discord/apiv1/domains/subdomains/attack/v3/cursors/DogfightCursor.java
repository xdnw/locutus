package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public class DogfightCursor extends UnitCursor {
    private SuccessType success;
    private int attcas1;
    private int defcas1;


    @Override
    public AttackType getAttackType() {
        return AttackType.AIRSTRIKE_AIRCRAFT;
    }

    @Override
    public SuccessType getSuccess() {
        return success;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = SuccessType.values[attack.getSuccess()];
        this.attcas1 = attack.getAtt_soldiers_lost();
        this.defcas1 = attack.getDef_soldiers_lost();
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.AIRCRAFT};

    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getUnitLosses(MilitaryUnit unit, boolean attacker) {
        return switch (unit) {
            case AIRCRAFT -> attacker ? attcas1 : defcas1;
            default -> 0;
        };
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = SuccessType.values[(int) input.readBits(2)];

        if (input.readBit()) attcas1 = input.readVarInt();
        else attcas1 = 0;

        if (input.readBit()) defcas1 = input.readVarInt();
        else defcas1 = 0;
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        // success = 0,1,2,3
        output.writeBits(success.ordinal(), 2);

        output.writeBit(attcas1 > 0);
        if (attcas1 > 0) output.writeVarInt(attcas1);


        output.writeBit(defcas1 > 0);
        if (defcas1 > 0) output.writeVarInt(defcas1);
    }
}
