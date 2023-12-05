package link.locutus.discord.event.mail;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.task.MailRespondTask;
import link.locutus.discord.util.task.mail.Mail;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class MailReceivedEvent extends Event {
    private final Auth auth;
    private final Mail mail;
    private final long defaultChannel;
    private final List<String> messages;

    public MailReceivedEvent(Auth auth, Mail mail, List<String> strings, long defaultChannel) {
        this.auth = auth;
        this.mail = mail;
        this.messages = strings;
        this.defaultChannel = defaultChannel;

    }

    public String getTitle() {
        String title = mail.leader + " in '" + mail.subject + "'";
        return title;
    }

    public String getUrl() {
        String url = "" + Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + mail.id;
        return url;
    }

    public DBNation getNation() {
        return DBNation.getById(mail.nationId);
    }

    public DBNation getAuthNation() {
        return auth.getNation();
    }

    public GuildMessageChannel getChannel() {
        String[] split = mail.subject.split("/");
        if (split.length > 1 && MathMan.isInteger(split[split.length - 1])) {
            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(Long.parseLong(split[split.length - 1]));
            if (channel != null) {
                return channel;
            }
        }
        DBNation nation = getNation();
        DBNation authNation = getAuthNation();
        if (nation != null && authNation != null && nation.getAlliance_id() != 0) {
            GuildDB db = authNation.getGuildDB();
            if (db.isAllianceId(nation.getAlliance_id())) {
                IACategory iaCat = db.getIACategory(true, true, false);
                if (iaCat != null) {
                    IAChannel channel = iaCat.find(nation);
                    if (channel != null) {
                        TextChannel text = channel.getChannel();
                        if (text != null) {
                            return text;
                        }
                    }
                }
            }
        }
        return Locutus.imp().getDiscordApi().getGuildChannelById(this.defaultChannel);
    }

    public String toEmbedString() {
        StringBuilder body = new StringBuilder();
        DBNation nation = Locutus.imp().getNationDB().getNation(mail.nationId);
        if (nation != null) {
            body.append(" | " + PnwUtil.getMarkdownUrl(nation.getAlliance_id(), true));
            User user = nation.getUser();
            if (user != null) {
                body.append(" | ").append(user.getAsMention());
            }
        }
        body.append("\n");
        body.append("```").append(messages.get(0)).append("```");
        body.append("\n").append(getUrl());

        if (!messages.isEmpty()) {
            String msg = messages.get(0);

            Map.Entry<DBNation, double[]> spyReport = SpyCount.parseSpyReport(nation, msg);
            if (spyReport != null) {
                double converted = PnwUtil.convertedTotal(spyReport.getValue());

                body.append("\nWorth: ~$" + MathMan.format(converted * 0.14));
            }
        }

        return body.toString();
    }

    public Auth getAuth() {
        return auth;
    }

    public long getDefaultChannelId() {
        return defaultChannel;
    }

    public Mail getMail() {
        return mail;
    }

    public List<String> getMessages() {
        return messages;
    }

    public String reply(String message) throws IOException {
        String response = new MailRespondTask(getAuth(), mail.leader, mail.id, message, null).call();
        return response;
    }
}
