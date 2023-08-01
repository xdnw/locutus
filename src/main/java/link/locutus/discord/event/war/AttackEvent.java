package link.locutus.discord.event.war;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.event.Event;

public class AttackEvent extends Event {
    private final AbstractCursor attack;

    public AttackEvent(AbstractCursor attack) {
        super(attack.getDate());
        this.attack = attack;
    }

    public AbstractCursor getAttack() {
        return attack;
    }
}
