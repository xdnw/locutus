package com.boydti.discord.commands.account.question;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.Map;

public interface Question {
    public String getContent();
    public boolean isValidateOnInit();
    public String[] getOptions();

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