package link.locutus.discord.util.task;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.offshore.Auth;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class MailRespondTask implements Callable<String> {
    private final int convoid;
    private final String leader;
    private final String message;
    private final Auth auth;
    private final MessageChannel output;

    public MailRespondTask(Auth auth, String leader, int convoid, String message, MessageChannel output) {
        this.leader = leader;
        this.convoid = convoid;
        this.message = message;
        this.auth = auth;
        this.output = output;
    }

    public synchronized String call() throws IOException {
        return PW.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Map<String, String> msgPost = new HashMap<>();
                msgPost.put("convoid", Integer.toString(convoid));
                msgPost.put("receiver", leader);
                msgPost.put("body", message);
                msgPost.put("sndmsg", "Send Message");

                String url = "" + Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + convoid;
                String msgResponse = auth.readStringFromURL(PagePriority.MAIL_REPLY, url, msgPost);
                if (msgResponse.contains("You have successfully sent a message.")) {
                    return "Message sent to " + leader + "! (check your out folder)";
                } else if (msgResponse.contains("because they are less than 3 minutes old. Please give new players some time to catch their")) {
                    Locutus.imp().getCommandManager().getExecutor().schedule(CaughtRunnable.wrap(() -> {
                        String result = MailRespondTask.this.call();
                        Guild server = Locutus.imp().getDiscordApi().getGuildById(Settings.INSTANCE.ROOT_SERVER);
                        if (output != null) {
                            RateLimitUtil.queueWhenFree(output.sendMessage(result));
                        }
                        return result;
                    }), 3, TimeUnit.MINUTES);
                    return "Account is too new. Scheduled to try again later: " + leader;
                } else {
                    return "Error: " + PW.getAlert(Jsoup.parse(msgResponse));
                }
            }
        }, auth);
    }
}
