package link.locutus.discord.event;

import link.locutus.discord.Locutus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public class NewApplicantOnDiscordEvent extends GuildScopeEvent {
    private final User user;
    private final Guild guild;

    public NewApplicantOnDiscordEvent(Guild guild, User user) {
        this.guild = guild;
        this.user = user;
    }

    public Guild getGuild() {
        return guild;
    }

    public User getUser() {
        return user;
    }

    @Override
    public void postToGuilds() {
        post(Locutus.imp().getGuildDB(guild));
    }
}
