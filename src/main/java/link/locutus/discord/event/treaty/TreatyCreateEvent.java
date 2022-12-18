package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyCreateEvent extends TreatyChangeEvent {
    public TreatyCreateEvent(Treaty treaty) {
        super(null, treaty);
    }
}
