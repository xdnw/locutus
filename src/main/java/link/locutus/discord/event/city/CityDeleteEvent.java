package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

public class CityDeleteEvent extends CityChangeEvent {
    public CityDeleteEvent(int nation, DBCity previous) {
        super(nation, previous, null);
    }
}
