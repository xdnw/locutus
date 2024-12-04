package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityCreateEvent extends CityChangeEvent {
    public CityCreateEvent(int nation, DBCity current) {
        super(nation, null, current);
        setTime(current.getCreated());
    }
}
