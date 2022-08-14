package link.locutus.discord.event.war;

import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.event.Event;

public class AttackEvent extends Event {
    private final DBAttack attack;

    public AttackEvent(DBAttack attack) {
        super(attack.epoch);
        this.attack = attack;
    }

    public DBAttack getAttack() {
        return attack;
    }
}
