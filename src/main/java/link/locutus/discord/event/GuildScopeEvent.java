package link.locutus.discord.event;

import link.locutus.discord.db.GuildDB;

public abstract class GuildScopeEvent extends Event {
    protected abstract void postToGuilds();

    public final void post(GuildDB db) {
        if (db != null) db.postEvent(this);
    }

    @Override
    public void post() {
        super.post();
        postToGuilds();
    }
}