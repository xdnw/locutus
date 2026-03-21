package link.locutus.discord.web.commands.binding.value_types;

import net.dv8tion.jda.api.entities.Role;

public class WebAllianceAutoRole {
    public final long role_id;
    public final String name;
    public final int color;
    public final int alliance_id;
    public final boolean duplicate_key;

    public WebAllianceAutoRole(Role role, int allianceId) {
        this(role, allianceId, false);
    }

    public WebAllianceAutoRole(Role role, int allianceId, boolean duplicateKey) {
        this.role_id = role.getIdLong();
        this.name = role.getName();
        this.color = role.getColorRaw();
        this.alliance_id = allianceId;
        this.duplicate_key = duplicateKey;
    }
}
