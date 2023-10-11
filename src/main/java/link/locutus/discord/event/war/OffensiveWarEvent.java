package link.locutus.discord.event.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBWar;

public class OffensiveWarEvent extends WarCreateEvent {
    public OffensiveWarEvent(DBWar war) {
        super(war);
    }

    @Override
    protected void postToGuilds() {
        post(Locutus.imp().getGuildDBByAA(getCurrent().getAttacker_aa()));
    }
}
