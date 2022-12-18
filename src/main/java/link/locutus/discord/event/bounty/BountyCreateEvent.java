package link.locutus.discord.event.bounty;

import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.event.Event;

public class BountyCreateEvent extends Event {
    public final DBBounty bounty;

    public BountyCreateEvent(DBBounty bounty) {
        this.bounty = bounty;
    }
}
