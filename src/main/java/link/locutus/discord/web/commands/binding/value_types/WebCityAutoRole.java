package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Role;

public class WebCityAutoRole {
    public final long role_id;
    public final String name;
    public final int color;
    public final int range_start;
    public final int range_end;
    public final boolean duplicate_key;

    public WebCityAutoRole(Role role, int rangeStart, int rangeEnd) {
        this(role, rangeStart, rangeEnd, false);
    }

    public WebCityAutoRole(Role role, int rangeStart, int rangeEnd, boolean duplicateKey) {
        this.role_id = role.getIdLong();
        this.name = role.getName();
        this.color = role.getColorRaw();
        this.range_start = rangeStart;
        this.range_end = rangeEnd;
        this.duplicate_key = duplicateKey;
    }
}
