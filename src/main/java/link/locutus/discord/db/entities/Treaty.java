package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.TreatyType;

public class Treaty {
    public final int from, to;
    public final TreatyType type;
    public final long date;

    public Treaty(int from, int to, TreatyType type, long date) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.date = date;
    }

    public int getFrom() {
        return from;
    }

    public int getTo() {
        return to;
    }

    public long getDate() {
        return date;
    }

    public TreatyType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Treaty treaty = (Treaty) o;

        if (from != treaty.from) return false;
        if (to != treaty.to) return false;
        return type == treaty.type;
    }

    @Override
    public int hashCode() {
        int result = from;
        result = 31 * result + to;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}
