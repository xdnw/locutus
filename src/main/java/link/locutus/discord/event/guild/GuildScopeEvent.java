package link.locutus.discord.event.guild;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.event.Event;

public abstract class GuildScopeEvent extends Event {

    public GuildScopeEvent(long time) {
        super(time);
    }

    public GuildScopeEvent() {
        super();
    }

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