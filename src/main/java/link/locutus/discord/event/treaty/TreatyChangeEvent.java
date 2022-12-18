package link.locutus.discord.event.treaty;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class TreatyChangeEvent extends GuildScopeEvent {
    private final Treaty previous;
    private final Treaty current;

    public TreatyChangeEvent(Treaty previous, Treaty current) {
        this.previous = previous;
        this.current = current;
    }

    public Treaty getPrevious() {
        return previous;
    }

    public Treaty getCurrent() {
        return current;
    }

    @Override
    protected void postToGuilds() {
        Treaty nonNull = previous != null ? previous : current;
        post(Locutus.imp().getGuildDBByAA(nonNull.getFromId()));
        post(Locutus.imp().getGuildDBByAA(nonNull.getToId()));
    }
}
