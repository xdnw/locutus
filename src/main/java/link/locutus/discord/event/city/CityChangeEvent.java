package link.locutus.discord.event.city;

import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;

public class CityChangeEvent extends Event {
    private final DBCity previous;
    private final DBCity current;
    private final int nation;

    public CityChangeEvent(int nation, DBCity previous, DBCity current) {
        this.nation = nation;
        this.previous = previous;
        this.current = current;
    }

    public int getNationId() {
        return nation;
    }

    public DBNation getNation() {
        return DBNation.byId(nation);
    }

    public DBCity getPrevious() {
        return previous;
    }

    public DBCity getCurrent() {
        return current;
    }
}
