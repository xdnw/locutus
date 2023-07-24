package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.FailedCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.UnitCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Arrays;

public class ALootCursor extends FailedCursor {
    public boolean hasLoot = false;
    public double[] looted = ResourceType.getBuffer();
    private int loot_percent_cents;

    @Override
    public AttackType getAttack_type() {
        return AttackType.A_LOOT;
    }

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        // load resources
        hasLoot = input.readBit();
        if (hasLoot) {
            loot_percent_cents = input.readVarInt();
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                looted[type.ordinal()] = input.readVarLong() * 0.01d;
            }
        } else {
            loot_percent_cents = 0;
        }
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);
        // add current
        output.writeBit(hasLoot);
        if (hasLoot) {
            output.writeVarInt(loot_percent_cents);
            output.writeVarLong(infra_destroyed_value_cents);
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                output.writeVarLong((long) (looted[type.ordinal()] * 100));
            }
        }

    }
}
