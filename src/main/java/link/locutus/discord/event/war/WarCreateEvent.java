package link.locutus.discord.event.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBWar;

public class WarCreateEvent extends WarUpdateEvent {
    public WarCreateEvent(DBWar war) {
        super(null, war);
    }

    @Override
    protected void postToGuilds() {
        post(Locutus.imp().getGuildDBByAA(getCurrent().getAttacker_aa()));
        post(Locutus.imp().getGuildDBByAA(getCurrent().getDefender_aa()));

        if (getCurrent().getDefender_aa() != 0) new DefensiveWarEvent(getCurrent()).postToGuilds();
        if (getCurrent().getAttacker_aa() != 0) new OffensiveWarEvent(getCurrent()).postToGuilds();
    }
}
