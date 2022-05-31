package com.boydti.discord.event;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;

import java.util.Collection;
import java.util.function.Supplier;

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