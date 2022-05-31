package com.boydti.discord.event;

import com.boydti.discord.apiv1.domains.subdomains.DBAttack;

public class AttackEvent {
    private final DBAttack attack;

    public AttackEvent(DBAttack attack) {
        this.attack = attack;
    }

    public DBAttack getAttack() {
        return attack;
    }
}
