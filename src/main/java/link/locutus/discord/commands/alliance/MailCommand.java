package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.MailRespondTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MailCommand extends Command implements Noformat {
    public MailCommand() {
        super("mail", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.INTERNAL_AFFAIRS);
    }

    @Override
    public String help() {
        return "`" + Settings.commandPrefix(true) + "mail <nations> <subject> <message...>` or `" + Settings.commandPrefix(true) + "mail <leaders> <message-url> <message...>`";
    }

    @Override
    public String desc() {
        return "Send an in game message to a nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MAIL.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 3) return usage(args.size(), 3, channel);
        String fromStr = DiscordUtil.parseArg(args, "from");

        GuildDB db = Locutus.imp().getGuildDB(guild);

        try {
            String arg0 = args.get(0);
            String arg1 = args.get(1);

            if (arg1.contains("message/id=")) {
                Auth auth;

                if (fromStr != null) {
                    DBNation from = DiscordUtil.parseNation(fromStr);
                    if (from == null) throw new IllegalArgumentException("Invalid sender: " + fromStr);
                    auth = from.getAuth(true);
                    GuildDB authDB = Locutus.imp().getGuildDB(from.getAlliance_id());
                    boolean hasPerms = (Roles.INTERNAL_AFFAIRS.hasOnRoot(author)) || (authDB != null && Roles.INTERNAL_AFFAIRS.has(author, authDB.getGuild()));
                    if (!hasPerms) return "You do not have permission to reply to this message (1).";
                } else {
                    auth = me.getAuth();
                }

                int messageId = Integer.parseInt(arg1.split("=")[1]);
                String content = DiscordUtil.trimContent(fullCommandRaw);
                String body = content.substring(content.indexOf(' ', content.indexOf("message/id=")) + 1);

                GPTUtil.checkThrowModeration(body);
                String result = new MailRespondTask(auth, arg0, messageId, body, null).call();
                return "Mail: " + result;
            }

            Set<DBNation> nations = DiscordUtil.parseNations(guild, author, me, arg0, false, false);
            if (nations.isEmpty()) {
                return "Invalid nation `" + arg0 + "`";
            }

            String message = StringMan.join(args.subList(2, args.size()), " ");

            message = MarkupUtil.transformURLIntoLinks(message);
            String subject = args.get(1);

            ApiKeyPool.ApiKey myKey = me.getApiKey(false);

            ApiKeyPool key;
            if (flags.contains('l') || myKey == null) {
                if (!Roles.MAIL.has(author, db.getGuild())) {
                    return "You do not have the role `MAIL` (see " + CM.role.setAlias.cmd.toSlashMention() + " OR use`" + CM.credentials.addApiKey.cmd.toSlashMention() + "` to add your own key.";
                }
                key = db.getMailKey();
            } else {
                key = ApiKeyPool.builder().addKey(myKey).build();
            }
            if (key == null) {
                return "No api key found. Please use`" + CM.credentials.addApiKey.cmd.toSlashMention() + "`";
            }


            if (!flags.contains('f')) {
                String title = "Send " + nations.size() + " messages.";
                String pending = Settings.commandPrefix(true) + "pending '" + title + "' " + DiscordUtil.trimContent(fullCommandRaw).replaceFirst(" ", " -f ");

                Set<Integer> alliances = new LinkedHashSet<>();
                for (DBNation nation : nations) alliances.add(nation.getAlliance_id());
                String embedTitle = title + " to ";
                if (nations.size() == 1) {
                    DBNation nation = nations.iterator().next();
                    embedTitle += nations.size() == 1 ? nation.getName() + " | " + nation.getAllianceName() : "nations";
                } else {
                    embedTitle += " nations";
                }
                if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

                String body = "subject: " + subject + "\n" +
                        "body: ```" + message + "```";

                channel.create().embed(embedTitle, body)
                                .commandButton(pending, "Next").send();
                return null;
            }

            if (!Roles.ADMIN.hasOnRoot(author)) {
                message += "\n\n<i>This message was sent by: " + author.getName() + "</i>";
            }
            GPTUtil.checkThrowModeration(message);

            CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Sending to...");
            IMessageBuilder msg = null;
            StringBuilder response = new StringBuilder();
            long start = System.currentTimeMillis();
            for (DBNation nation : nations) {
                if (System.currentTimeMillis() - start > 10000) {
                    try {
                        msg = msgFuture.get();
                        if (msg != null && msg.getId() > 0) {
                            msg.clear().append("Sending to " + nation.getNation()).sendIfFree();
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    start = System.currentTimeMillis();
                }
                response.append(nation.sendMail(key, subject, message, nations.size() == 1)).append("\n");
            }

            if (msg != null && msg.getId() > 0) {
                channel.delete(msg.getId());
            }
            return response.toString();
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}