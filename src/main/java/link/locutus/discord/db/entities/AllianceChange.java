package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.Rank;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AllianceChange {
    private final int nationId;
    private final int fromAA, toAA;
    private final Rank fromRank, toRank;
    private final long date;

    public AllianceChange(int nationId, int fromAA, int toAA, Rank fromRank, Rank toRank, long date) {
        this.nationId = nationId;
        if (fromRank == null) fromRank = Rank.REMOVE;
        if (toRank == null) fromRank = Rank.REMOVE;
        this.fromAA = fromAA;
        this.toAA = toAA;
        this.fromRank = fromRank;
        this.toRank = toRank;
        this.date = date;
    }

    public AllianceChange(ResultSet rs) throws SQLException {
        this.nationId = rs.getInt("nation");
        this.fromAA = rs.getInt("from_aa");
        this.fromRank = Rank.values[(rs.getInt("from_rank"))];
        this.toAA = rs.getInt("to_aa");
        this.toRank = Rank.values[(rs.getInt("to_rank"))];
        this.date = rs.getLong("date");
    }

    public AllianceChange(DBNation previous, DBNation current, long time) {
        this.nationId = current.getId();
        this.fromAA = previous.getAlliance_id();
        this.fromRank = previous.getPositionEnum();
        this.toAA = current.getAlliance_id();
        this.toRank = current.getPositionEnum();
        this.date = time;
    }

    public int getNationId() {
        return nationId;
    }

    public int getFromId() {
        return fromAA;
    }

    public int getToId() {
        return toAA;
    }

    public Rank getFromRank() {
        return fromRank;
    }

    public Rank getToRank() {
        return toRank;
    }

    public long getDate() {
        return date;
    }

    public DBAlliance getFromAlliance() {
        return DBAlliance.getOrCreate(fromAA);
    }

    public DBAlliance getToAlliance() {
        return DBAlliance.getOrCreate(toAA);
    }
}
