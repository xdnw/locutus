package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

public class CityInfraBuyEvent extends CityChangeEvent {
    public CityInfraBuyEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
    }
}
