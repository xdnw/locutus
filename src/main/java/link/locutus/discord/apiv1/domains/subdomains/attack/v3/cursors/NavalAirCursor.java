package link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors;

import link.locutus.discord.apiv1.enums.AttackType;

public class NavalAirCursor extends NavalCursor {

    @Override
    public AttackType getAttack_type() {
        return AttackType.NAVAL_AIR;
    }
}

