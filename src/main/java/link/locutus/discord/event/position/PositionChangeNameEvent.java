package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionChangeNameEvent extends PositionChangeEvent{
    public PositionChangeNameEvent(DBAlliancePosition previous, DBAlliancePosition current) {
        super(previous, current);
    }
}
