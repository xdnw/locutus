package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AttackCursorFactory;

public record AttackEntry(int id, int war_id, int attacker_id, int defender_id, long date, byte[] data) {
    public static AttackEntry of(AbstractCursor attack, AttackCursorFactory factory) {
        return new AttackEntry(attack.getWar_attack_id(), attack.getWar_id(), attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), factory.toBytes(attack));
    }
}
