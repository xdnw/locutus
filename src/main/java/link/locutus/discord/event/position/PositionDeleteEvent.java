package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionDeleteEvent extends PositionChangeEvent{
    public PositionDeleteEvent(DBAlliancePosition current) {
        super(current, null);
    }
}
