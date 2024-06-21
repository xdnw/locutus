package link.locutus.discord.apiv3.enums;

public enum ApiKeyPermission {
    NATION_VIEW_RESOURCES,
    NATION_DEPOSIT_TO_BANK,
    NATION_MILITARY_BUYS,
    NATION_SEE_RESET_TIMERS,
    NATION_SEE_SPIES,
    NATION_VIEW_TRADES,
    NATION_ACCEPT_TRADE,
    NATION_SEND_MESSAGE,  // To be used in the `/api/send-message` endpoint
    ALLIANCE_VIEW_BANK,
    ALLIANCE_WITHDRAW_BANK,
    ALLIANCE_CHANGE_PERMISSIONS,
    ALLIANCE_SEE_SPIES,
    ALLIANCE_SEE_RESET_TIMERS,
    ALLIANCE_TAX_BRACKETS,
    ALLIANCE_ACCEPT_APPLICANTS,
    ALLIANCE_REMOVE_MEMBERS,
    ALLIANCE_MANAGE_TREATIES,
    ALLIANCE_PROMOTE_SELF_TO_LEADER;

    public boolean has(int permission) {
        return (permission & (1 << this.ordinal())) > 0;
    }
}
