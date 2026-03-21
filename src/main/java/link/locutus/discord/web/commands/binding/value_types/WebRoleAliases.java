package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;
import java.util.Map;

public class WebRoleAliases {
    public final Map<Integer, Map<Long, Long>> mappings;

    public final List<Integer> invalid_role_ordinals;
    public final List<Integer> allows_alliance;
    public final Map<Integer, Integer> requiresSettings; // map of role ordinal to setting ordinal

    public final Map<Long, String> discord_role_names;

    public WebRoleAliases(Map<Integer, Map<Long, Long>> mappings,
                          List<Integer> invalidRoleOrdinals,
                          List<Integer> allows_alliance,
                          Map<Integer, Integer> requiresSettings,
                          Map<Long, String> discordRoleNames) {
        this.mappings = mappings;
        this.invalid_role_ordinals = invalidRoleOrdinals;
        this.allows_alliance = allows_alliance;
        this.requiresSettings = requiresSettings;
        this.discord_role_names = discordRoleNames;
    }
}