package link.locutus.discord.util.task.mail;

import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class ReadMailTask implements Callable<List<ReadMailTask.MailMessage>> {
    private final Auth auth;
    private final int id;

    public ReadMailTask(Auth auth, int id) {
        this.auth = auth;
        this.id = id;
    }

    /**
     * Returns a list of messages in the mail with true being for received and false being sent
     * @return
     * @throws Exception
     */
    @Override
    public List<MailMessage> call() throws Exception {
        return PW.withLogin(() -> {
            String url = Settings.INSTANCE.PNW_URL() + "/inbox/message/id=" + id;
            Document msgDom = Jsoup.parse(auth.readStringFromURL(PagePriority.MAIL_READ, url, Collections.emptyMap()));
            Elements messages = msgDom.select(".red-msg, .blue-msg");

            List<MailMessage> messagesColor = new ArrayList<>();

            for (Element message : messages) {
                messagesColor.add(new MailMessage(message));
            }

            return messagesColor;
        }, auth);
    }

    public static class MailMessage {
        private final String content;
        private final boolean isReceived;
        private final long date;
        private final int nationId;

        public MailMessage(Element message) {


            this.isReceived = message.hasClass("red-msg");
            Element secondChild = message.child(1);
            // html to markdown
            this.content = MarkupUtil.htmlToMarkdown(message.text());

            // first child has header, get it and then parse it to get the date, nationId
            // the nationId is from the first a link within the child (not a direct descendent, use a selector to get the first)
            // the date string is the only text not in a span tag, parse it, it's in the form `06/20/2024 Thursday 9:48 am`
            // i assume the day name e.g. `Thursday` can be ignored and just the month/day/year and time is needed
            Element header = secondChild.child(0);
            // get the nation id
            Element nationLink = header.selectFirst("a");
            String nationIdStr = nationLink.attr("href").split("=")[1];
            this.nationId = Integer.parseInt(nationIdStr);
            String dateStr = header.textNodes().get(0).text().trim();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy EEEE h:mm a");
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, formatter);
            Instant instant = dateTime.atZone(ZoneOffset.UTC).toInstant();
            this.date = instant.toEpochMilli();
        }

        public String getContent() {
            return content;
        }

        public boolean isReceived() {
            return isReceived;
        }

        public long getDate() {
            return date;
        }

        public int getNationId() {
            return nationId;
        }
    }
}
