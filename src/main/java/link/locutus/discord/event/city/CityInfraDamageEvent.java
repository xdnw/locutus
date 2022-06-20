package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityInfraDamageEvent extends CityChangeEvent {

    public CityInfraDamageEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
