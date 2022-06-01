package link.locutus.discord.db.entities;

import link.locutus.discord.util.discord.DiscordUtil;
import com.google.gson.JsonArray;
import net.dv8tion.jda.api.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InterviewMessage {
    public final long channelId;
    public final long sender;
    public final long message_id, date_updated, date_created;
    public final String message;

    public InterviewMessage(long channelId, long sender, long message_id, long date_created, long date_updated, String message) {
        this.channelId = channelId;
        this.sender = sender;
        this.message_id = message_id;
        this.date_updated = date_updated;
        this.date_created = date_created;
        this.message = message;
    }

    public InterviewMessage(ResultSet rs) throws SQLException {
        this.channelId = rs.getLong("channel_id");
        this.sender = rs.getLong("sender");
        this.message_id = rs.getLong("message_id");
        this.date_created = rs.getLong("date_created");
        this.date_updated = rs.getLong("date_updated");
        this.message = rs.getString("message");
    }

    public String getAvatar() {
        User user = DiscordUtil.getUser(sender);
        return user == null ? null : user.getAvatarUrl();
    }

    public String getUsername() {
        User user = DiscordUtil.getUser(sender);
        return user == null ? "<@" + sender + ">" : user.getName() + "#" + user.getDiscriminator();
    }

    public static JsonArray toJsonList(List<InterviewMessage> messages) {
        JsonArray lines = new JsonArray();
        for (InterviewMessage msg : messages) {
            JsonArray line = new JsonArray();
            line.add(msg.sender);
            line.add(msg.date_created);
            line.add(msg.message);
            lines.add(line);
        }
        return lines;
    }
}
