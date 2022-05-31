package com.boydti.discord.commands.alliance;

import com.boydti.discord.Locutus;
import com.boydti.discord.apiv1.entities.ApiRecord;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.Noformat;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.util.task.MailRespondTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MailCommand extends Command implements Noformat {
    public MailCommand() {
        super("mail", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return "`!mail <nation> <subject> <message...>` or `!mail <leader> <message-url> <message...>`";
    }

    @Override
    public String desc() {
        return "Send an in game message to a nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).hasAuth() && Roles.MAIL.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(guild);

        try {
            String arg0 = args.get(0);
            String arg1 = args.get(1);

            if (arg1.contains("message/id=")) {
                Auth auth = null;

                String fromStr = DiscordUtil.parseArg(args, "from");
                if (fromStr != null) {
                    DBNation from = DiscordUtil.parseNation(fromStr);
                    if (from == null) throw new IllegalArgumentException("Invalid sender: " + fromStr);
                    auth = from.getAuth(null);
                    GuildDB authDB = Locutus.imp().getGuildDB(from.getAlliance_id());
                    boolean hasPerms = (Roles.INTERNAL_AFFAIRS.hasOnRoot(author)) || (authDB != null && Roles.INTERNAL_AFFAIRS.has(author, authDB.getGuild()));
                    if (!hasPerms) return "You do not have permission to reply to this message";
                } else {
                    try {
                        auth = me.getAuth(Roles.INTERNAL_AFFAIRS);
                    } catch (IllegalArgumentException e) {
                        auth = db.getAuth();
                        if (auth == null) throw e;
                    }
                }

                int messageId = Integer.parseInt(arg1.split("=")[1]);
                String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
                String body = content.substring(content.indexOf(' ', content.indexOf("message/id=")) + 1);

                String result = new MailRespondTask(auth, arg0, messageId, body, null).call();
                return "Mail: " + result;
            }

            Set<DBNation> nations = DiscordUtil.parseNations(event.getGuild(), arg0);
            if (nations.isEmpty()) {
                return "Invalid nation `" + arg0 + "`";
            }

            String message = StringMan.join(args.subList(2, args.size()), " ");

            message = MarkupUtil.transformURLIntoLinks(message);
            String subject = args.get(1);

            String[] keys = { Locutus.imp().getRootAuth().getApiKey() };
            if (flags.contains('l') || (!Roles.ADMIN.hasOnRoot(event.getAuthor()) && !Roles.INTERNAL_AFFAIRS.hasOnRoot(event.getAuthor()))) {
                keys = Locutus.imp().getGuildDB(event).getOrThrow(GuildDB.Key.API_KEY);
            }


            Integer nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(keys[0]);
            if (nationId == null) return "Invalid Key `!KeyStore API_KEY`";
            DBNation sender = DBNation.byId(nationId);

            if (!flags.contains('f')) {
                String title = "Send " + nations.size() + " messages";
                String pending = "!pending '" + title + "' " + DiscordUtil.trimContent(event.getMessage().getContentRaw()).replaceFirst(" ", " -f ");

                Set<Integer> alliances = new LinkedHashSet<>();
                for (DBNation nation : nations) alliances.add(nation.getAlliance_id());
                String embedTitle = title + " to ";
                if (nations.size() == 1) {
                    DBNation nation = nations.iterator().next();
                    embedTitle += nations.size() == 1 ? nation.getName() + " | " + nation.getAlliance() : "nations";
                } else {
                    embedTitle += " nations";
                }
                if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

                StringBuilder body = new StringBuilder();
                body.append("subject: " + subject + "\n");
                body.append("body: ```" + message + "```");

                if (sender.getNation_id() == Settings.INSTANCE.NATION_ID) {
                    body.append("\nAdd `-l` to send from your alliance instead of Borg");
                }

                DiscordUtil.createEmbedCommand(event.getChannel(), embedTitle, body.toString(), "\u2705", pending);
                return null;
            }

            if (!Roles.ADMIN.hasOnRoot(author)) {
                message += "\n\n<i>This message was sent by: " + author.getName() + "</i>";
            }

            Message msg = com.boydti.discord.util.RateLimitUtil.complete(event.getChannel().sendMessage("Sending to..."));
            StringBuilder response = new StringBuilder();
            for (DBNation nation : nations) {
                RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Sending to " + nation.getNation()));


                response.append(nation.sendMail(keys[0], subject, message)).append("\n");
//                response.append(new MailTask(auth, nation, subject, message).call()).append('\n');
            }

            com.boydti.discord.util.RateLimitUtil.queue(event.getChannel().deleteMessageById(msg.getIdLong()));
            return response.toString();
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}
