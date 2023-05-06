package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;

public abstract class GuildLongSetting extends GuildSetting<Long> {
    public GuildLongSetting(GuildSettingCategory category) {
        this(category, null);
    }
    public GuildLongSetting(GuildSettingCategory category, Class annotation) {
        super(category, annotation != null ? Key.of(Long.class, annotation) : Key.of(Long.class));
    }

    @Override
    public final String toString(Long value) {
        return value.toString();
    }
}
