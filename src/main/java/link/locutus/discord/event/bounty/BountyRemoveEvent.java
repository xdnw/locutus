package link.locutus.discord.event.bounty;

import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.event.Event;

public class BountyRemoveEvent extends Event {
    public final DBBounty bounty;

    public BountyRemoveEvent(DBBounty bounty) {
        this.bounty = bounty;
    }
}
