package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.SuccessType;

public abstract class UFCursor extends AbstractCursor {
    @Override
    public SuccessType getSuccess() {
        return SuccessType.PYRRHIC_VICTORY;
    }
}
