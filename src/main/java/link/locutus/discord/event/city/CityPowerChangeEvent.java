package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityPowerChangeEvent extends CityChangeEvent {
    public CityPowerChangeEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
