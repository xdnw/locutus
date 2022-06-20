package link.locutus.discord.event.position;

import link.locutus.discord.db.entities.DBAlliancePosition;

public class PositionChangeLevelEvent extends PositionChangeEvent{
    public PositionChangeLevelEvent(DBAlliancePosition previous, DBAlliancePosition current) {
        super(previous, current);
    }
}
