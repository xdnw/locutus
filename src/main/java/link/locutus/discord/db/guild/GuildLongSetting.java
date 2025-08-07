package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.MathMan;

public abstract class GuildLongSetting extends GuildSetting<Long> {
    public GuildLongSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup) {
        this(category, subgroup, null);
    }
    public GuildLongSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup, Class annotation) {
        super(category, subgroup, annotation != null ? Key.of(Long.class, annotation) : Key.of(Long.class));
    }

    @Override
    public String toReadableString(GuildDB db, Long value) {
        return MathMan.format(value);
    }

    @Override
    public final String toString(Long value) {
        return value.toString();
    }
}
