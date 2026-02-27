package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.TreatyType;

/**
 * A single row from the TREATY_CHANGES history table.
 */
public class DBTreatyChange {
    private final long timestamp;
    private final TreatyChangeAction action;
    private final TreatyType treatyType;
    private final int fromAllianceId;
    private final int toAllianceId;
    private final int turnsRemaining;

    public DBTreatyChange(long timestamp, TreatyChangeAction action, TreatyType treatyType,
                          int fromAllianceId, int toAllianceId, int turnsRemaining) {
        this.timestamp = timestamp;
        this.action = action;
        this.treatyType = treatyType;
        this.fromAllianceId = fromAllianceId;
        this.toAllianceId = toAllianceId;
        this.turnsRemaining = turnsRemaining;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public TreatyChangeAction getAction() {
        return action;
    }

    public TreatyType getTreatyType() {
        return treatyType;
    }

    public int getFromAllianceId() {
        return fromAllianceId;
    }

    public int getToAllianceId() {
        return toAllianceId;
    }

    public int getTurnsRemaining() {
        return turnsRemaining;
    }

    @Override
    public String toString() {
        return "DBTreatyChange{" +
                "timestamp=" + timestamp +
                ", action=" + action +
                ", treatyType=" + treatyType +
                ", from=" + fromAllianceId +
                ", to=" + toAllianceId +
                ", turnsRemaining=" + turnsRemaining +
                '}';
    }
}

