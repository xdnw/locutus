package link.locutus.discord.event.city;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class CityChangeEvent extends GuildScopeEvent { // post to guilds with that nation
    private final DBCity previous;
    private final DBCity current;
    private final int nation;

    public CityChangeEvent(int nation, DBCity previous, DBCity current) {
        this.nation = nation;
        this.previous = previous;
        this.current = current;
    }

    public int getNationId() {
        return nation;
    }

    public DBNation getNation() {
        return DBNation.getById(nation);
    }

    public DBCity getPrevious() {
        return previous;
    }

    public DBCity getCurrent() {
        return current;
    }

    @Override
    protected void postToGuilds() {
        DBNation nationObj = getNation();
        if (nationObj != null) {
            int aaId = nationObj.getAlliance_id();
            post(Locutus.imp().getGuildDBByAA(aaId));
        }
    }
}
