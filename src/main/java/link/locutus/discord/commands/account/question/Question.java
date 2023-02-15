package link.locutus.discord.commands.account.question;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.Map;

public interface Question {
    String getContent();

    boolean isValidateOnInit();

    String[] getOptions();

    default boolean validate(Guild guild, User author, DBNation me, DBNation sudoer, GuildMessageChannel channel, String input) throws IOException {
        return true;
    }

    default String format(Guild guild, User author, DBNation me, GuildMessageChannel channel, String message) {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        message = DiscordUtil.format(guild, channel, author, me, message);
        for (Map.Entry<String, String> entry : db.getKeys().entrySet()) {
            if (!message.contains("{")) break;
            String key = entry.getKey().toLowerCase();
            if (key.equalsIgnoreCase("api_key")) continue;
            String placeholder = "{guild." + key + "}";
            message = message.replace(placeholder, entry.getValue());
        }
        return message;
    }

    int ordinal();
}