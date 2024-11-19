package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Guild;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WebSession {
    public @Nullable String user;
    public @Nullable String user_name;
    public @Nullable String user_icon;
    public @Nullable Boolean user_valid;
    public @Nullable Integer nation;
    public @Nullable String nation_name;
    public @Nullable Integer alliance;
    public @Nullable String alliance_name;
    public @Nullable Boolean nation_valid;
    public Long expires;
    public @Nullable String guild;
    public @Nullable String guild_name;
    public @Nullable String guild_icon;
    public @Nullable Boolean registered;
    public @Nullable Integer registered_nation;

    public WebSession() {
    }

    public void setGuild(Guild guild) {
        this.guild = guild.getId();
        this.guild_name = guild.getName();
        this.guild_icon = guild.getIconUrl();
    }
}
