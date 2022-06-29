package link.locutus.discord.event.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class AllianceChangeEvent extends GuildScopeEvent {
    private final DBAlliance current;
    private final DBAlliance previous;

    public AllianceChangeEvent(DBAlliance previous, DBAlliance current) {
        this.previous = previous;
        this.current = current;
    }

    public DBAlliance getPrevious() {
        return previous;
    }

    public DBAlliance getCurrent() {
        return current;
    }

    @Override
    protected void postToGuilds() {
        int aaId = current != null ? current.getAlliance_id() : previous.getAlliance_id();
        if (aaId != 0) {
            post(Locutus.imp().getGuildDBByAA(aaId));
        }
    }
}
