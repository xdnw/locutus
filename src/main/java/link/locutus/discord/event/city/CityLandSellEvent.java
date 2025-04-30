package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;

public class CityLandSellEvent extends CityChangeEvent {
    public CityLandSellEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
