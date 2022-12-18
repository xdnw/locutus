package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionChangePermissionEvent extends PositionChangeEvent{
    public PositionChangePermissionEvent(DBAlliancePosition previous, DBAlliancePosition current) {
        super(previous, current);
    }
}
