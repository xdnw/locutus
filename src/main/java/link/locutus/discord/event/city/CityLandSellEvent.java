package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

public class CityLandSellEvent extends CityChangeEvent {
    public CityLandSellEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
