package link.locutus.discord.util.task.mail;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class SearchMailTask implements Callable<List<Mail>> {
    private final String url;
    private final BiConsumer<Mail, List<String>> onEach;
    private final Auth auth;
    private final boolean checkUnread;
    private final boolean checkRead;
    private final boolean readContent;
    private Predicate<DBNation> allowFilter;
    private boolean skipUnread;

    public SearchMailTask(Auth auth, String query, boolean checkUnread, boolean checkRead, boolean readContent, BiConsumer<Mail, List<String>> onEach) {
        if (query == null || query.isEmpty()) {
            this.url = "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=16&backpage=%3C%3C&maximum=100&minimum=0&od=DESC&searchTerm=";
        } else {
            this.url = "https://politicsandwar.com/index.php?id=16&backpage=%3C%3C&maximum=15000&minimum=0&od=DESC&searchTerm=" + URLEncoder.encode(query);
        }
        System.out.println("URL " + url);
        this.checkUnread = checkUnread;
        this.checkRead = checkRead;
        this.readContent = readContent;
        
        this.onEach = onEach;
        this.auth = auth;
    }

    public SearchMailTask addNationFilter(Predicate<DBNation> allow) {
        this.allowFilter = allow;
        return this;
    }

    public SearchMailTask skipUnread(boolean value) {
        this.skipUnread = value;
        return this;
    }

    @Override
    public List<Mail> call() {
        return PW.withLogin(new Callable<List<Mail>>() {
            @Override
            public List<Mail> call() throws Exception {
                ArrayList<Mail> response = new ArrayList<Mail>();

                HashMap<String, String> post = new HashMap<>();
                post.put("id", "16");
                post.put("maximum", "15000");
                post.put("minimum", "0");
                post.put("od", "DESC");

                String html = auth.readStringFromURL(PagePriority.MAIL_SEARCH, url, post);

                if (html.contains("Are You Human?\n") || html.contains("https://politicsandwar.com/human/")) {
                    throw new IllegalArgumentException("Captcha");
                }
                Document dom = Jsoup.parse(html);


                Elements tables = dom.getElementsByClass("nationtable");
                if (tables.isEmpty()) {
                    String alerts = PW.getAlert(dom);
                    if (alerts != null && !alerts.isEmpty()) {
                        AlertUtil.error("Could not check mail: " + auth.getNationId(), alerts);
                    } else {
                        AlertUtil.error("Could not check mail (2): " + auth.getNationId(), html);
                    }
                    return Collections.emptyList();
                }
                Element table = tables.get(0);
                Elements rows = table.getElementsByTag("tr");

                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements columns = row.getElementsByTag("td");
                    boolean unread = row.classNames().contains("bold");
                    boolean recRead = row.html().contains("Receipient Read");

                    if (skipUnread && !recRead) continue;

                    if (unread && checkUnread || !unread && checkRead) {
                        System.out.println("Row " + row.html());
                        System.out.println("Columns " + columns.html());
                        String url = columns.get(2).getElementsByTag("a").first().attr("href");
                        int msgId = Integer.parseInt(url.split("=")[1]);

                        String subject = columns.get(2).text();
                        String leader = columns.get(3).text();
                        int nationId = Integer.parseInt(columns.get(3).getElementsByTag("a").first().attr("href").split("=")[1]);
                        if (allowFilter != null) {
                            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                            if (nation == null || !allowFilter.test(nation)) continue;
                        }

                        Mail mail = new Mail(msgId, subject, leader, nationId, null);
                        List<String> messagesStr = new ArrayList<>();

                        if (readContent) {
                            List<ReadMailTask.MailMessage> replies = new ReadMailTask(auth, msgId).call();
                            for (ReadMailTask.MailMessage reply : replies) {
                                if (reply.isReceived()) {
                                    messagesStr.add(reply.getContent());
                                }
                            }
                        } else {
                            messagesStr.add("" + recRead);
                        }
                        if (onEach != null) onEach.accept(mail, messagesStr);
                        response.add(mail);
//                        {
//                            String discordFormat = String.format(format, leader, nationId, subject, msgMarkdown);
//                            Mail mail = new Mail(msgId, subject, leader, nationId, discordFormat);
//                            response.add(mail);
//                            onEach.accept();
////                            if (firstMsg.charAt(0) == '!') {
////                                DBNation dbNation = Locutus.imp().getNationDB().getNation(nationId);
////
////                                if (dbNation == null) continue;
////                                PNWUser pnwUser = DiscordUtil.getUser(dbNation);
////                                if (pnwUser == null) continue;
////                                User user = Locutus.imp().getDiscordApi().getUserById(pnwUser.getDiscordId());
////
////                                String result = Locutus.imp().getCommandManager().run(guild, firstMsg, dbNation, user);
////
////                                String resultBBCode = MarkupUtil.markdownToBBCode(result);
////
////                                post.clear();
////                                post.put("receiver", leader);
////                                post.put("convoid", Integer.toString(msgId));
////                                post.put("body", resultBBCode);
////                                post.put("sndmsg", "Send Message");
////
////                                Document successDom = Jsoup.parse(FileUtil.readStringFromURL(url, post));
////                                for (Element element : successDom.getElementsByClass("alert")) {
////                                    String txt = element.text();
////                                    if (!txt.startsWith("Player Advertisement")) {
////                                        AlertUtil.displayChannel("Send Status: ", txt, Settings.INSTANCE.Discord.Channel.MAIL_RESPONSES);
////                                    }
////                                }
////                            }
//                        }
                    }
                }

                return response;
            }
        }, auth);
    }
}
