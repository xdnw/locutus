package link.locutus.discord.db.entities;

import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBLoan {
    public int loanId;
    public long loanerGuildOrAA;
    public int loanerNation;
    public int nationOrAllianceId;
    public boolean isAlliance;
    public double[] principal;
    public double[] remaining;
    public Status status;
    public long dueDate;
    public long loanDate;
    public long date_submitted;

    // constructor
    public DBLoan(ResultSet rs) throws SQLException {
        this.loanId = rs.getInt("loan_id");
        this.loanerGuildOrAA = rs.getLong("loaner_guild_or_aa");
        this.loanerNation = rs.getInt("loaner_nation");
        long id = rs.getLong("receiver");
        if (id < 0) {
            this.isAlliance = true;
            this.nationOrAllianceId = (int) -id;
        } else {
            this.isAlliance = false;
            this.nationOrAllianceId = (int) id;
        }
        this.principal = ArrayUtil.toDoubleArray(rs.getBytes("principal"));
        this.remaining = ArrayUtil.toDoubleArray(rs.getBytes("remaining"));
        this.status = Status.values()[rs.getInt("status")];
        this.dueDate = rs.getLong("due_date");
        this.loanDate = rs.getLong("loan_date");
        this.date_submitted = rs.getLong("date_submitted");
    }

    public Status getStatus() {
        return status;
    }

    public long getLoanDate() {
        return loanDate;
    }

    public enum Status {
        OPEN("\uD83D\uDEAA"), // 1f6aa
        CLOSED("\uD83D\uDD10"), // U+1f510
        EXTENDED("\u21aa\ufe0f"), // U+21aaU+fe0f
        MISSED_PAYMENT("\u23f1\ufe0f"),
        DEFAULTED("\u274c"),
        ;
        public final String emoji;

        Status(String s) {
            this.emoji = s;
        }
    }
}
