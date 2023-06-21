package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class DBBounty {
    private final int id;
    private final long date;
    private final int nationId;
    private final int postedBy;
    private final WarType type;
    private final long amount;

    public DBBounty(int id, long date, int nationId, int postedBy, WarType type, long amount) {
        this.id = id;
        this.date = date;
        this.nationId = nationId;
        this.postedBy = postedBy;
        this.type = type;
        this.amount = amount;
    }

    @Deprecated
    public static DBBounty fromLegacy(ResultSet rs) throws SQLException {
        return new DBBounty(-1,
                rs.getLong("date"),
                rs.getInt("nation_id"),
                rs.getInt("posted_by"),
                WarType.values()[rs.getInt("attack_type")],
                rs.getLong("amount"));
    }

    public DBBounty(ResultSet rs) throws SQLException {
        this(
                rs.getInt("id"),
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

    public void toCard(MessageChannel channel, boolean deanonymize) {
        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
        if (nation == null) return;

        Set<DBBounty> bounties = Locutus.imp().getWarDb().getBounties(nationId);

        long total = 0;
        for (DBBounty bounty : bounties) {
            if (bounty.type == type) total += bounty.amount;
        }


        String title = nation.getNation() + " | " + type + " | $" + MathMan.format(total);
        String embed = nation.toEmbedString(true);
        if (deanonymize && postedBy > 0) embed += "\nPosted by: " + PnwUtil.getName(postedBy, false);
        DiscordUtil.createEmbedCommand(channel.getIdLong(), title, embed);
    }

    public int getId() {
        return id;
    }
}
