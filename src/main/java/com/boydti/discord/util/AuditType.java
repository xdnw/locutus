package com.boydti.discord.util;

import com.boydti.discord.apiv1.enums.WarType;

import java.util.Map;

public enum AuditType {
    GRAY("Please go to <https://politicsandwar.com/nation/edit/> and click save (so that you receive color trade bloc revenue)"),
    HIGH_INFRA("As a low tier nation (<c5), the most profitable way to make money is to raid. " +
            "Building past 1700 infra while raiding is generally not cost effective as infra at that level is more expensive to replace when lost from war." +
            "It is only recommended to replace lost infra when you lose power or multiple military buildings, or do not have enough to sustain a military."),
    RAIDING_W_TANKS("It is not recommended to raid with tanks as they are expensive and unable to loot money with any efficiency."),
    UNPROFITABLE_FARMS("Your city has farms, which are not very profitable at your level. It is more profitable buying food from the trade page"),
    WIND_POWER("Wind power take up 1 slot per 250 infra, compared to nuclear which can power 2000 infra"),
    GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR("You performed a ground attack using tanks against an enemy with a high amount of soldiers (but no tanks), no loot, and no aircraft. An airstrike may be cheaper at getting the initial soldiers down and avoiding tank losses"),
    GROUND_TANKS_NO_LOOT_NO_ENEMY_AIR_INACTIVE("You performed an expensive ground attack using tanks against an INACTIVE enemy with a high amount of soldiers (but no tanks), no loot, and no aircraft. A 3 plane attack may be cheaper."),
    UNEVEN_INFRA("You bought uneven infra in <{city}> ({amount} infra) but only get a building slot every `50` infra.\n" +
            "You can enter e.g. `@{suggested_amount}` to buy up to that amount"),

    GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY("You performed a ground attack against an enemy using munitions, but could have won and looted the same amount without it. https://cdn.discordapp.com/attachments/672286352959733779/716563558279806986/unknown.png\n" +
            "Note: Infra damage is the same regardless of munitions"),
    GROUND_TANKS_USED_UNNECESSARY("You performed a ground attack using tanks against an enemy with weak ground and no aircraft, but could have won an Immense Triumph using soldiers"),
    GROUND_NO_TANKS_MUNITIONS_USED_UNNECESSARY_INACTIVE("You performed a ground attack against an inactive enemy using munitions, but could have won and looted the same amount without it. https://cdn.discordapp.com/attachments/672286352959733779/716563558279806986/unknown.png"),
    GROUND_TANKS_USED_UNNECESSARY_INACTIVE("You performed a ground attack against an inactive enemy using tanks, but could have won an Immense Triumph without tanks."),
    AIRSTRIKE_SOLDIERS_NONE("You performed an airstrike against enemy soldiers when the enemy has none"),
    AIRSTRIKE_SOLDIERS_SHOULD_USE_GROUND("You performed an airstrike against enemy ground forces when:\n" +
            " - You already have vastly superior ground forces\n" +
            " - All other enemies they are engaged in have more ground\n" +
            " - You already have air control\n" +
            "(It may be more efficient doing a ground attack instead)"),
    AIRSTRIKE_TANKS_NONE("You performed an airstrike against enemy tanks when the enemy has none"),
    AIRSTRIKE_SHIP_NONE("You performed an airstrike against enemy ships when the enemy has none"),
    AIRSTRIKE_INACTIVE_NO_GROUND("You performed an airstrike against an inactive enemy, but could have won a ground battle using soldiers and no munitions."),
    AIRTRIKE_INACTIVE_NO_SHIP("You performed an airstrike against an inactive enemy, but could have won a naval battle with 1 ship."),
    AIRSTRIKE_FAILED_NOT_DOGFIGHT("You performed an airstrike against an enemy with relatively strong air, but did not target aircraft. A dogfight will inflict 83% more air casualties: <https://politicsandwar.fandom.com/wiki/Airstrike#Aircraft>"),
    AIRSTRIKE_AIRCRAFT_NONE("You performed a dogfight against an enemy with no aircraft. "),
    AIRSTRIKE_AIRCRAFT_NONE_INACTIVE(AIRSTRIKE_AIRCRAFT_NONE.message + "You can get an immense triumph with just 3 aircraft"),

    AIRSTRIKE_AIRCRAFT_LOW("You performed a dogfight using {amt_att} against an enemy with only {amt_def} aircraft, when you could have targeted other military units"),

    AIRSTRIKE_INFRA("You performed an airstrike against enemy infra using {amount} aircraft. There is no need to waste resources destroying infrastructure."),

    AIRSTRIKE_CASH("You performed an airstrike against enemy $$$ using {amount} aircraft. There is no need to waste resources destroying money." +
            "Note: It often costs you more in consumption than you destroy in money."),
    NAVAL_ALREADY_BLOCKADED("You performed a naval attack against an enemy that is **already** blockaded, and you destroyed 0 ships. For counters, the goal is to kill units.\n" +
            "Note: Airstrike air the attacker has advantage. The enemy loses half their tank strength if you have air control"),

    NAVAL_MAX_VS_NONE("You performed a naval attack against an enemy with no ships, but could have got an immense triumph with only 1 ship"),


    WAR_TYPE("<{war}> is declared as a {type} but should be " + WarType.RAID),

    WAR_POLICY("<{war}> is declared against an inactive enemy. You can go to <https://politicsandwar.com/nation/edit/> and switch your `WAR POLICY` to `PIRATE` to get 40% more loot")
    ;


    public final String message;

    AuditType(String msg) {
        this.message = msg;
    }

    public Map.Entry<AuditType, String> toPair() {
        return Map.entry(this, this.message);
    }
}
