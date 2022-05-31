package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.CommandManager;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MsgInfo extends Command {
    public MsgInfo() {
        super("msginfo", CommandCategory.DEBUG);
    }
    @Override
    public String help() {
        return "!msginfo <message-link>";
    }

    @Override
    public String desc() {
        return "print message info to console";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        String arg = args.get(0);
        String[] split = arg.split("/");
        if (!arg.startsWith("https://discord.com/channels/") || split.length < 3) return usage(event);
        Message message = DiscordUtil.getMessage(arg);
        if (message == null) return usage(event);

        StringBuilder response = new StringBuilder();

        List<MessageReaction> reactions = message.getReactions();
        Map<User, List<String>> reactionsByUser = new LinkedHashMap<>();
        for (MessageReaction reaction : reactions) {
            String emoji = reaction.getReactionEmote().getEmoji();
            List<User> users = com.boydti.discord.util.RateLimitUtil.complete(reaction.retrieveUsers());
            for (User user : users) {
                reactionsByUser.computeIfAbsent(user, f -> new ArrayList<>()).add(emoji);
            }
        }

        String title = "Message " + message.getIdLong();
        response.append("```" + DiscordUtil.trimContent(message.getContentRaw()).replaceAll("`", "\\`") + "```\n\n");

        if (!reactionsByUser.isEmpty()) {
            for (Map.Entry<User, List<String>> entry : reactionsByUser.entrySet()) {
                response.append(entry.getKey().getAsMention() + "\t" + StringMan.join(entry.getValue(), ","));
                response.append("\n");
            }
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
        return null;
    }
}
