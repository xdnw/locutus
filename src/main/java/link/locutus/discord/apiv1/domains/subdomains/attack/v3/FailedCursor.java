package link.locutus.discord.apiv1.domains.subdomains.attack.v3;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;

public abstract class FailedCursor extends AbstractCursor {
    @Override
    public SuccessType getSuccess() {
        return SuccessType.UTTER_FAILURE;
    }
}
