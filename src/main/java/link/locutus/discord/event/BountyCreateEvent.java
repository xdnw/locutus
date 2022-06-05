package link.locutus.discord.event;

import link.locutus.discord.db.entities.DBBounty;

public class BountyCreateEvent extends Event {
    public final DBBounty bounty;

    public BountyCreateEvent(DBBounty bounty) {
        this.bounty = bounty;
    }
}
