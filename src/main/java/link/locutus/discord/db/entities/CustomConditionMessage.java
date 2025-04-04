package link.locutus.discord.db.entities;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.util.task.mail.MailApiSuccess;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CustomConditionMessage {
    private final long date_created;
    private String subject;
    private String body;
    private MessageTrigger trigger;
    private long delay;

    public CustomConditionMessage(String subject, String body, MessageTrigger trigger, long delay, long date_created) {
        this.subject = subject;
        this.body = body;
        this.trigger = trigger;
        this.delay = delay;
        this.date_created = date_created;
    }

    public static CustomConditionMessage fromJson(JSONObject jsonObject) {
        String subject = jsonObject.getString("subject");
        String body = jsonObject.getString("body");
        MessageTrigger trigger = MessageTrigger.valueOf(jsonObject.getString("trigger"));
        long delay = jsonObject.getLong("delay");
        long date_created = jsonObject.getLong("date_created");
        return new CustomConditionMessage(subject, body, trigger, delay, date_created);
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        obj.put("subject", subject);
        obj.put("body", body);
        obj.put("trigger", trigger.name());
        obj.put("delay", delay);
        obj.put("date_created", date_created);
        return obj;
    }

    private String getTitle() {
        return trigger.name() + " | " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, delay) + " | Subject:`" + subject + "`";
    }

    public long getDateCreated() {
        return date_created;
    }

    public MessageTrigger getTrigger() {
        return this.trigger;
    }

    public long getDelay() {
        return this.delay;
    }

    public String getSubject() {
        return this.subject;
    }

    public String getBody() {
        return this.body;
    }

    public long getOriginDate() {
        return getDateCreated() - getDelay();
    }

    public void send(GuildDB db, DBNation nation, boolean sendEnabled) {
        Locutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                MessageChannel output = GuildKey.RECRUIT_MESSAGE_OUTPUT.getOrNull(db);
                if (output == null) return;
                ApiKeyPool mailKey = db.getMailKey();
                if (mailKey == null) {
                    RateLimitUtil.queue(output.sendMessage("No mail key set. See: " + CM.settings_default.registerApiKey.cmd.toSlashMention()));
                    return;
                }
                NationPlaceholders ph = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
                String subjectFormat = ph.format2(db.getGuild(), null, null, subject, nation, false);
                String bodyFormat = ph.format2(db.getGuild(), null, null, body, nation, false);
                String message = getTitle() + "\n- To: " + nation.getMarkdownUrl();
                if (sendEnabled) {
                    MailApiResponse result = nation.sendMail(mailKey, subjectFormat, bodyFormat, false);
                    if (result.status() == MailApiSuccess.SUCCESS) {
                        message += "\n- Sent: " + result.status();
                    } else if (result.status() == MailApiSuccess.NON_MAIL_KEY) {
                        message += "\n- Failed: " + result.status() + " " + result.error() + ". Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name()
                                + "`. Please set a new working API_KEY (" + CM.settings_default.registerApiKey.cmd.toSlashMention() + "), then See: " + CM.settings.delete.cmd.key(GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + " <@" + db.getGuild().getOwnerId() + ">");
                        db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                    } else {
                        message += "\n" + result.status() + " " + result.error();
                    }
                } else {
                    message += "\n- Sending disabled. `/admin queue custom_messages setmeta:True sendmessages:True run:true`";
                }
                RateLimitUtil.queue(output.sendMessage(message));
            }
        });
    }
}