package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Guild;
import org.checkerframework.checker.nullness.qual.Nullable;

public class WebSession extends WebSuccess {
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
        super(true, null);
    }

    public void setGuild(Guild guild) {
        this.guild = guild.getId();
        this.guild_name = guild.getName();
        this.guild_icon = guild.getIconUrl();
    }

    // ts example interface
//    export interface SessionData {
//        user: string | null;
//        user_name?: string;
//        user_icon?: string;
//        user_valid: boolean | null;
//        nation: number | null;
//        nation_name?: string;
//        alliance?: number | null;
//        alliance_name?: string;
//        nation_valid: boolean | null;
//        expires: string | null;
//        guild: string | null;
//        guild_name?: string;
//        guild_icon?: string;
//        registered?: boolean;
//        registered_nation?: number;
//    }
}
