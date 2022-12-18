package link.locutus.discord.apiv3.enums;

public enum AlliancePermission {
    VIEW_BANK,
    WITHDRAW_BANK,
    CHANGE_PERMISSIONS,
    SEE_SPIES,
    SEE_RESET_TIMERS,
    TAX_BRACKETS,
    POST_ANNOUNCEMENTS,
    MANAGE_ANNOUNCEMENTS,
    ACCEPT_APPLICANTS,
    REMOVE_MEMBERS,
    EDIT_ALLIANCE_INFO,
    MANAGE_TREATIES,
    MANAGE_MARKET_SHARE,
    MANAGE_EMBARGOES,
    PROMOTE_SELF_TO_LEADER
    ;

    public boolean has(long permission) {
        return (permission & (1 << this.ordinal())) > 0;
    }
}
