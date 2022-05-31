package com.boydti.discord.commands.alliance;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.PNWUser;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class Dm extends Command {
    public Dm() {
        super(CommandCategory.LOCUTUS_ADMIN);
    }
    @Override
    public String help() {
        return super.help() + " <user> <message>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        String body = content.substring(content.indexOf(' ', content.indexOf(args.get(0)) + args.get(0).length()) + 1);
        List<User> mentions = new ArrayList<>();

        User user = DiscordUtil.getMention(args.get(0));
        if (user != null) {
            mentions.add(user);
        } else {
            for (DBNation nation : DiscordUtil.parseNations(guild, args.get(0))) {
                PNWUser pnwUser = nation.getDBUser();
                if (pnwUser != null) {
                    user = pnwUser.getUser();
                    if (user != null) {
                        mentions.add(user);
                    }
                }
            }
        }

        Set<DBNation> nations = new HashSet<>();
        for (User mention : mentions) {
            nations.add(DiscordUtil.getNation(mention));
        }

        if (mentions.size() > 1 && !flags.contains('f')) {
            String title = "Send " + mentions.size() + " messages";
            String pending = "!pending " + DiscordUtil.trimContent(event.getMessage().getContentRaw()) + " -f";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : nations) alliances.add(nation.getAlliance_id());

            String embedTitle = title + " to nations";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder dmMsg = new StringBuilder();
            dmMsg.append("content: ```" + body + "```");

            DiscordUtil.createEmbedCommand(event.getChannel(), embedTitle, dmMsg.toString(), "\u2705", pending);
            return null;
        }

        Message message = com.boydti.discord.util.RateLimitUtil.complete(event.getChannel().sendMessage("Please wait..."));

        for (User mention : mentions) {
            mention.openPrivateChannel().queue(new Consumer<PrivateChannel>() {
                @Override
                public void accept(PrivateChannel channel) {
                    RateLimitUtil.queue(channel.sendMessage(event.getAuthor().getAsMention() + " said: " + body + "\n\n(no reply)"));
                }
            });
        }
        RateLimitUtil.queue(event.getChannel().editMessageById(message.getIdLong(), "Sent " + mentions.size() + " messages"));
        return null;
    }
}
