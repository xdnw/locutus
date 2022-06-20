package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;

public class CityPowerChangeEvent extends CityChangeEvent {
    public CityPowerChangeEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
