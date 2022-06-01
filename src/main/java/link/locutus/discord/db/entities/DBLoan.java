package link.locutus.discord.db.entities;

import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DBLoan {
    public int loanId;
    public long serverId;
    public long messageId;
    public int nationId;
    public double[] resources;
    public long dueDate;
    public Status status;

    public DBLoan(int loanId, long serverId, long messageId, int nationId, double[] resources, long dueDate, Status status) {
        this.loanId = loanId;
        this.serverId = serverId;
        this.messageId = messageId;
        this.nationId = nationId;
        this.resources = resources;
        this.dueDate = dueDate;
        this.status = status;
    }

    public DBLoan(ResultSet set) throws SQLException {
        this.loanId = set.getInt("loan_id");
        this.serverId = set.getLong("server");
        this.messageId = set.getLong("message");
        this.nationId = set.getInt("receiver");
        this.resources = new double[ResourceType.values.length];
        long[] resourcesLong = ArrayUtil.toLongArray(set.getBytes("resources"));
        for (int i = 0; i < resourcesLong.length; i++) resources[i] = resourcesLong[i] / 100d;
        this.dueDate = set.getLong("due");
        this.status = Status.values()[set.getInt("repaid")];
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

    public void update() {
//        JDA jda = Locutus.imp().getDiscordApi();
        //TODO
    }
}
