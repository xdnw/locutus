package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.db.GuildDB;

public abstract class GuildStringSetting extends GuildSetting<String> {
    public GuildStringSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup) {
        this(category, subgroup, null);
    }
    public GuildStringSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup, Class annotation) {
        super(category, subgroup, annotation != null ? Key.of(String.class, annotation) : Key.of(String.class));
    }

    @Override
    public String parse(GuildDB db, String input) {
        return super.parse(db, input);
    }

    @Override
    public final String toString(String value) {
        return value.replace("\\n", "\n");
    }
}
