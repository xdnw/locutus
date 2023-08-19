package link.locutus.discord.db.entities;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DiscordBan {
    // user long, server long, date long, reason string, mod long
    public long user;
    public long server;
    public long date;
    public String reason;

    public DiscordBan(ResultSet rs) throws SQLException {
        user = rs.getLong("user");
        server = rs.getLong("server");
        date = rs.getLong("date");
        reason = rs.getString("reason");
    }

    public DiscordBan(long user, long server, long date, String reason) {
        this.user = user;
        this.server = server;
        this.date = date;
        this.reason = reason;
    }
}
