package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class MailTask implements Callable<String> {
    private final DBNation nation;
    private final String subject;
    private final String message;
    private final Auth auth;
    private final MessageChannel output;
    private final boolean priority;

    public MailTask(Auth auth, boolean priority, DBNation nation, String subject, String message, MessageChannel output) {
        this.nation = nation;
        this.priority = priority;
        this.subject = subject;
        this.message = message;
        this.auth = auth;
        this.output = output;
    }

    public synchronized String call() throws IOException {
        return PnwUtil.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Map<String, String> msgPost = new HashMap<>();
                msgPost.put("newconversation", "true");
                msgPost.put("receiver", nation.getLeader());
                msgPost.put("carboncopy", "");
                msgPost.put("subject", subject);
                msgPost.put("body", message);
                msgPost.put("sndmsg", "Send Message");

                String url = "" + Settings.INSTANCE.PNW_URL() + "/inbox/message/";
                String msgResponse = auth.readStringFromURL(priority ? PagePriority.MAIL_SEND_SINGLE : PagePriority.MAIL_SEND_BULK, url, msgPost);
                if (msgResponse.contains("You have successfully sent a message.")) {
                    return "Message sent to " + nation.getNation() + "! (check your out folder)";
                } else if (msgResponse.contains("because they are less than 3 minutes old. Please give new players some time to catch their")) {
                    Locutus.imp().getCommandManager().getExecutor().schedule(CaughtRunnable.wrap(() -> {
                        String result = MailTask.this.call();
                        Guild server = Locutus.imp().getDiscordApi().getGuildById(Settings.INSTANCE.ROOT_SERVER);
                        if (output != null) {
                            RateLimitUtil.queueWhenFree(output.sendMessage(result));
                        }
                        return result;
                    }), 3, TimeUnit.MINUTES);
                    return "Account is too new. Scheduled to try again later: " + nation.getLeader();
                } else {
                    return "Mail error: " + PnwUtil.getAlert(Jsoup.parse(msgResponse));
                }
            }
        }, auth);
    }
}
