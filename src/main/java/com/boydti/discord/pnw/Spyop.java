package com.boydti.discord.pnw;

import com.boydti.discord.util.SpyCount;

public class Spyop {
    public final int safety;
    public final DBNation attacker;
    public final DBNation defender;
    public final int spies;
    public final SpyCount.Operation operation;
    public final double netDamage;

    public Spyop(DBNation attacker, DBNation defender, int spies, SpyCount.Operation operation, double netDamage, int safety) {
        this.attacker = attacker;
        this.defender = defender;
        this.spies = spies;
        this.operation = operation;
        this.netDamage = netDamage;
        this.safety = safety;
    }
}
