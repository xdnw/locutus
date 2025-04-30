package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityDeleteEvent extends CityChangeEvent {
    public CityDeleteEvent(int nation, DBCity previous) {
        super(nation, previous, null);
    }
}
