package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class AllianceLoan {
    // int id primary key autoincrement, bigint receiver, bigint banker_nation, bigint banker_user, bigint channel_id, byte[] principal, byte[] interest_amt, bigint due_date, bigint interest_rate, bigint overdue_rate, bigint last_paid
    public int id;

    public long receiver;
    public int bankerNation;
    public long bankerUser;

    public long channelId;
    public double[] principal;
    public double principalValue;
    public double[] interestPaid;
    public double interestPaidValue;

    public long date;
    public long dueDate;
    public long lastPaid;

    public long dateClosed;
    public double interestRate;
    public double overdueRate;

    public AllianceLoan(long receiver, int bankerNation, long bankerUser, long channelId, double[] principal, double principalValue, double[] interestPaid, double interestPaidValue, long date, long dueDate, long lastPaid, long dateClosed, long interestRate, long overdueRate) {
        this.id = -1;
        this.receiver = receiver;
        this.bankerNation = bankerNation;
        this.bankerUser = bankerUser;
        this.channelId = channelId;
        this.principal = principal;
        this.principalValue = principalValue;
        this.interestPaid = interestPaid;
        this.interestPaidValue = interestPaidValue;
        this.date = date;
        this.dueDate = dueDate;
        this.lastPaid = lastPaid;
        this.interestRate = interestRate;
        this.overdueRate = overdueRate;
        this.dateClosed = dateClosed;
    }

    public AllianceLoan(ResultSet rs) throws SQLException {
        this.id = rs.getInt("id");
        this.receiver = rs.getLong("receiver");
        this.bankerNation = rs.getInt("banker_nation");
        this.bankerUser = rs.getLong("banker_user");
        this.channelId = rs.getLong("channel_id");
        this.principal = ArrayUtil.toDoubleArray(rs.getBytes("principal"));
        this.principalValue = rs.getLong("principal_value") / 100d;
        this.interestPaid = ArrayUtil.toDoubleArray(rs.getBytes("interest_paid"));
        this.interestPaidValue = rs.getLong("paid_value") / 100d;
        this.date = rs.getLong("date");
        this.dueDate = rs.getLong("due_date");
        this.lastPaid = rs.getLong("last_paid");
        this.interestRate = rs.getDouble("interest_rate");
        this.overdueRate = rs.getDouble("overdue_rate");
        this.dateClosed = rs.getLong("date_closed");
    }

    public Map.Entry<Long, Long> getInterestTurns() {
        long turnStart = TimeUtil.getTurn(date);
        long turnDue = TimeUtil.getTurn(dueDate);
        long now = TimeUtil.getTurn();
        if (dateClosed > 0) {
            now = TimeUtil.getTurn(dateClosed);
        }

        long turnsInterest = Math.min(now - turnStart, turnDue - turnStart);
        long turnsOverdue = Math.max(0, now - turnDue);
        return Map.entry(turnsInterest, turnsOverdue);
    }

    public double getInterestValue() {
        Map.Entry<Long, Long> turns = getInterestTurns();
        long turnsInterest = turns.getKey();
        long turnsOverdue = turns.getValue();

        double interest = 0;
        if (turnsInterest > 0 && interestRate > 0) {
            interest += principalValue * interestRate * turnsInterest;
//            interest += (principalValue * Math.pow(1 + interestRate, turnsInterest)) - principalValue;
        }
        if (turnsOverdue > 0 && overdueRate > 0) {
            interest += principalValue * overdueRate * turnsOverdue;
//            interest += (principalValue * Math.pow(1 + overdueRate, turnsOverdue)) - principalValue;
        }
        return interest;
    }

    public boolean isClosed() {
        return dateClosed > 0;
    }

    public Map.Entry<double[], Double> getAmountOwed() {
        double[] interestLeft = interestPaid.clone();
        double[] remainingPrincipal = principal.clone();
        for (ResourceType type : ResourceType.values) {
            double amt = Math.min(remainingPrincipal[type.ordinal()], interestLeft[type.ordinal()]);
            if (amt > 0) {
                interestLeft[type.ordinal()] -= amt;
                remainingPrincipal[type.ordinal()] -= amt;
            }
            remainingPrincipal[type.ordinal()] -= principalValue;
        }

        double interestValue = getInterestValue();
        double remainingInterestValue = Math.max(0, interestValue - interestPaidValue);
        return Map.entry(remainingPrincipal, remainingInterestValue);
    }
}
