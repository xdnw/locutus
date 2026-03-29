package link.locutus.discord.util;

/**
 * Ordering for deferred or condensed work once we decide not to submit immediately.
 *
 * <p>Each enum entry represents one concrete source, not a generic bucket. Earlier entries are
 * drained first. Sources marked with {@code reserveHeadroom=true} are also allowed to consume the
 * wider submission threshold so interactive work can still make progress under load.</p>
 */
public enum DeferredPriority {
    COMMAND_RESULT(true),

    MENU_MANAGER_COMMAND_SYNC(false),

    WAR_ROOM_MANUAL_ROOM_CREATE(true),
    WAR_ROOM_ROOM_INFO(false),

    IA_CATEGORY_DISCORD_SYNC(false),
    INVITE_SYNC(false),

    GUILD_HANDLER_NEW_APPLICANT_ALERT(false),

    WAR_ROOM_INITIAL_PIN(true), // TODO: this needs different priority for auto created rooms

    DB_NATION_DIRECT_MESSAGE(true),

    NATION_UPDATE_BEIGE_ALERT_MENTIONS(false),
    WAR_UPDATE_BOUNTY_MENTIONS(false),

    GUILD_HANDLER_DISCORD_ALERT(false), // TODO: This needs to be deleted and replaced with actual sources
    DISCORD_UTIL_EMBED_COMMAND(false), // TODO: This needs to be deleted and replaced with actual sources

    COMMAND_MANAGER_WAR_ROOM_RELAY(false),


    GUILD_HANDLER_WAR_ALERT_BATCH(false),
    WAR_CARD_CONDENSED_EMBED(false), // TODO: This needs to be deleted and replaced with actual sources

    GUILD_HANDLER_MEMBER_LEAVE_ALERT(false),
    GUILD_HANDLER_APPLICANT_MAIL_FAILURE(false),

    OFFSHORE_WITHDRAW_LIMIT_ALERT(false),
    BANK_UPDATE_WITHDRAW_ALERT(false),

    PNW_PUSHER_INVALID_KEY_ALERT(false),

    NATION_UPDATE_EXODUS_ALERT(false),

    GUILD_HANDLER_MEMBER_LEAVE_EMBED(false),

    GUILD_DB_DISCORD_SYNC(false), // TODO: This needs to be deleted and replaced with actual sources
    DB_NATION_ROLE_ASSIGN(false),

    DISCORD_UTIL_CHANNEL_SYNC(false), // TODO: This needs to be deleted and replaced with actual sources
    DISCORD_UTIL_MESSAGE_IO(false), // TODO: This needs to be deleted and replaced with actual sources

    MAIL_TASK_RETRY_STATUS(false),
    MAIL_RESPOND_TASK_RETRY_STATUS(false),

    ALERT_AUDIT(false), // TODO: This needs to be deleted and replaced with actual sources
    ALERT_DISPLAY_CHANNEL(false), // TODO: This needs to be deleted and replaced with actual sources
    ALERT_BUFFER_PING(false), // TODO: This needs to be deleted and replaced with actual sources

    GUILD_DB_ADD_BALANCE_ALERT(false),

    NEW_USER_MAIN(false),

    WAR_ROOM_AUTO_ROOM_CREATE(false), // TODO: this needs different priority for auto created rooms, despite it being named `AUTO` it is used by both manual and auto room creation
    WAR_ROOM_PIN_REFRESH(false), // TODO: this needs different priority for auto created rooms
    WAR_ROOM_STATUS_UPDATE(false), // TODO: this should NOT be mixing critical room creation priorities with symbol updates
    WAR_ROOM_ROOM_CLEANUP(false),

    TREATY_UPDATE_ALERT(false),

    GUILD_HANDLER_RECRUIT_MESSAGE_STATUS(false),
    GUILD_HANDLER_RECRUIT_MESSAGE_ERROR(false),
    REPORT_COMMAND_ALERT(false),

    WAR_ROOM_ATTACK_MESSAGE(false),

    GUILD_HANDLER_RECRUIT_INELIGIBLE_NOTICE(false),
    GUILD_HANDLER_INCENTIVE_LOG(false),
    SEND_INTERNAL_TRANSFER_LOG(false),
    DB_NATION_OFFSHORE_LOG(false),
    AUTH_OFFSHORE_LOG(false),
    OFFSHORE_CROSS_GUILD_LOG(false),
    OFFSHORE_TRANSFER_LOG(false),
    OFFSHORE_ALLIANCE_TRANSFER_LOG(false),

    WAR_ROOM_ROOM_LOG(false),

    COMMAND_PROGRESS(false),
    ;

    private final boolean reserveHeadroom;

    DeferredPriority(boolean reserveHeadroom) {
        this.reserveHeadroom = reserveHeadroom;
    }

    public boolean reserveHeadroom() {
        return reserveHeadroom;
    }
}

