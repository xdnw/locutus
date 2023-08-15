package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.BannedNation;
import link.locutus.discord.Locutus;
import link.locutus.discord.pnw.PNWUser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class DBBan {
    public int nation_id;
    public long discord_id;
    public String reason;
    public long date;
    public int days_left;

    public DBBan(ResultSet rs) throws SQLException {
        this.nation_id = rs.getInt("nation_id");
        this.discord_id = rs.getLong("discord_id");
        this.reason = rs.getString("reason");
        this.date = rs.getLong("date");
        this.days_left = rs.getInt("days_left");
    }

    public DBBan(BannedNation banQl) {
        this.nation_id = banQl.getNation_id();
        PNWUser pwUser = Locutus.imp().getDiscordDB().getUserFromNationId(this.nation_id);
        if (pwUser != null) {
            this.discord_id = pwUser.getDiscordId();
        }
        this.reason = banQl.getReason();
        this.date = banQl.getDate().toEpochMilli();
        this.days_left = banQl.getDays_left() == null ? Integer.MAX_VALUE : banQl.getDays_left();
    }

    public long getTimeRemaining() {
        if (days_left == -1) return Integer.MAX_VALUE;

        long now = System.currentTimeMillis();
        long expire = date + TimeUnit.DAYS.toMillis(days_left);
        return Math.max(0, expire - now);
    }

    public boolean isExpired() {
        return getTimeRemaining() <= 0;
    }
}
