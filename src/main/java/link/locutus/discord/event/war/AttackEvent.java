package link.locutus.discord.event.war;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.event.Event;

public class AttackEvent extends Event {
    private final AbstractCursor attack;
    private DBWar war;

    public AttackEvent(DBWar war, AbstractCursor attack) {
        super(attack.getDate());
        this.attack = attack;
        this.war = war;
    }

    public DBWar getWar() {
        if (this.war == null) {
            return this.war = attack.getWar();
        }
        return this.war;
    }

    public AbstractCursor getAttack() {
        return attack;
    }
}
