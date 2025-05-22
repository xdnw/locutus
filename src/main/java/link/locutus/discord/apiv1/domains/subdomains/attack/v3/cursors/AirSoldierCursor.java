package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public class AirSoldierCursor extends UnitCursor {
    private int attcas1;
    private int defcas1;
    private int defcas2;

    @Override
    public String toString() {
        return "AirSoldierCursor{" +
                "attcas1=" + attcas1 +
                ", defcas1=" + defcas1 +
                ", defcas2=" + defcas2 +
                ", success=" + success +
                ", war_attack_id=" + war_attack_id +
                ", date=" + date +
                ", war_id=" + war_id +
                ", attacker_id=" + attacker_id +
                ", defender_id=" + defender_id +
                '}';
    }

    @Override
    public void load(DBAttack legacy) {
        super.load(legacy);
        this.attcas1 = legacy.getAttcas1();
        this.defcas1 = legacy.getDefcas1();
        this.defcas2 = legacy.getDefcas2();
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.AIRSTRIKE_SOLDIER;
    }

    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);
        this.attcas1 = attack.getAtt_aircraft_lost();
        this.defcas1 = attack.getDef_aircraft_lost();
        this.defcas2 = attack.getDef_soldiers_lost();
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.AIRCRAFT, MilitaryUnit.SOLDIER};

    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getAttUnitLosses(MilitaryUnit unit) {
        return unit == MilitaryUnit.AIRCRAFT ? attcas1 : 0;
    }

    @Override
    public int getDefUnitLosses(MilitaryUnit unit) {
        return switch (unit) {
            case AIRCRAFT -> defcas1;
            case SOLDIER -> defcas2;
            default -> 0;
        };
    }
    @Override
    public double getMoney_looted() {
        return 0;
    }
    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);

        if (input.readBit()) attcas1 = input.readVarInt();
        else attcas1 = 0;

        if (input.readBit()) defcas1 = input.readVarInt();
        else defcas1 = 0;

        if (input.readBit()) defcas2 = input.readVarInt();
        else defcas2 = 0;
    }

    @Override
    public void serialize(BitBuffer output) {
        super.serialize(output);

        output.writeBit(attcas1 > 0);
        if (attcas1 > 0) output.writeVarInt(attcas1);


        output.writeBit(defcas1 > 0);
        if (defcas1 > 0) output.writeVarInt(defcas1);

        output.writeBit(defcas2 > 0);
        if (defcas2 > 0) output.writeVarInt(defcas2);
    }
}
