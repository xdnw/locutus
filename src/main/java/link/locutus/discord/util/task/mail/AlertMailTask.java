package link.locutus.discord.util.task.mail;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.war.Spyops;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.task.MailRespondTask;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class AlertMailTask extends CaughtRunnable implements BiConsumer<Mail, List<String>> {
    private final SearchMailTask task;
    private long outputChannel;
    private final Auth auth;

    public AlertMailTask(Auth auth, long channel) {
        this.auth = auth;
        this.task = new SearchMailTask(auth, null, true, false, true, this);
        this.outputChannel = channel;
    }
    @Override
    public void runUnsafe() {
        try {
            this.task.call();
        } catch (Throwable e) {
            AlertUtil.error("Error reading mail for: " + auth.getNationId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void accept(Mail mail, List<String> strings) {
        try {
            if (strings.size() == 0) {
                return;
            }
            String url = "" + Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + mail.id;

            String title = mail.leader + " in '" + mail.subject + "'";

            StringBuilder body = new StringBuilder();
            body.append(PnwUtil.getMarkdownUrl(mail.nationId, false));

            DBNation nation = Locutus.imp().getNationDB().getNation(mail.nationId);
            if (nation != null) {
                body.append(" | " + PnwUtil.getMarkdownUrl(nation.getAlliance_id(), true));
                User user = nation.getUser();
                if (user != null) {
                    body.append(" | ").append(user.getAsMention());
                }
            }
            body.append("\n");
            body.append("```").append(strings.get(0)).append("```");
            body.append("\n").append(url);


            long output = outputChannel;
            String[] split = mail.subject.split("/");
            if (split.length > 1 && MathMan.isInteger(split[split.length - 1])) {
                output = Long.parseLong(split[split.length - 1]);
            }

            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(output);
            if (channel == null) {
                channel = Locutus.imp().getDiscordApi().getGuildChannelById(outputChannel);
            }

            if (channel != null) {
                DiscordChannelIO outputBuilder = new DiscordChannelIO(channel, () -> null);

                Guild guild = channel.getGuild();

                if (!strings.isEmpty()) {
                    String msg = strings.get(0);

                    Map.Entry<DBNation, double[]> spyReport = SpyCount.parseSpyReport(nation, msg);
                    if (spyReport != null) {
                        double converted = PnwUtil.convertedTotal(spyReport.getValue());

                        body.append("\nWorth: ~$" + MathMan.format(converted * 0.14));
                    }
                }

                IMessageBuilder builder = outputBuilder.create();

                Role role = Roles.MAIL.toRole(guild);
                if (role != null) {
                    builder.append("^ " + role.getAsMention());
                }

                builder.embed(title, body.toString());
                DBNation receiver = Locutus.imp().getNationDB().getNationByLeader(mail.leader);
                CM.mail.reply mailCmd = CM.mail.reply.cmd.create(receiver.getNation(), url, null, auth.getNation().getNation());
                builder.modal(CommandBehavior.DELETE_REACTION, mailCmd, "\uD83D\uDCE7 Reply");

                builder.send();

                processCommands(guild, mail, strings);


            }

            if (nation == null) return;

            if (strings.isEmpty()) return;
            String msg = strings.get(0);

            if (mail.subject.toLowerCase().startsWith("targets-")) {
                if (msg.toLowerCase().startsWith("more")) {

                    Set<Integer> tracked = new HashSet<>();


                    String targets = null;
                    GuildDB db = null;
                    if (channel != null) {
                        db = Locutus.imp().getGuildDB(channel.getGuild());
                    }
                    if (db == null) {
                        db = Locutus.imp().getGuildDB(nation.getAlliance_id());
                    }
//                    if (rootDB != null && rootDB.getCoalition("spyops").contains(nation.getAlliance_id())) {
//                        db = rootDB;
//                    }
                    try {
                        if (db != null && !db.getCoalition(Coalition.ENEMIES).isEmpty()) {
                            Spyops cmd = new Spyops();

                            split = msg.split(" ");
                            String type = "*";
                            if (split.length >= 2) {
                                try {
                                    type = MilitaryUnit.valueOf(split[1].toUpperCase()).name();
                                } catch (IllegalArgumentException igniore) {
                                }
                            }
                            ArrayList<String> args = new ArrayList<>(Arrays.asList("#wars>0,enemies", type));
                            Set<Character> flags = new HashSet<>(Arrays.asList('s', 'r'));
                            targets = cmd.run(null, nation, db, args, flags);
                        } else if (db == null) {
                            targets = "Your alliance does not have Locutus setup. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() +  "";
                        } else {
                            targets = "Your alliance does not have any enemies set. Use the command on discord instead:\n" + CM.spy.find.target.cmd.toSlashMention() + "";
                        }
                        if (targets != null) {
                            String response = new MailRespondTask(auth, mail.leader, mail.id, MarkupUtil.bbcodeToHTML(targets), null).call();
                            if (channel != null) {
                                RateLimitUtil.queueWhenFree(channel.sendMessage("Sending target messages to " + nation.getNation() + ": " + response));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void processCommands(Guild guild, Mail mail, List<String> strings) {
        String reply = strings.get(0);
        if (reply.isEmpty() || reply.charAt(0) != (Settings.commandPrefix(true)).charAt(0)) return;

        DBNation nation = DBNation.byId(mail.nationId);
        if (nation == null) return;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return;

        if (nation.getPosition() <= 1 || !db.isAllianceId(nation.getAlliance_id())) return;
    }
}
