package link.locutus.discord.db.entities;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBComment {
    public final int comment_id;
    public final int topic_id;
    public final int poster_id;
    public final int category_id;
    public final String topic_name;
    public final String topic_urlname;
    public final String poster_name;
    public final String category_name;
    public final String content;
    public final long timestamp;

    public DBComment(int comment_id, int topic_id, int poster_id, int category_id, String topic_name, String topic_urlname, String poster_name, String category_name, String content, long timestamp) {
        this.comment_id = comment_id;
        this.topic_id = topic_id;
        this.poster_id = poster_id;
        this.category_id = category_id;
        this.topic_name = topic_name;
        this.topic_urlname = topic_urlname;
        this.poster_name = poster_name;
        this.category_name = category_name;
        this.content = content;
        this.timestamp = timestamp;
    }

    public DBComment(ResultSet rs) throws SQLException {
        this.comment_id = rs.getInt("comment_id");
        this.topic_id = rs.getInt("topic_id");
        this.poster_id = rs.getInt("poster_id");
        this.category_id = rs.getInt("category_id");
        this.topic_name = rs.getString("topic_name");
        this.topic_urlname = rs.getString("topic_urlname");
        this.poster_name = rs.getString("poster_name");
        this.category_name = rs.getString("category_name");
        this.content = rs.getString("content");
        this.timestamp = rs.getLong("timestamp");
    }

    public String topicUrl() {
        return "https://forum.politicsandwar.com/index.php?/topic/" + topic_id + "-" + topic_urlname + "/";
    }

    public String categoryUrl() {
        return "https://forum.politicsandwar.com/index.php?/forum/" + category_id + "-" + category_name.toLowerCase().replace(" ", "-") + "/";
    }

    public String posterUrl() {
        return "https://forum.politicsandwar.com/index.php?/profile/" + poster_id + "-" + poster_id + "/";
    }

    public String commentUrl() {
        return topicUrl() + "&do=findComment&comment=" + comment_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DBComment dbComment = (DBComment) o;

        return comment_id == dbComment.comment_id;
    }

    @Override
    public int hashCode() {
        return comment_id;
    }
}
