package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyExpireEvent extends TreatyChangeEvent {
    public TreatyExpireEvent(Treaty treaty) {
        super(treaty, null);
    }
}
