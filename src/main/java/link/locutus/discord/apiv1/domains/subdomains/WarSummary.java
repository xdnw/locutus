package link.locutus.discord.apiv1.domains.subdomains;

import link.locutus.discord.db.entities.DBWar;

import java.util.List;

public class WarSummary {
    byte[] data;

    public WarSummary(DBWar war, List<DBAttack> attacks) {
        long date = war.date;

        int attacker_id = war.attacker_id;
        int defender_id = war.defender_id;

        int victor;
        int war_id;

        byte[] attacker_attack_subcategories; // list of WarUpdateProcessor.AttackTypeSubCategory ordinals
        byte[] defender_attack_subcategories; // list of WarUpdateProcessor.AttackTypeSubCategory ordinals

        byte[] attackerAttacks; // list of AttackType ordinal
        byte[] defenderAttacks; // list of AttackType ordinal
    }
}
