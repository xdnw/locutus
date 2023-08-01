package link.locutus.discord.db.entities;

public record AttackEntry(int id, int war_id, int attacker_id, int defender_id, long date, byte[] data) {
}
