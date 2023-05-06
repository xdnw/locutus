package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.db.GuildDB;

public abstract class GuildEnumSetting<T extends Enum> extends GuildSetting<T> {
    private final Class<T> t;

    public GuildEnumSetting(GuildSettingCategory category, Class<T> t) {
        super(category, t);
        this.t = t;
    }

    @Override
    public String toString(T value) {
        return value.name();
    }

    @Override
    public T parse(GuildDB db, String input) {
        try {
            return BindingHelper.emum(t, input);
        } catch (IllegalArgumentException ignore) {}
        return super.parse(db, input);
    }
}
