package link.locutus.discord.event.nation;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.event.guild.GuildScopeEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class NationRegisterEvent extends GuildScopeEvent {
    private final int nationId;
    private final GuildDB db;
    private final User user;
    private final boolean isNew;
    public NationRegisterEvent(int nationId, GuildDB db, User user, boolean isNew) {
        this.nationId = nationId;
        this.db = db;
        this.user = user;
        this.isNew = isNew;
    }

    public boolean isNew() {
        return isNew;
    }

    public int getNationId() {
        return nationId;
    }

    public GuildDB getGuildDB() {
        return db;
    }

    public User getUser() {
        return user;
    }

    @Override
    protected void postToGuilds() {
        if (db != null) {
            post(db);
        }
        if (isNew && user != null) {
            for (Guild otherGuild : Locutus.imp().getDiscordApi().getMutualGuilds(user)) {
                if (db != null && otherGuild.getIdLong() == this.db.getIdLong()) continue;
                GuildDB otherDb = Locutus.imp().getGuildDB(otherGuild);
                if (otherDb != null) {
                    post(otherDb);
                }
            }
        }
    }
}
