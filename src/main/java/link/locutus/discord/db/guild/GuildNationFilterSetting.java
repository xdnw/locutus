package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;

public abstract class GuildNationFilterSetting extends GuildSetting<NationFilter, String> {
    public GuildNationFilterSetting(GuildSettingCategory category) {
        super(category, Key.of(NationFilter.class));
    }

    @Override
    public final String toString(NationFilter value) {
        return value.getFilter();
    }
}
