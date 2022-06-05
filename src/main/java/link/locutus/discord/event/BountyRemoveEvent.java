package link.locutus.discord.event;

import link.locutus.discord.db.entities.DBBounty;

public class BountyRemoveEvent extends Event {
    public final DBBounty bounty;

    public BountyRemoveEvent(DBBounty bounty) {
        this.bounty = bounty;
    }
}
