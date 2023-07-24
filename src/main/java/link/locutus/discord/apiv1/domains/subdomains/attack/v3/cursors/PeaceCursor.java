package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public class PeaceCursor extends FailedCursor {

    @Override
    public AttackType getAttackType() {
        return AttackType.PEACE;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
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
