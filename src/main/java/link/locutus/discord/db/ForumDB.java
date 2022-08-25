package link.locutus.discord.db;

import link.locutus.discord.db.entities.DBComment;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ForumDB extends DBMain {
    private final Guild guild;

    public ForumDB(Guild guild) throws SQLException, ClassNotFoundException {
        super("forum");
        this.guild = guild;
    }

    public boolean update() {
        try {
            List<DBComment> comments = updateAndGetNewComments();
            List<Category> purgeCategories = new ArrayList<>();
            for (DBComment comment : comments) {

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

            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
            for (Category category : purgeCategories) {
                for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
                    if (GuildMessageChannel.hasLatestMessage()) {
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
    }

    public List<DBComment> updateAndGetNewComments() throws IOException {
        Set<Integer> existing = getCommentIds();
        List<DBComment> newComments = new ArrayList<>();

        String url = "https://forum.politicsandwar.com/index.php?/discover/&view=expanded";
        String content = FileUtil.readStringFromURL(url);
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
        Set<Integer> comments = new HashSet<>();
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
}
