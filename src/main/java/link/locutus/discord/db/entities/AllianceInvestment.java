package link.locutus.discord.db.entities;

import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AllianceInvestment {
    public int id;
    public long accountId;
    public int accountType;
    public InvestmentType type;
    public double[] resources;
    public long date;
    public double reserveRatio;

    public AllianceInvestment(ResultSet rs) throws SQLException {
        this.id = rs.getInt("id");
        this.accountId = rs.getInt("account_id");
        this.accountType = rs.getInt("account_type");
        this.type = InvestmentType.values()[rs.getInt("type")];
        this.resources = ArrayUtil.toDoubleArray(rs.getBytes("resources"));
        this.date = rs.getLong("date");
        this.reserveRatio = rs.getLong("reserve_ratio") / 100d;
    }

    public enum InvestmentType {
        DONATION,
        SHARE,
        RESERVE,
    }
}
