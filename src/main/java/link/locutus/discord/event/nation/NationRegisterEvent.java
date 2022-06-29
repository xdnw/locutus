package link.locutus.discord.event.nation;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.event.guild.GuildScopeEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class NationRegisterEvent extends GuildScopeEvent {
    private final int nationId;
    private final Guild guild;
    private final User user;
    private final boolean isNew;
    public NationRegisterEvent(int nationId, Guild guild, User user, boolean isNew) {
        this.nationId = nationId;
        this.guild = guild;
        this.user = user;
        this.isNew = isNew;
    }

    public boolean isNew() {
        return isNew;
    }

    public int getNationId() {
        return nationId;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }

    @Override
    protected void postToGuilds() {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db != null) {
            post(db);
        }
        if (isNew) {
            for (Guild otherGuild : user.getMutualGuilds()) {
                if (otherGuild.getIdLong() == this.guild.getIdLong()) continue;
                GuildDB otherDb = Locutus.imp().getGuildDB(otherGuild);
                if (otherDb != null) {
                    post(otherDb);
                }
            }
        }
    }
}
