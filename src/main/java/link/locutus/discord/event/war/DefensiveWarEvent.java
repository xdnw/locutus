package link.locutus.discord.event.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBWar;

public class DefensiveWarEvent extends WarCreateEvent {
    public DefensiveWarEvent(DBWar war) {
        super(war);
    }

    @Override
    protected void postToGuilds() {
        post(Locutus.imp().getGuildDBByAA(getCurrent().getDefender_aa()));
    }
}
