package link.locutus.discord.db.entities;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.User;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBLoan {
    public int loanId;
    public long loanerGuildOrAA;
    public int loanerNation;
    public int nationOrAllianceId;
    public boolean isAlliance;
    public double[] principal;
    public double[] paid;
    public double[] remaining;
    public Status status;
    public long dueDate;
    public long loanDate;
    public long date_submitted;

    public DBLoan(
            long loanerGuildOrAA,
            int loanerNation,
            int nationOrAllianceId,
            boolean isAlliance,
            double[] principal,
            double[] paid,
            double[] remaining,
            Status status,
            long dueDate,
            long loanDate,
            long date_submitted
    ) {
        this.loanerGuildOrAA = loanerGuildOrAA;
        this.loanerNation = loanerNation;
        this.nationOrAllianceId = nationOrAllianceId;
        this.isAlliance = isAlliance;
        this.principal = principal;
        this.paid = paid;
        this.remaining = remaining;
        this.status = status;
        this.dueDate = dueDate;
        this.loanDate = loanDate;
        this.date_submitted = date_submitted;
    }

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
        this.principal = ArrayUtil.toDoubleArray(rs.getBytes("paid"));
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

    public String getSenderQualifiedName() {
        return (isAlliance ? "AA:" : "" )+ PW.getName(nationOrAllianceId, isAlliance);
    }

    public String getReceiverQualifiedName() {
        if (this.loanerGuildOrAA > Integer.MAX_VALUE) {
            return "guild:" + DiscordUtil.getGuildName((int) this.loanerGuildOrAA);
        }
        return PW.getName(this.loanerNation, false);
    }

    public String getLineString(boolean showReceiver, boolean showSender) {
//        return #").append(loan.loanId).append(" ").append(loan.status)
//                .append("updated:" + DiscordUtil.timestamp(loan.date_submitted, null))
        StringBuilder response = new StringBuilder("#" + loanId + " " + status + ": ");
        if (showReceiver) {
            response.append(PW.getMarkdownUrl(nationOrAllianceId, isAlliance));
        }
        if (status != Status.DEFAULTED && status != Status.CLOSED) {
            if (remaining != null && !ResourceType.isZero(remaining)) {
                response.append(" | " + ResourceType.resourcesToString(remaining));
            }
            if (dueDate != 0) {
                response.append(" | next: " + DiscordUtil.timestamp(dueDate, null));
            }
        }
        if (showSender) {
            String senderStr;
            if (this.loanerGuildOrAA > Integer.MAX_VALUE) {
                senderStr = MarkupUtil.markdownUrl(DiscordUtil.getGuildUrl(this.loanerGuildOrAA), DiscordUtil.getGuildUrl(this.loanerGuildOrAA));
            } else {
                senderStr = PW.getMarkdownUrl(this.loanerNation, true);
            }
            response.append(" | " + senderStr);
        }
        return response.toString();
    }

    public String getLoanerString() {
        return loanerGuildOrAA > Integer.MAX_VALUE ? DiscordUtil.getGuildName(loanerGuildOrAA) : PW.getAllianceUrl((int) loanerGuildOrAA);
    }

    public boolean hasPermission(GuildDB db, DBNation me, User author) {
        if (!Roles.ADMIN.hasOnRoot(author) && loanerNation != me.getNation_id()) {
            if (loanerGuildOrAA > Integer.MAX_VALUE ? db.getIdLong() != loanerGuildOrAA : !db.isAllianceId((int) loanerGuildOrAA)) {
                return false;
            }
        }
        return true;
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
