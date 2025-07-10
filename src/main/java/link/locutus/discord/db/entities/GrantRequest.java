package link.locutus.discord.db.entities;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GrantRequest {
    private int id;
    private final long userId;
    private final int nationId;
    private final int receiverId;
    private final int receiverType; // 0 = nation, 1 = alliance, 2 = guild
    private final String reason;
    private final JSONObject command;
    private final long channel;
    private long message_id;
    private final double[] estimated_amount;
    private final long dateCreated;

    public GrantRequest(ResultSet rs) throws SQLException {
        id               = rs.getInt("id");
        userId           = rs.getLong("user");
        nationId         = rs.getInt("nation");
        receiverId       = rs.getInt("receiver");
        receiverType     = rs.getInt("receiver_type");
        reason           = rs.getString("reason");
        command          = new JSONObject(rs.getString("command"));
        channel          = rs.getLong("channel");
        message_id       = rs.getLong("message_id");
        estimated_amount = (double[]) rs.getObject("estimate_amount");
        dateCreated      = rs.getLong("date_created");
    }

    public GrantRequest(long userId, int nationId, int receiverId, int receiverType, String reason, JSONObject command, long channel, long message_id, double[] estimated_amount, long dateCreated) {
        this.userId = userId;
        this.nationId = nationId;
        this.receiverId = receiverId;
        this.receiverType = receiverType;
        this.reason = reason;
        this.command = command;
        this.channel = channel;
        this.message_id = message_id;
        this.estimated_amount = estimated_amount;
        this.dateCreated = dateCreated;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public int getNationId() {
        return nationId;
    }

    public int getReceiverId() {
        return receiverId;
    }

    public int getReceiverType() {
        return receiverType;
    }

    public String getReason() {
        return reason;
    }

    public JSONObject getCommand() {
        return command;
    }

    public long getChannel() {
        return channel;
    }

    public long getMessageId() {
        return message_id;
    }

    public double[] getEstimatedAmount() {
        return estimated_amount;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public void setMessageId(long id) {
        this.message_id = id;
    }

    public void deleteMessage(GuildDB db) {
        GuildDB delegate = db.getDelegateServer();
        if (delegate == null) delegate = db;
        Guild guild = db.getGuild();
        GuildMessageChannel reqChannel = guild.getTextChannelById(getChannel());
        if (reqChannel != null) {
            RateLimitUtil.queue(reqChannel.deleteMessageById(getMessageId()));
        }
    }
}
