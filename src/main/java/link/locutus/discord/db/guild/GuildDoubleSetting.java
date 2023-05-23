package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.util.MathMan;

public abstract class GuildDoubleSetting extends GuildSetting<Double> {
    public GuildDoubleSetting(GuildSettingCategory category) {
        super(category, Key.of(Double.class));
    }

    @Override
    public String toReadableString(Double value) {
        return MathMan.format(value);
    }

    @Override
    public final String toString(Double value) {
        return value.toString();
    }
}
