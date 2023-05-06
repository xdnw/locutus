package link.locutus.discord.db.guild;

import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.MessageChannel;

public abstract class GuildChannelSetting extends GuildSetting<MessageChannel> {
    public GuildChannelSetting(GuildSettingCategory category) {
        super(category, Key.of(MessageChannel.class));
    }

    @Override
    public MessageChannel validate(GuildDB db, MessageChannel value) {
        return validateChannel(db, value);
    }

    @Override
    public final String toString(MessageChannel value) {
        return ((IMentionable) value).getAsMention();
    }
}
