package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public class GroundCursor extends UnitCursor {
    private int attcas1;
    private int attcas2;
    private int defcas1;
    private int defcas2;
    private int defcas3;
    private long money_looted_cents;

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        this.attcas1 = legacy.getAttcas1();
        this.attcas2 = legacy.getAttcas2();
        this.defcas1 = legacy.getDefcas1();
        this.defcas2 = legacy.getDefcas2();
        this.defcas3 = legacy.getDefcas3();
        this.money_looted_cents = (long) Math.round(legacy.getMoney_looted() * 100);
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.GROUND;
    }

    @Override
    public double getMoney_looted() {
        return money_looted_cents * 0.01;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        this.attcas1 = attack.getAtt_soldiers_lost();
        this.attcas2 = attack.getAtt_tanks_lost();
        this.defcas1 = attack.getDef_soldiers_lost();
        this.defcas2 = attack.getDef_tanks_lost();
        this.defcas3 = attack.getDef_aircraft_lost();
        this.money_looted_cents = (long) (attack.getMoney_looted() * 100);
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT};

    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getUnitLosses(MilitaryUnit unit, boolean attacker) {
        return switch (unit) {
            case SOLDIER -> attacker ? attcas1 : defcas1;
            case TANK -> attacker ? attcas2 : defcas2;
            case AIRCRAFT -> attacker ? 0 : defcas3;
            default -> 0;
        };
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        if (input.readBit()) attcas1 = input.readVarInt();
        else attcas1 = 0;

        if (input.readBit()) attcas2 = input.readVarInt();
        else attcas2 = 0;

        if (input.readBit()) defcas1 = input.readVarInt();
        else defcas1 = 0;

        if (input.readBit()) defcas2 = input.readVarInt();
        else defcas2 = 0;

        if (input.readBit()) defcas3 = input.readVarInt();
        else defcas3 = 0;

        if (success != SuccessType.UTTER_FAILURE) {
            if (input.readBit()) money_looted_cents = input.readVarLong();
            else money_looted_cents = 0;
        }
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        output.writeBit(attcas1 > 0);
        if (attcas1 > 0) output.writeVarInt(attcas1);

        output.writeBit(attcas2 > 0);
        if (attcas2 > 0) output.writeVarInt(attcas2);

        output.writeBit(defcas1 > 0);
        if (defcas1 > 0) output.writeVarInt(defcas1);

        output.writeBit(defcas2 > 0);
        if (defcas2 > 0) output.writeVarInt(defcas2);

        output.writeBit(defcas3 > 0);
        if (defcas3 > 0) output.writeVarInt(defcas3);

        if (success != SuccessType.UTTER_FAILURE) {
            output.writeBit(money_looted_cents > 0);
            if (money_looted_cents > 0) output.writeVarLong(money_looted_cents);
        }
    }
}
