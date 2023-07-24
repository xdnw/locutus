package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.io.BitBuffer;

public abstract class ProjectileCursor extends AbstractCursor {
    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private byte improvements_destroyed;
    private boolean success;

    @Override
    public SuccessType getSuccess() {
        return success ? SuccessType.PYRRHIC_VICTORY : SuccessType.UTTER_FAILURE;
    }

    public abstract MilitaryUnit getUnitType();

    @Override
    public void load(WarAttack attack) {
        super.load(attack);
        success = attack.getSuccess() > 0;
        if (success) {
            city_id = attack.getCity_id();
            city_infra_before_cents = (int) (attack.getCity_infra_before() * 100);
            infra_destroyed_cents = (int) (attack.getInfra_destroyed() * 100);
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
        }
    }

    @Override
    public void load(DBWar war, BitBuffer input) {
        super.load(war, input);
        success = input.readBit();
        if (success) {
            city_id = input.readInt();
            city_infra_before_cents = input.readVarInt();
            infra_destroyed_cents = input.readVarInt();
            // 0 - 4
            improvements_destroyed = (byte) input.readBits(4);
        } else {
            city_id = 0;
            city_infra_before_cents = 0;
            infra_destroyed_cents = 0;
            improvements_destroyed = 0;
        }
    }

    @Override
    public void serialze(BitBuffer output) {
        super.serialze(output);

        output.writeBit(success);
        if (success) {
            output.writeInt(city_id);
            output.writeVarInt(city_infra_before_cents);
            output.writeVarInt(infra_destroyed_cents);
            output.writeBits(improvements_destroyed, 4);
        }
    }
}
