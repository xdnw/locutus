package com.boydti.discord.util.task.mail;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.war.Spyops;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.scheduler.CaughtRunnable;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.AlertUtil;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.SpyCount;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.util.task.MailRespondTask;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
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
            e.printStackTrace();
            AlertUtil.error("Error reading mail for: " + auth.getNationId(), e);
        }
    }

    @Override
    public void accept(Mail mail, List<String> strings) {
        try {
            if (strings.size() == 0) {
                return;
            }
            String url = "" + Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + mail.id;

            String replyEmoji = "\uD83D\uDCE7";
            String infoEmoji = "\u2139";
            String reply = "_!say `!mail from:" + auth.getNationId() + " \"" + mail.leader + "\" " + url + " <response>`\n - " + url;
            String info = "!checkmail " + url;

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
            body.append("\nPress " + replyEmoji + " to reply");


            long output = outputChannel;
            String[] split = mail.subject.split("/");
            if (split.length > 1 && MathMan.isInteger(split[split.length - 1])) {
                output = Long.parseLong(split[split.length - 1]);
            }

            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(output);
            if (channel == null) {
                channel = Locutus.imp().getDiscordApi().getGuildChannelById(outputChannel);
            }

            Message message = null;
            if (channel != null) {
                Guild guild = channel.getGuild();
                GuildDB db = Locutus.imp().getGuildDB(guild);
                message = DiscordUtil.createEmbedCommand(output, title, body.toString(), replyEmoji, reply, infoEmoji, info);

                Role role = Roles.INTERNAL_AFFAIRS.toRole(guild);
                if (role != null) {
//                channel.sendMessage("^ " + com.boydti.discord.util.RateLimitUtil.queue(role.getAsMention()));
                }

                processCommands(guild, mail, strings);
            }

            if (nation == null) return;

            if (strings.isEmpty()) return;
            String msg = strings.get(0);

            Map.Entry<DBNation, double[]> spyReport = SpyCount.parseSpyReport(nation, msg);
            if (spyReport != null && message != null) {
                double converted = PnwUtil.convertedTotal(spyReport.getValue());
                DiscordUtil.appendDescription(message, "\nWorth: ~$" + MathMan.format(converted * 0.14));
            }


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
                            targets = "Your alliance does not have Locutus setup. Use the command on discord instead:\n!spyops";
                        } else {
                            targets = "Your alliance does not have any enemies set. Use the command on discord instead:\n!spyops";
                        }
                        if (targets != null) {
                            String response = new MailRespondTask(auth, mail.leader, mail.id, MarkupUtil.bbcodeToHTML(targets), null).call();
                            if (channel != null) {
                                RateLimitUtil.queue(channel.sendMessage("Sending target messages to " + nation.getNation() + ": " + response));
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
        if (reply.isEmpty() || !reply.startsWith("!")) return;

        DBNation nation = DBNation.byId(mail.nationId);
        if (nation == null) return;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return;
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId == null) return;

        if (nation.getPosition() <= 1 || nation.getAlliance_id() != aaId) return;
    }
}
