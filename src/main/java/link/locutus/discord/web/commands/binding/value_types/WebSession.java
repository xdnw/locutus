package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Guild;
import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;

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
    public @Nullable List<Integer> guild_alliances;
    public @Nullable List<String> guild_alliances_names;
    public long delegates_to;
    public @Nullable String delegate_server_name;
    public long fa_server;
    public @Nullable String fa_server_name;
    public long ma_server;
    public @Nullable String ma_server_name;

    public WebSession() {
    }

    public void setGuild(Guild guild) {
        this.guild = guild.getId();
        this.guild_name = guild.getName();
        this.guild_icon = guild.getIconUrl();
    }
}
