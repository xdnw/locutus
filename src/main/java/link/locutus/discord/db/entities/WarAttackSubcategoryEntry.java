package link.locutus.discord.db.entities;

import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;

public class WarAttackSubcategoryEntry {
    public final int war_id;
    public final int attack_id;
    public final AttackTypeSubCategory subcategory;

    public WarAttackSubcategoryEntry(int war_id, int attack_id, AttackTypeSubCategory category) {
        this.war_id = war_id;
        this.attack_id = attack_id;
        this.subcategory = category;
    }
}
