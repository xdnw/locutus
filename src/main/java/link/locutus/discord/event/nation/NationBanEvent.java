package link.locutus.discord.event.nation;

import link.locutus.discord.db.entities.DBBan;
import link.locutus.discord.event.Event;

public class NationBanEvent  extends Event {

    private final DBBan ban;

    public NationBanEvent(DBBan ban) {
        super(ban.date);
        this.ban = ban;
    }

    public DBBan getBan() {
        return ban;
    }
}
