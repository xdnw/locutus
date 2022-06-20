package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionCreateEvent extends PositionChangeEvent{
    public PositionCreateEvent(DBAlliancePosition current) {
        super(null, current);
    }
}
