package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.db.entities.DBComment;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ForumDB extends DBMain {

    private final long guildId;

    public ForumDB(long guildId) throws SQLException, ClassNotFoundException {
        super("forum", false, 0, 0);
        this.guildId = guildId;
    }

    public Guild getGuild() {
        return Locutus.imp().getDiscordApi().getGuildById(guildId);
    }

    public boolean update() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        try {
            List<DBComment> comments = updateAndGetNewComments();
            Guild guild = getGuild();
            if (guild == null) return true;

            List<Category> purgeCategories = new ArrayList<>();
            for (DBComment comment : comments) {
                if (comment.timestamp > cutoff) {
                    List<Category> discordCategories = guild.getCategoriesByName(comment.category_name.replace(" ", "-"), true);
                    if (discordCategories.isEmpty()) continue;
                    Category category = discordCategories.get(0);

                    purgeCategories.add(category);

                    String requiredChannelName = comment.topic_name.toLowerCase().replace("-", " ").replaceAll("[ ]+", " ").replaceAll("[^a-z0-9 ]", "") + " " + comment.topic_id;
                    requiredChannelName = requiredChannelName.replaceAll(" +", " ");
                    requiredChannelName = requiredChannelName.trim();
                    requiredChannelName = requiredChannelName.replaceAll(" ", "-");

                    List<TextChannel> channels = guild.getTextChannelsByName(requiredChannelName, true);
                    TextChannel channel = channels.isEmpty() ? null : channels.get(0);
                    if (channel == null) {
                        channel = RateLimitUtil.complete(category.createTextChannel(requiredChannelName));
                        RateLimitUtil.queue(channel.getManager().setTopic(comment.topicUrl()));
                    }

                    String title = comment.poster_name;
                    String body = comment.content;
                    String footer = "[link](" + comment.commentUrl() + ")";
                    DiscordUtil.createEmbedCommand(channel, title, footer + "\n" + body, new String[0]);
                }
            }

            for (Category category : purgeCategories) {
                for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
                    if (GuildMessageChannel.getLatestMessageIdLong() > 0) {
                        long message = GuildMessageChannel.getLatestMessageIdLong();
                        try {
                            Message msg = RateLimitUtil.complete(GuildMessageChannel.retrieveMessageById(message));
                            long created = msg.getTimeCreated().toEpochSecond() * 1000L;
                            if (created > cutoff) {
                                continue;
                            }
                        } catch (Throwable ignore) {}
                    }
                    RateLimitUtil.queue(GuildMessageChannel.delete());
                }
            }

            return true;
        } catch (IOException ignore) {
            ignore.printStackTrace();
            return false;
        }
    }

    @Override
    public void createTables() {
        {
            String nations = "CREATE TABLE IF NOT EXISTS `FORUM_POSTS` (`comment_id` INT PRIMARY KEY, `topic_id` INT NOT NULL, `poster_id` INT NOT NULL, `category_id` INT NOT NULL, `topic_name` VARCHAR NOT NULL, `topic_urlname` VARCHAR NOT NULL, `poster_name` VARCHAR NOT NULL, `category_name` VARCHAR NOT NULL, `content` VARCHAR NOT NULL, `timestamp` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String nations = "CREATE TABLE IF NOT EXISTS `FORUM_TOPICS` (`topic_id` INT PRIMARY KEY, `section_id` INT NOT NULL, `topic_name` VARCHAR NOT NULL, `topic_urlname` VARCHAR NOT NULL, `section_name` VARCHAR NOT NULL, `section_urlname` VARCHAR NOT NULL, `timestamp` BIGINT NOT NULL, `poster_id` INT NOT NULL, `poster_name` VARCHAR NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
    }

    public void addTopic(DBTopic topic) throws SQLException {
        String sql = "INSERT OR REPLACE INTO `FORUM_TOPICS` (`topic_id`, `section_id`, `topic_name`, `topic_urlname`, `section_name`, `section_urlname`, `timestamp`, `poster_id`, `poster_name`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setInt(1, topic.topic_id);
            stmt.setInt(2, topic.section_id);
            stmt.setString(3, topic.topic_name);
            stmt.setString(4, topic.topic_urlname);
            stmt.setString(5, topic.section_name);
            stmt.setString(6, topic.section_urlname);
            stmt.setLong(7, topic.timestamp);
            stmt.setInt(8, topic.poster_id);
            stmt.setString(9, topic.poster_name);
            stmt.execute();
        }
    }

    public DBTopic getTopic(int id) {
        try (PreparedStatement stmt = prepareQuery("select * FROM FORUM_TOPICS WHERE `topic_id` = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new DBTopic(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String get(String requestURL) throws IOException {
        return FileUtil.readStringFromURL(PagePriority.FORUM_PAGE, requestURL);
    }

    public void scrapeTopic(int section_id, String section_name) throws SQLException, IOException {
        String baseUrl = "https://forum.politicsandwar.com/index.php?/forum/" + section_id + "-" + section_name + "/";
        int page = 1;
        while (true) {
            String url = baseUrl + "page/" + page + "/";

            String content = get(url);
            Document html = Jsoup.parse(content);
            // get every ipsType_break ipsContained
            Elements elems = html.select(".ipsDataItem_main");
            for (Element elem : elems) {
                // get href
                Elements a = elem.select("a");
                String topicUrl = a.attr("href");
                String topicName = a.text();
                int topic_id = Integer.parseInt(topicUrl.split("topic/")[1].split("-")[0]);

                String topicUrlName = topicUrl.split("topic/")[1].split("-", 2)[1].split("/")[0];

                // get date
                Elements date = elem.select("time");
                String dateStr = date.attr("datetime");
                // to milliseconds
//                System.out.println("Date " + dateStr);
                DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
                ZonedDateTime dateTime = ZonedDateTime.parse(dateStr, formatter);
                long timestamp = dateTime.toInstant().toEpochMilli();

                // get poster id and name (611 and prefontaine)
                Elements poster = elem.select("a[href^='https://forum.politicsandwar.com/index.php?/profile/']");
                String posterName = poster.text();
                String posterUrl = poster.attr("href");
                int posterId = Integer.parseInt(posterUrl.split("profile/")[1].split("-")[0]);

                // public DBTopic(int topic_id, int section_id, String topic_name, String topic_urlname, String section_name, String section_urlname, long timestamp, int poster_id, String poster_name) {

                DBTopic topic = new DBTopic(topic_id, section_id, topicName, topicUrlName, section_name, section_name, timestamp, posterId, posterName);
                addTopic(topic);
            }

            // get highest data-page
            Elements pages = html.select("li.ipsPagination_page");
            // if exists
            if (pages.size() > 0) {
                // get last page
                Element lastPage = pages.get(pages.size() - 1);
                // get href
                String href = lastPage.select("a").attr("href");
                // get page number
                int highestPage = Integer.parseInt(href.split("page/")[1].split("/")[0]);
                if (highestPage > page) {
                    page++;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }
//
//    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
//        ForumDB db = new ForumDB(null);
//        db.scrapeTopic(42, "alliance-affairs");
//    }

    public String getSectionName(int id) {
        try (PreparedStatement stmt = prepareQuery("select `section_name` FROM FORUM_TOPICS WHERE `section_id` = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer getSectionId(String name) {
        try (PreparedStatement stmt = prepareQuery("select `section_id` FROM FORUM_TOPICS WHERE `section_name` = ?")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, String> getSectionIds() {
        Map<Integer, String> topics = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select `section_id`, `section_name` FROM FORUM_TOPICS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    topics.putIfAbsent(rs.getInt(1), rs.getString(2));
                }
            }
            return topics;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } }

    public List<DBComment> updateAndGetNewComments() throws IOException {
        Set<Integer> existing = getCommentIds();
        List<DBComment> newComments = new ArrayList<>();

        String url = "https://forum.politicsandwar.com/index.php?/discover/&view=expanded";
        String content = FileUtil.readStringFromURL(PagePriority.FORUM_PAGE, url);
        Document dom = Jsoup.parse(content);
        Elements elems = dom.select("[data-searchable]");
        for (Element elem : elems) {
            String topicName = elem.text();
            String topicUrl = elem.attr("href");
            int topicId = Integer.parseInt(topicUrl.split("topic/")[1].split("-")[0]);
            String topicUrlName = topicUrl.split("topic/")[1].split("-", 2)[1].split("/")[0];
            String[] split = topicUrl.split("=");
            int commentId = Integer.parseInt(split[split.length - 1]);

            Element signature = elem.parent().parent().nextElementSibling();
            Element posterElem = signature.select("a").first();
            String posterUrl = posterElem.attr("href");
            int posterId = Integer.parseInt(posterUrl.split("profile/")[1].split("-")[0]);
            String posterName = posterElem.text();

            Element categoryElem = signature.select("a").last();
            String categoryUrl = categoryElem.attr("href");
            int categoryId = Integer.parseInt(categoryUrl.split("forum/")[1].split("-")[0]);
            String category = categoryElem.text();

            Element postElem = elem.parent().parent().parent().parent().nextElementSibling();
            String post = MarkupUtil.htmlToMarkdown(postElem.html());

            long timestamp = System.currentTimeMillis();
            DBComment comment = new DBComment(commentId, topicId, posterId, categoryId, topicName, topicUrlName, posterName, category, post, timestamp);
            if (existing.contains(commentId)) continue;

            newComments.add(comment);
            addComment(comment);
        }
        Collections.reverse(newComments);
        return newComments;
    }

    public void addComment(DBComment comment) {
        update("INSERT OR REPLACE INTO `FORUM_POSTS`(`comment_id`, `topic_id`, `poster_id`, `category_id`, `topic_name`, `topic_urlname`, `poster_name`, `category_name`, `content`, `timestamp`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, comment.comment_id);
            stmt.setInt(2, comment.topic_id);
            stmt.setInt(3, comment.poster_id);
            stmt.setInt(4, comment.category_id);
            stmt.setString(5, comment.topic_name);
            stmt.setString(6, comment.topic_urlname);
            stmt.setString(7, comment.poster_name);
            stmt.setString(8, comment.category_name);
            stmt.setString(9, comment.content);
            stmt.setLong(10, comment.timestamp);
        });
    }

    public Set<Integer> getCommentIds() {
        Set<Integer> comments = new IntOpenHashSet();
        try (PreparedStatement stmt = prepareQuery("select `comment_id` FROM FORUM_POSTS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    comments.add(rs.getInt(1));
                }
            }
            return comments;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, DBComment> getComments() {
        ArrayList<DBComment> comments = new ArrayList<DBComment>();
        try (PreparedStatement stmt = prepareQuery("select * FROM FORUM_POSTS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    comments.add(new DBComment(rs));
                }
            }
            Map<Integer, DBComment> map = comments.stream().collect(Collectors.toMap(a -> a.comment_id, a -> a));
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public DBTopic loadTopic(int topicId, String topicUrlStub) throws SQLException, IOException {
        String url = "https://forum.politicsandwar.com/index.php?/topic/" + topicId + "-" + topicUrlStub + "/";
        Document doc = Jsoup.connect(url).get();

        // select ipsType_reset ipsType_blendLinks
        Element elems = doc.select(".ipsPageHeader__meta").first();
        // a with https://forum.politicsandwar.com/index.php?/profile
        String profilePrefix = "https://forum.politicsandwar.com/index.php?/profile/";
        Element profileLink = elems.select("a[href^='" + profilePrefix + "']").last();
        if (profileLink == null) {
            Logg.info("No profile link found for " + url);
            return null;
        }
        String[] profileSplit = profileLink.attr("href").replace(profilePrefix, "").split("/")[0].split("-", 2);
        int profileId = Integer.parseInt(profileSplit[0]);
        String profileUrlStub = profileSplit[1];
        String profileName = profileLink.text();

        // parent topic
        // https://forum.politicsandwar.com/index.php?/forum/
        String forumPrefix = "https://forum.politicsandwar.com/index.php?/forum/";
        Element sectionLink = elems.select("a[href^='" + forumPrefix + "']").first();
        String[] sectionSplit = sectionLink.attr("href").replace(forumPrefix, "").split("/")[0].split("-", 2);
        int sectionId = Integer.parseInt(sectionSplit[0]);
        String sectionUrlStub = sectionSplit[1];
        String sectionName = sectionLink.html();

        Element time = elems.select("time[datetime]").first();
        String dateStr = time.attr("datetime");
        DateTimeFormatter formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        ZonedDateTime dateTime = ZonedDateTime.parse(dateStr, formatter);
        long millis = dateTime.toInstant().toEpochMilli();

        String pageTitle = doc.select(".ipsType_pageTitle").first().text();

        DBTopic topic = new DBTopic(topicId, sectionId, pageTitle, topicUrlStub, sectionName, sectionUrlStub, millis, profileId, profileUrlStub);
        addTopic(topic);
        return topic;
    }

    public Map<Integer, DBTopic> getTopics(Set<Integer> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        List<Integer> idsSorted = new ArrayList<>(ids);
        Collections.sort(idsSorted);
        String sql = "SELECT * FROM FORUM_TOPICS WHERE `topic_id` IN " + StringMan.getString(idsSorted);
        try (PreparedStatement stmt = prepareQuery(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Integer, DBTopic> map = new HashMap<>();
                while (rs.next()) {
                    DBTopic topic = new DBTopic(rs);
                    map.put(topic.topic_id, topic);
                }
                return map;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
