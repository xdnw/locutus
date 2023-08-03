package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.db.GuildDB;

public abstract class GuildStringSetting extends GuildSetting<String, String> {
    public GuildStringSetting(GuildSettingCategory category) {
        super(category, Key.of(String.class));
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
