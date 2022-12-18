package link.locutus.discord.event.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.guild.GuildScopeEvent;

public class WarUpdateEvent extends GuildScopeEvent {
    private final DBWar previous;
    private final DBWar current;

    public WarUpdateEvent(DBWar previous, DBWar current) {
        super(previous == null ? current.date : System.currentTimeMillis());
        this.previous = previous;
        this.current = current;
    }

    public DBWar getPrevious() {
        return previous;
    }

    public DBWar getCurrent() {
        return current;
    }

    public DBNation getAttacker() {
        return getCurrent().getNation(true);
    }

    public DBNation getDefender() {
        return getCurrent().getNation(false);
    }

    @Override
    protected void postToGuilds() {
        DBNation attacker = getAttacker();
        DBNation defender = getDefender();

        int attAA = attacker == null ? current.attacker_aa : attacker.getAlliance_id();
        int defAA = defender == null ? current.defender_aa : defender.getAlliance_id();

        post(Locutus.imp().getGuildDBByAA(attAA));
        post(Locutus.imp().getGuildDBByAA(defAA));
    }
}
