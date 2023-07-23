package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.io.BitBuffer;

public class VictoryCursor extends UnitCursor {
    public double[] looted = ResourceType.getBuffer();
    public boolean hasLoot = false;
    private int loot_percent_cents;

    @Override
    public AttackType getAttackType() {
        return AttackType.VICTORY;
    }

    @Override
    public SuccessType getSuccess() {
        return SuccessType.UTTER_FAILURE;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        if (attack.getInfra_destroyed() != null) {
            new Exception().printStackTrace();
            System.out.println("Infra is destroyed in victory");
        }
        attack.
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        // add current

    }
}
