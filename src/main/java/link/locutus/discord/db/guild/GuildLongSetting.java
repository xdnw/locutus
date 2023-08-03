package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.util.MathMan;

public abstract class GuildLongSetting extends GuildSetting<Long, Long> {
    public GuildLongSetting(GuildSettingCategory category) {
        this(category, null);
    }
    public GuildLongSetting(GuildSettingCategory category, Class annotation) {
        super(category, annotation != null ? Key.of(Long.class, annotation) : Key.of(Long.class));
    }

    @Override
    public String toReadableString(Long value) {
        return MathMan.format(value);
    }

    @Override
    public final String toString(Long value) {
        return value.toString();
    }
}
