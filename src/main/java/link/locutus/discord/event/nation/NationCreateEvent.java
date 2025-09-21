package link.locutus.discord.event.nation;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;

public class NationCreateEvent extends NationChangeEvent2 {
    public NationCreateEvent(DBNation original, DBNation changed) {
        super(original, changed);
        setTime(changed.getDate());
    }

    @Override
    protected void postToGuilds() {
        super.postToGuilds();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            try {
                db.getHandler().onGlobalNationCreate(this);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }
}
