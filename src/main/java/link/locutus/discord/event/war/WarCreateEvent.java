package link.locutus.discord.event.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;

public class WarCreateEvent extends WarUpdateEvent {
    public WarCreateEvent(DBWar war) {
        super(null, war);
    }

    @Override
    protected void postToGuilds() {
        post(Locutus.imp().getGuildDBByAA(getCurrent().attacker_aa));
        post(Locutus.imp().getGuildDBByAA(getCurrent().defender_aa));

        if (getCurrent().defender_aa != 0) new DefensiveWarEvent(getCurrent()).postToGuilds();
        if (getCurrent().attacker_aa != 0) new OffensiveWarEvent(getCurrent()).postToGuilds();
    }
}
