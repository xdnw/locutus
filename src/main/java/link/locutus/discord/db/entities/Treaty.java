package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import org.jooq.meta.derby.sys.Sys;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Treaty {
    private final int id;
    private final long date;
    private final TreatyType type;
    private final int from;
    private final int to;
    private final long turn_ends;
    private final boolean pending;

    public Treaty(com.politicsandwar.graphql.model.Treaty v3) {
        this.id = v3.getId();
        this.from = v3.getAlliance1_id();
        this.to = v3.getAlliance2_id();
        this.type = TreatyType.valueOf(v3.getTreaty_type().toUpperCase(Locale.ROOT));
        this.turn_ends = (v3.getTurns_left() == -3) ? Long.MAX_VALUE : TimeUtil.getTurn() + v3.getTurns_left() + 1;
        this.date = v3.getDate().toEpochMilli();
        this.pending = v3.getApproved() != null && !v3.getApproved();
    }

    public boolean isPermanent() {
        return turn_ends == Long.MAX_VALUE;
    }

    public Treaty(int id, long date, TreatyType type, int from, int to, long turn_ends) {
        this.id = id;
        this.date = date;
        this.type = type;
        this.from = from;
        this.to = to;
        this.turn_ends = turn_ends;
        this.pending = false;
    }

    @Command(desc = "If this treaty is pending approval")
    public boolean isPending() {
        return pending;
    }

    @Command
    public String getExpiresString() {
        if (isPermanent()) {
            return "Permanent";
        }
        return TimeUtil.secToTime(TimeUnit.MILLISECONDS, TimeUtil.getTimeFromTurn(getTurnEnds() - TimeUtil.getTurn()))
    }

    @Command(desc = "Absolute turns (since unix epoch) this treaty ends")
    public long getTurnEnds() {
        return turn_ends;
    }

    @Command(desc = "Number of turns until this treaty ends")
    public int getTurnsRemaining() {
        // int turn = TimeUtil.getTurn();
        if (turn_ends == Long.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(0, turn_ends - TimeUtil.getTurn());
    }

    @Command(desc = "id of this treaty")
    public int getId() {
        return id;
    }

    @Command(desc = "Date this treaty was signed")
    public long getDate() {
        return date;
    }

    @Command(desc = "Type of this treaty")
    public TreatyType getType() {
        return type;
    }

    public int getMinFromToId() {
        return Math.min(getFromId(), getToId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Treaty treaty = (Treaty) o;
        return treaty.id == this.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Command(desc = "id of the alliance that sent this treaty")
    public int getFromId() {
        return from;
    }

    @Command(desc = "id of the alliance that received this treaty")
    public int getToId() {
        return to;
    }

    @Command(desc = "Get the alliance that sent this treaty")
    public DBAlliance getFrom() {
        return DBAlliance.getOrCreate(getFromId());
    }

    @Command(desc = "Get the alliance that received this treaty")
    public DBAlliance getTo() {
        return DBAlliance.getOrCreate(getToId());
    }

    @Command(desc = "If this treaty is between the given alliances")
    public boolean isAlliance(@NoFormat Set<DBAlliance> fromOrTo) {
        return fromOrTo.contains(getFrom()) || fromOrTo.contains(getTo());
    }

    @Override
    public String toString() {
        return "Treaty{" +
                "id=" + id +
                ", date=" + date +
                ", type=" + type +
                ", from=" + from +
                ", to=" + to +
                ", turn_ends=" + turn_ends +
                '}';
    }

    @Command
    public String toLineString() {
        return type + ":" + PW.getName(from, true) + "/" + PW.getName(to, true);
    }

    @Command(desc = "Timestamp the treaty ends")
    public long getEndTime() {
        if (isPermanent()) return Long.MAX_VALUE;
        return Math.max(System.currentTimeMillis(), TimeUtil.getTimeFromTurn(getTurnEnds() - 1));
    }

    @Command
    public String getExpiresDiscordString() {
        if (isPermanent()) {
            return "Permanent";
        }
        return DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(turn_ends), null);
    }
}
