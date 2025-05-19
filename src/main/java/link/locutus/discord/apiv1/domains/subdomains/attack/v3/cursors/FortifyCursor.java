package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Collections;
import java.util.Set;

public class FortifyCursor extends FailedCursor {

    @Override
    public SuccessType getSuccess() {
        return SuccessType.PYRRHIC_VICTORY;
    }

    @Override
    public AttackType getAttack_type() {
        return AttackType.FORTIFY;
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
    public Set<Integer> getCityIdsDamaged() {
        return Collections.emptySet();
    }

    @Override
    public void serialize(BitBuffer output) {
        super.serialize(output);
    }
}
