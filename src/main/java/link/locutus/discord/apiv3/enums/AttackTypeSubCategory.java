package link.locutus.discord.apiv3.enums;

import java.util.Map;

public enum AttackTypeSubCategory {
    GROUND_NO_MUNITIONS_NO_TANKS("Ground attack with no munitions and no tanks"),
    GROUND_NO_TANKS_MUNITIONS_USED_NECESSARY("Ground attack with no tanks where munitions were necessary"),
    GROUND_TANKS_MUNITIONS_USED_NECESSARY("Ground attack with tanks where munitions were necessary"),
    GROUND_TANKS_MUNITIONS_USED_UNNECESSARY("Ground attack with tanks and munitions when munitions were not needed for an immense triumph"),
    AIRSTRIKE_3_PLANE("Airstrike with 3 planes"),
    AIRSTRIKE_UNIT("Aristrike that killed units"),
    NAVY_1_SHIP("Naval attack using 1 ship"),
    NAVY_KILL_UNITS("Naval attack which killed enemy units"),
    MISSILE("Missile launch"),
    NUKE("Nuke launch"),
    FORTIFY("Fortify"),
    DOUBLE_FORTIFY("Repeated use of fortify does not stack and is a waste of MAP."),
    IMPROVEMENTS_DESTROYED("An improvement was destroyed"),

    GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR("You performed a ground attack using tanks against an enemy with a high amount of soldiers (but no tanks), no loot, and no aircraft. An airstrike may be cheaper at getting the initial soldiers down and avoiding tank losses"),
    GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR_INACTIVE("You performed an expensive ground attack using tanks against an INACTIVE enemy with a high amount of soldiers (but no tanks), no loot, and no aircraft. A 3 plane attack may be cheaper."),
    GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY("You performed a ground attack against an enemy using munitions, but could have won and looted the same amount without it. https://cdn.discordapp.com/attachments/672286352959733779/716563558279806986/unknown.png\n" +
            "Note: Infra damage is the same regardless of munitions"),
    GROUND_TANKS_USED_UNNECESSARY("You performed a ground attack using tanks against an enemy with weak ground and no aircraft, but could have won an Immense Triumph using soldiers"),
    GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY_INACTIVE("You performed a ground attack against an inactive enemy using munitions, but could have won and looted the same amount without it. https://cdn.discordapp.com/attachments/672286352959733779/716563558279806986/unknown.png"),
    GROUND_TANKS_USED_UNNECESSARY_INACTIVE("You performed a ground attack against an inactive enemy using tanks, when they had $0, but could have won an Immense Triumph without tanks."),
    AIRSTRIKE_SOLDIERS_NONE("You performed an airstrike against enemy soldiers when the enemy has none"),
    AIRSTRIKE_SOLDIERS_SHOULD_USE_GROUND("You performed an airstrike against enemy ground forces when:\n" +
            "- You already have vastly superior ground forces\n" +
            "- All other enemies they are engaged in have more ground\n" +
            "- You already have air control\n" +
            "(It may be more efficient doing a ground attack instead)"),
    AIRSTRIKE_TANKS_NONE("You performed an airstrike against enemy tanks when the enemy has none"),
    AIRSTRIKE_SHIP_NONE("You performed an airstrike against enemy ships when the enemy has none"),
    AIRSTRIKE_INACTIVE_NO_GROUND("You performed an airstrike against an inactive enemy, but could have won a ground battle using soldiers and no munitions."),
    AIRSTRIKE_INACTIVE_NO_SHIP("You performed an airstrike against an inactive enemy, but could have won a naval battle with 1 ship."),
    AIRSTRIKE_FAILED_NOT_DOGFIGHT("You performed an airstrike against an enemy with relatively strong air, but did not target aircraft. A dogfight will inflict 83% more air casualties: <https://politicsandwar.fandom.com/wiki/Airstrike#Aircraft>"),
    AIRSTRIKE_AIRCRAFT_NONE("You performed a dogfight against an enemy with no aircraft. "),
    AIRSTRIKE_AIRCRAFT_NONE_INACTIVE(AIRSTRIKE_AIRCRAFT_NONE.message + "You can get an immense triumph with just 3 aircraft"),

    AIRSTRIKE_AIRCRAFT_LOW("You performed a dogfight using {amt_att} against an enemy with only {amt_def} aircraft, when you could have targeted other military units"),

    AIRSTRIKE_INFRA("You performed an airstrike against enemy infra using {amount} aircraft. There is no need to waste resources destroying infrastructure."),

    AIRSTRIKE_MONEY("You performed an airstrike against enemy $$$ using {amount} aircraft. There is no need to waste resources destroying money." +
            "Note: It often costs you more in consumption than you destroy in money."),
    NAVAL_ALREADY_BLOCKADED("You performed a naval attack against an enemy that is **already** blockaded, and you destroyed 0 ships. For counters, the goal is to kill units.\n" +
            "Note: Airstrike air the attacker has advantage. The enemy loses half their tank strength if you have air control"),

    NAVAL_MAX_VS_NONE("You performed a naval attack against an enemy with no ships, but could have got an immense triumph with only 1 ship"),

    ;

    public static final AttackTypeSubCategory[] values = values();
    public final String message;

    AttackTypeSubCategory(String msg) {
        this.message = msg;
    }

    public Map.Entry<AttackTypeSubCategory, String> toPair() {
        return Map.entry(this, this.message);
    }
}
