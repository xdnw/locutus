package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;

public abstract class GuildNationFilterSetting extends GuildSetting<NationFilter> {
    public GuildNationFilterSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup) {
        super(category, subgroup, Key.of(NationFilter.class));
    }

    @Override
    public final String toString(NationFilter value) {
        return value.getFilter();
    }
}
