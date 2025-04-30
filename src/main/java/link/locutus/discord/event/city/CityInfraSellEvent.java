package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityInfraSellEvent extends CityChangeEvent {

    public CityInfraSellEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
