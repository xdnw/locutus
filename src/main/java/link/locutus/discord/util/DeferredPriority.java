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
    WAR_ROOM_MANUAL_ROOM_CREATE(true),
    WAR_ROOM_INITIAL_PIN(true),

    GUILD_HANDLER_MEMBER_LEAVE_ALERT(false),
    GUILD_HANDLER_NEW_APPLICANT_ALERT(false),
    GUILD_HANDLER_APPLICANT_MAIL_FAILURE(false),
    OFFSHORE_WITHDRAW_LIMIT_ALERT(false),
    PNW_PUSHER_INVALID_KEY_ALERT(false),
    GUILD_HANDLER_WAR_ALERT_BATCH(false),
    NATION_UPDATE_BEIGE_ALERT_MENTIONS(false),
    WAR_UPDATE_BOUNTY_MENTIONS(false),
    GUILD_HANDLER_MEMBER_LEAVE_EMBED(false),
    GUILD_DB_ADD_BALANCE_ALERT(false),
    COMMAND_MANAGER_WAR_ROOM_RELAY(false),
    GUILD_HANDLER_RECRUIT_INELIGIBLE_NOTICE(false),
    MAIL_TASK_RETRY_STATUS(false),
    MAIL_RESPOND_TASK_RETRY_STATUS(false),
    ALERT_AUDIT(false),
    ALERT_DISPLAY_CHANNEL(false),
    ALERT_BUFFER_PING(false),
    WAR_ROOM_AUTO_ROOM_CREATE(false),
    WAR_ROOM_PIN_REFRESH(false),
    WAR_ROOM_ROOM_INFO(false),
    WAR_ROOM_STATUS_UPDATE(false),
    WAR_ROOM_ROOM_CLEANUP(false),

    GUILD_HANDLER_INCENTIVE_LOG(false),
    GUILD_HANDLER_RECRUIT_MESSAGE_STATUS(false),
    GUILD_HANDLER_RECRUIT_MESSAGE_ERROR(false),
    SEND_INTERNAL_TRANSFER_LOG(false),
    OFFSHORE_CROSS_GUILD_LOG(false),
    OFFSHORE_TRANSFER_LOG(false),
    OFFSHORE_ALLIANCE_TRANSFER_LOG(false),
    COMMAND_STATUS(false),
    COMMAND_PROGRESS(false),
    WAR_ROOM_ATTACK_MESSAGE(false),
    WAR_ROOM_ROOM_LOG(false);

    private final boolean reserveHeadroom;

    DeferredPriority(boolean reserveHeadroom) {
        this.reserveHeadroom = reserveHeadroom;
    }

    public boolean reserveHeadroom() {
        return reserveHeadroom;
    }
}

