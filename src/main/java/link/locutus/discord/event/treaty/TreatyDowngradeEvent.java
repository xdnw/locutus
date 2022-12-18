package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyDowngradeEvent extends TreatyChangeEvent {

    public TreatyDowngradeEvent(Treaty previous, Treaty current) {
        super(previous, current);
    }
}
