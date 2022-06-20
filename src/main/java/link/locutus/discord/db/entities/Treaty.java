package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.util.TimeUtil;

import java.util.Locale;

public class Treaty {
    private final int id;
    private final long date;
    private final TreatyType type;
    private final int from, to;
    private final long turn_ends;

    public Treaty(com.politicsandwar.graphql.model.Treaty v3) {
        this.id = v3.getId();
        this.from = v3.getAlliance1_id();
        this.to = v3.getAlliance2_id();
        this.type = TreatyType.valueOf(v3.getTreaty_type().toUpperCase(Locale.ROOT));
        this.turn_ends = TimeUtil.getTurn() + v3.getTurns_left();
        this.date = v3.getDate().toEpochMilli();
    }

    public Treaty(int id, long date, TreatyType type, int from, int to, long turn_ends) {
        this.id = id;
        this.date = date;
        this.type = type;
        this.from = from;
        this.to = to;
        this.turn_ends = turn_ends;
    }

    public long getTurnEnds() {
        return turn_ends;
    }

    public int getId() {
        return id;
    }

    public int getFromId() {
        return from;
    }

    public int getToId() {
        return to;
    }

    public long getDate() {
        return date;
    }

    public TreatyType getType() {
        return type;
    }

    public int getMinFromToId() {
        return Math.min(from, to);
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
}
