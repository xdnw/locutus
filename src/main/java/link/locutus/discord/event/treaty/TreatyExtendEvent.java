package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyExtendEvent extends TreatyChangeEvent {

    public TreatyExtendEvent(Treaty previous, Treaty current) {
        super(previous, current);
    }
}
