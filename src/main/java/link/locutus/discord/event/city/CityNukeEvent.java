package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.TimeUtil;

public class CityNukeEvent extends CityChangeEvent {
    public CityNukeEvent(int nation, DBCity previous, DBCity current) {
        super(nation, previous, current);
        setTime(TimeUtil.getTimeFromTurn(current.getNuke_turn()));

    }
}
