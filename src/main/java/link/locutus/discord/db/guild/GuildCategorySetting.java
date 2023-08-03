package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.IMentionable;

public abstract class GuildCategorySetting extends GuildSetting<Category, Long> {
    public GuildCategorySetting(GuildSettingCategory category) {
        super(category, Key.of(Category.class));
    }

    @Override
    public Category validate(GuildDB db, Category value) {
        return validateCategory(db, value);
    }

    @Override
    public final String toString(Category value) {
        return ((IMentionable) value).getAsMention();
    }
}
