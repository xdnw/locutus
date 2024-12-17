package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Role;

public class DiscordRole {
    public final String name;
    public final int color;

    public DiscordRole(String name, int color) {
        this.name = name;
        this.color = color;
    }

    public DiscordRole(Role role) {
        this.name = role.getName();
        this.color = role.getColorRaw();
    }
}
