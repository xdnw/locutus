package link.locutus.discord.pnw;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.Operation;

public class Spyop {
    public final int safety;
    public final DBNation attacker;
    public final DBNation defender;
    public final int spies;
    public final Operation operation;
    public final double netDamage;

    public Spyop(DBNation attacker, DBNation defender, int spies, Operation operation, double netDamage, int safety) {
        this.attacker = attacker;
        this.defender = defender;
        this.spies = spies;
        this.operation = operation;
        this.netDamage = netDamage;
        this.safety = safety;
    }
}
