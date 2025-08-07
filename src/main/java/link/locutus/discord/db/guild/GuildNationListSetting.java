package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.pnw.NationList;

public abstract class GuildNationListSetting extends GuildSetting<NationList> {
    public GuildNationListSetting(GuildSettingCategory category, GuildSettingSubgroup subgroup) {
        super(category, subgroup, Key.of(NationList.class));
    }

    @Override
    public final String toString(NationList value) {
        return value.getFilter();
    }
}
