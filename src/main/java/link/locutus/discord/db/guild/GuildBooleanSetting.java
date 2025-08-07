package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;

public abstract class GuildBooleanSetting extends GuildSetting<Boolean> {
    public GuildBooleanSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup) {
        super(category, subgroup, Key.of(Boolean.class));
    }

    @Override
    public final String toString(Boolean value) {
        return value.toString();
    }
}
