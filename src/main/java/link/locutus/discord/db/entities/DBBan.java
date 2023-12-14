package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.BannedNation;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.DiscordUtil;

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

    public DBBan(int nation_id, long discord_id, String reason, long date, int days_left) {
        this.nation_id = nation_id;
        this.discord_id = discord_id;
        this.reason = reason;
        this.date = date;
        this.days_left = days_left;
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

    @Command(desc = "Nation ID banned")
    public int getNation_id() {
        return nation_id;
    }

    @Command(desc = "Get time remaining on this ban (in milliseconds)")
    public long getTimeRemaining() {
        if (days_left == -1) return Integer.MAX_VALUE;

        long now = System.currentTimeMillis();
        long expire = getEndDate();
        return Math.max(0, expire - now);
    }

    @Command(desc = "Is this ban expired")
    public boolean isExpired() {
        return getTimeRemaining() <= 0;
    }

    @Command(desc = "Get the date this ban expires (epoch milliseconds)")
    public long getEndDate() {
        return date + TimeUnit.DAYS.toMillis(days_left);
    }

    @Command(desc = "Discord ID banned")
    public long getDiscordId() {
        return discord_id;
    }

    @Command(desc = "Reason for ban")
    public String getReason() {
        return reason;
    }

    @Command(desc = "Date of ban")
    public long getDate() {
        return date;
    }

    @Command(desc = "Nation or discord corresponds to existing nation")
    public boolean hasExistingNation() {
        return getExistingNation() != null;
    }

    @Command(desc = "The existing nation (if any) that corresponds to this ban")
    public DBNation getExistingNation() {
        DBNation nation = DBNation.getById(nation_id);
        if (nation == null) {
            nation = DiscordUtil.getNation(discord_id);
        }
        return nation;
    }
}
