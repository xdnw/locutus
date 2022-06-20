package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

public class CityLandBuyEvent extends CityChangeEvent {
    public CityLandBuyEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
