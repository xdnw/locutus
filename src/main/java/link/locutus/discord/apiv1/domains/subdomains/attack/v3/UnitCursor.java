package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import com.politicsandwar.graphql.model.WarAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.util.io.BitBuffer;

import java.util.Map;

public abstract class UnitCursor extends AbstractCursor {
    private short att_gas_used_cents;
    private short att_mun_used_cents;
    private short def_gas_used_cents;
    private short def_mun_used_cents;
    private int city_id;
    private int city_infra_before_cents;
    private int infra_destroyed_cents;
    private byte improvements_destroyed;

    @Override
    public void load(WarAttack attack) {

    }

    public abstract MilitaryUnit[] getUnits();
    public abstract int getUnitLosses(MilitaryUnit unit, boolean attacker);

    @Override
    public void serialze(BitBuffer output) {
        if (att_mun_used_cents != 0) {
            output.writeBit(true);
        }
    }
}
