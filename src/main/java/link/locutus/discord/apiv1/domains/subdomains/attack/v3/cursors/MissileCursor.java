package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.ProjectileCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public class MissileCursor extends ProjectileCursor {

    @Override
    public AttackType getAttack_type() {
        return AttackType.MISSILE;
    }

    private static final MilitaryUnit[] UNITS = {MilitaryUnit.MISSILE};

    @Override
    public MilitaryUnit[] getUnits() {
        return UNITS;
    }

    @Override
    public int getAttUnitLosses(MilitaryUnit unit) {
        return unit == MilitaryUnit.MISSILE ? 1 : 0;
    }

    @Override
    public void load(WarAttack attack, WarDB db) {
        super.load(attack, db);
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
    }
}
