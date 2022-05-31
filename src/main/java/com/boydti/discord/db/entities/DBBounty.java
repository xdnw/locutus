package com.boydti.discord.db.entities;

import com.boydti.discord.Locutus;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class DBBounty {
    private final long date;
    private final int nationId;
    private final int postedBy;
    private final WarType type;
    private final long amount;

    public DBBounty(long date, int nationId, int postedBy, WarType type, long amount) {
        this.date = date;
        this.nationId = nationId;
        this.postedBy = postedBy;
        this.type = type;
        this.amount = amount;
    }

    public DBBounty(ResultSet rs) throws SQLException {
        this(
                rs.getLong("date"),
                rs.getInt("nation_id"),
                rs.getInt("posted_by"),
                WarType.values()[rs.getInt("attack_type")],
                rs.getLong("amount")
        );
    }

    public long getDate() {
        return date;
    }

    public int getNationId() {
        return nationId;
    }

    public int getPostedBy() {
        return postedBy;
    }

    public WarType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DBBounty dbBounty = (DBBounty) o;

        if (date != dbBounty.date) return false;
        if (nationId != dbBounty.nationId) return false;
        if (amount != dbBounty.amount) return false;
        return type == dbBounty.type;
    }

    @Override
    public int hashCode() {
        int result = (int) (date ^ (date >>> 32));
        result = 31 * result + nationId;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "DBBounty{" +
                "date=" + date +
                ", nationId=" + nationId +
                ", postedBy=" + postedBy +
                ", type=" + type +
                ", amount=" + amount +
                '}';
    }

    public Message toCard(MessageChannel channel, boolean deanonymize) {
        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        if (nation == null) return null;

        Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(nationId);

        long total = 0;
        for (DBBounty bounty : bounties) {
            if (bounty.type == type) total += bounty.amount;
        }


        String title = nation.getNation() + " | " + type + " | $" + MathMan.format(total);
        String embed = nation.toEmbedString(true);
        if (deanonymize && postedBy > 0) embed += "\nPosted by: " + PnwUtil.getName(postedBy, false);
        return DiscordUtil.createEmbedCommand(channel.getIdLong(), title, embed);
    }
}
