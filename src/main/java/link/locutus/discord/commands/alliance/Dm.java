package link.locutus.discord.commands.alliance;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import java.util.*;

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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);

        String content = DiscordUtil.trimContent(fullCommandRaw);
        String body = content.substring(content.indexOf(' ', content.indexOf(args.get(0)) + args.get(0).length()) + 1);
        List<User> mentions = new ArrayList<>();

        User user = DiscordUtil.getMention(args.get(0));
        if (user != null) {
            mentions.add(user);
        } else {
            for (DBNation nation : DiscordUtil.parseNations(guild, author, me, args.get(0), false, false)) {
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
            String title = "Send " + mentions.size() + " messages.";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : nations) alliances.add(nation.getAlliance_id());

            String embedTitle = title + " to nations.";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances.";

            String dmMsg = "content: ```" + body + "```";

            JSONObject json = CM.admin.dm.cmd.message(dmMsg).nations(args.get(0)).force("true").toJson();
            channel.create().embed( embedTitle, dmMsg).confirmation(json).send();
            return null;
        }

        channel.sendMessage("Please wait...");
        for (User mention : mentions) {
            mention.openPrivateChannel().queue(f -> RateLimitUtil.queue(f.sendMessage(author.getAsMention() + " said: " + body + "\n\n(no reply)")));
        }
        channel.sendMessage("Sent " + mentions.size() + " messages");
        return null;
    }
}
