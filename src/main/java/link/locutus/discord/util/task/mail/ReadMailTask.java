package link.locutus.discord.util.task.mail;

import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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
            List<MailMessage> messagesColor = new ArrayList<>();
            try {
                String url = Settings.PNW_URL() + "/inbox/message/id=" + id;
                Document msgDom = Jsoup.parse(auth.readStringFromURL(PagePriority.MAIL_READ, url, Collections.emptyMap()));
                Elements messages = msgDom.select(".blue-msg, .red-msg");
                for (Element message : messages) {
                    messagesColor.add(new MailMessage(message));
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
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
            this.content = MarkupUtil.htmlToMarkdown(secondChild.text());
            Element header = message.child(0);
            // get the nation id
            Element nationLink = header.selectFirst("a");
            String nationIdStr = nationLink.attr("href").split("=")[1];
            this.nationId = Integer.parseInt(nationIdStr);
            String dateStr = header.textNodes().stream().map(TextNode::text).collect(Collectors.joining()).trim();
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MM/dd/yyyy EEEE h:mm a")
                    .toFormatter(Locale.US);
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
