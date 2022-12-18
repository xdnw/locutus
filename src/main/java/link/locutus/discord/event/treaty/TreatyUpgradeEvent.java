package link.locutus.discord.event.treaty;

import link.locutus.discord.db.entities.Treaty;

public class TreatyUpgradeEvent extends TreatyChangeEvent {

    public TreatyUpgradeEvent(Treaty previous, Treaty current) {
        super(previous, current);
    }
}
