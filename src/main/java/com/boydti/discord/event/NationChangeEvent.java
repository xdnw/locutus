package com.boydti.discord.event;

import com.boydti.discord.Locutus;
import com.boydti.discord.pnw.DBNation;

public class NationChangeEvent extends GuildScopeEvent {
    private final DBNation previous;
    private final DBNation current;

    public NationChangeEvent(DBNation previous, DBNation current) {
        this.previous = previous;
        this.current = current;
    }

    public DBNation getPrevious() {
        return previous;
    }

    public DBNation getCurrent() {
        return current;
    }

    @Override
    public void postToGuilds() {
        if (current != null) {
            post(Locutus.imp().getGuildDBByAA(current.getAlliance_id()));
        }
        if (previous != null && (current == null || current.getAlliance_id() != previous.getAlliance_id())) {
            post(Locutus.imp().getGuildDBByAA(previous.getAlliance_id()));
        }
    }
}
