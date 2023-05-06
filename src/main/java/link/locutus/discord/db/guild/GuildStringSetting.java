package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;

public abstract class GuildStringSetting extends GuildSetting<String> {
    public GuildStringSetting(GuildSettingCategory category) {
        super(category, Key.of(String.class));
    }

    @Override
    public final String toString(String value) {
        return value.replace("\\n", "\n");
    }
}
