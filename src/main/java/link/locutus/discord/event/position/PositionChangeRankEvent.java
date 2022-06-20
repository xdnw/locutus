package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionChangeRankEvent extends PositionChangeEvent{
    public PositionChangeRankEvent(DBAlliancePosition previous, DBAlliancePosition current) {
        super(previous, current);
    }
}
