package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MsgInfo extends Command {
    public MsgInfo() {
        super("msginfo", CommandCategory.DEBUG);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "msginfo <message-link>";
    }

    @Override
    public String desc() {
        return "print message info to console.";
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
            List<User> users = RateLimitUtil.complete(reaction.retrieveUsers());
            for (User user : users) {
                reactionsByUser.computeIfAbsent(user, f -> new ArrayList<>()).add(emoji);
            }
        }

        String title = "Message " + message.getIdLong();
        response.append("```").append(DiscordUtil.trimContent(message.getContentRaw()).replaceAll("`", "\\`")).append("```\n\n");

        if (!reactionsByUser.isEmpty()) {
            for (Map.Entry<User, List<String>> entry : reactionsByUser.entrySet()) {
                response.append(entry.getKey().getAsMention()).append("\t").append(StringMan.join(entry.getValue(), ","));
                response.append("\n");
            }
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString());
        return null;
    }
}
