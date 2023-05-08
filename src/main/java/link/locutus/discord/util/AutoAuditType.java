package link.locutus.discord.util;

import link.locutus.discord.apiv1.enums.WarType;

import java.util.Map;

public enum AutoAuditType {
    GRAY("Please go to <https://politicsandwar.com/nation/edit/> and click save (so that you receive color trade bloc revenue)"),
    HIGH_INFRA("As a low tier nation (<c5), the most profitable way to make money is to raid. " +
            "Building past 1700 infra while raiding is generally not cost effective as infra at that level is more expensive to replace when lost from war." +
            "It is only recommended to replace lost infra when you lose power or multiple military buildings, or do not have enough to sustain a military."),
    RAIDING_W_TANKS("It is not recommended to raid with tanks as they are expensive and unable to loot money with any efficiency."),
    UNPROFITABLE_FARMS("Your city has farms, which are not very profitable at your level. It is more profitable buying food from the trade page"),

    UNEVEN_INFRA("You bought uneven infra in <{city}> ({amount} infra) but only get a building slot every `50` infra.\n" +
            "You can enter e.g. `@{suggested_amount}` to buy up to that amount"),
    WIND_POWER("Wind power take up 1 slot per 250 infra, compared to nuclear which can power 2000 infra"),

    WAR_TYPE_NOT_RAID("<{war}> is declared as a {type} but should be " + WarType.RAID),

    WAR_POLICY("<{war}> is declared against an inactive enemy. You can go to <https://politicsandwar.com/nation/edit/> and switch your `WAR POLICY` to `PIRATE` to get 40% more loot"),

    INACTIVE("Please remember to login every day to deter raiders and collect the login bonus"),

        ;


    public final String message;

    AutoAuditType(String msg) {
        this.message = msg;
    }

    public Map.Entry<AutoAuditType, String> toPair() {
        return Map.entry(this, this.message);
    }
}
