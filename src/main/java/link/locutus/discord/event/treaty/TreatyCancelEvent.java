package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyCancelEvent extends TreatyChangeEvent {
    public TreatyCancelEvent(Treaty treaty) {
        super(treaty, null);
    }
}
