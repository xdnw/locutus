package link.locutus.discord.util;

import java.util.Objects;

/**
 * Shared catalog of canonical {@link RateLimitedSource} instances.
 *
 * <p>Use these for non-public/internal source definitions instead of repeating one-off enums that
 * only wrap a {@link SendPolicy}/{@link DeferredPriority} pair. Public semantic source surfaces
 * can still expose their own types when that improves readability.</p>
 */
public final class RateLimitedSources {
    private record Source(SendPolicy sendPolicy, DeferredPriority deferredPriority) implements RateLimitedSource {
        private Source {
            Objects.requireNonNull(sendPolicy, "sendPolicy");
            Objects.requireNonNull(deferredPriority, "deferredPriority");
        }
    }

    private static RateLimitedSource source(SendPolicy sendPolicy, DeferredPriority deferredPriority) {
        return new Source(sendPolicy, deferredPriority);
    }

    public static final RateLimitedSource PNW_PUSHER_INVALID_KEY_ALERT = source(SendPolicy.DEFER, DeferredPriority.PNW_PUSHER_INVALID_KEY_ALERT);
    public static final RateLimitedSource REPORT_COMMAND_ALERT = source(SendPolicy.DEFER, DeferredPriority.REPORT_COMMAND_ALERT);
    public static final RateLimitedSource GUILD_DB_ADD_BALANCE_ALERT = source(SendPolicy.DEFER, DeferredPriority.GUILD_DB_ADD_BALANCE_ALERT);
    public static final RateLimitedSource GUILD_DB_DISCORD_SYNC = source(SendPolicy.DEFER, DeferredPriority.GUILD_DB_DISCORD_SYNC);
    public static final RateLimitedSource GUILD_HANDLER_MEMBER_LEAVE_ALERT = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_MEMBER_LEAVE_ALERT);
    public static final RateLimitedSource GUILD_HANDLER_NEW_APPLICANT_ALERT = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_NEW_APPLICANT_ALERT);
    public static final RateLimitedSource GUILD_HANDLER_APPLICANT_MAIL_FAILURE = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_APPLICANT_MAIL_FAILURE);
    public static final RateLimitedSource GUILD_HANDLER_MEMBER_LEAVE_EMBED = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_MEMBER_LEAVE_EMBED);
    public static final RateLimitedSource GUILD_HANDLER_WAR_ALERT_BATCH = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_WAR_ALERT_BATCH);
    public static final RateLimitedSource GUILD_HANDLER_INCENTIVE_LOG = source(SendPolicy.DEFER, DeferredPriority.GUILD_HANDLER_INCENTIVE_LOG);
    public static final RateLimitedSource GUILD_HANDLER_RECRUIT_INELIGIBLE_NOTICE = source(SendPolicy.DEFER, DeferredPriority.GUILD_HANDLER_RECRUIT_INELIGIBLE_NOTICE);
    public static final RateLimitedSource GUILD_HANDLER_RECRUIT_MESSAGE_STATUS = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_RECRUIT_MESSAGE_STATUS);
    public static final RateLimitedSource GUILD_HANDLER_RECRUIT_MESSAGE_ERROR = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_RECRUIT_MESSAGE_ERROR);
    public static final RateLimitedSource GUILD_HANDLER_DISCORD_ALERT = source(SendPolicy.CONDENSE, DeferredPriority.GUILD_HANDLER_DISCORD_ALERT);
    public static final RateLimitedSource DB_NATION_ROLE_ASSIGN = source(SendPolicy.IMMEDIATE, DeferredPriority.DB_NATION_ROLE_ASSIGN);
    public static final RateLimitedSource DB_NATION_OFFSHORE_LOG = source(SendPolicy.DEFER, DeferredPriority.DB_NATION_OFFSHORE_LOG);
    public static final RateLimitedSource DB_NATION_DIRECT_MESSAGE = source(SendPolicy.IMMEDIATE, DeferredPriority.DB_NATION_DIRECT_MESSAGE);
    public static final RateLimitedSource ANNOUNCE_INVITE_SYNC = source(SendPolicy.DEFER, DeferredPriority.ANNOUNCE_INVITE_SYNC);
    public static final RateLimitedSource MENU_MANAGER_COMMAND_SYNC = source(SendPolicy.DEFER, DeferredPriority.MENU_MANAGER_COMMAND_SYNC);
    public static final RateLimitedSource SEND_INTERNAL_TRANSFER_LOG = source(SendPolicy.CONDENSE, DeferredPriority.SEND_INTERNAL_TRANSFER_LOG);
    public static final RateLimitedSource ALERT_AUDIT = source(SendPolicy.DEFER, DeferredPriority.ALERT_AUDIT);
    public static final RateLimitedSource ALERT_DISPLAY_CHANNEL = source(SendPolicy.DEFER, DeferredPriority.ALERT_DISPLAY_CHANNEL);
    public static final RateLimitedSource ALERT_BUFFER_PING = source(SendPolicy.DEFER, DeferredPriority.ALERT_BUFFER_PING);
    public static final RateLimitedSource DISCORD_UTIL_CHANNEL_SYNC = source(SendPolicy.DEFER, DeferredPriority.DISCORD_UTIL_CHANNEL_SYNC);
    public static final RateLimitedSource DISCORD_UTIL_MESSAGE_IO = source(SendPolicy.DEFER, DeferredPriority.DISCORD_UTIL_MESSAGE_IO);
    public static final RateLimitedSource DISCORD_UTIL_EMBED_COMMAND = source(SendPolicy.DEFER, DeferredPriority.DISCORD_UTIL_EMBED_COMMAND);
    public static final RateLimitedSource AUTH_OFFSHORE_LOG = source(SendPolicy.DEFER, DeferredPriority.AUTH_OFFSHORE_LOG);
    public static final RateLimitedSource OFFSHORE_WITHDRAW_LIMIT_ALERT = source(SendPolicy.CONDENSE, DeferredPriority.OFFSHORE_WITHDRAW_LIMIT_ALERT);
    public static final RateLimitedSource OFFSHORE_CROSS_GUILD_LOG = source(SendPolicy.CONDENSE, DeferredPriority.OFFSHORE_CROSS_GUILD_LOG);
    public static final RateLimitedSource OFFSHORE_TRANSFER_LOG = source(SendPolicy.CONDENSE, DeferredPriority.OFFSHORE_TRANSFER_LOG);
    public static final RateLimitedSource OFFSHORE_ALLIANCE_TRANSFER_LOG = source(SendPolicy.CONDENSE, DeferredPriority.OFFSHORE_ALLIANCE_TRANSFER_LOG);
    public static final RateLimitedSource IA_CATEGORY_DISCORD_SYNC = source(SendPolicy.DEFER, DeferredPriority.IA_CATEGORY_DISCORD_SYNC);
    public static final RateLimitedSource MAIL_RESPOND_TASK_RETRY_STATUS = source(SendPolicy.DEFER, DeferredPriority.MAIL_RESPOND_TASK_RETRY_STATUS);
    public static final RateLimitedSource MAIL_TASK_RETRY_STATUS = source(SendPolicy.DEFER, DeferredPriority.MAIL_TASK_RETRY_STATUS);
    public static final RateLimitedSource WAR_CARD_CONDENSED_EMBED = source(SendPolicy.CONDENSE, DeferredPriority.WAR_CARD_CONDENSED_EMBED);
    public static final RateLimitedSource BANK_UPDATE_WITHDRAW_ALERT = source(SendPolicy.DEFER, DeferredPriority.BANK_UPDATE_WITHDRAW_ALERT);
    public static final RateLimitedSource NATION_UPDATE_BEIGE_ALERT_MENTIONS = source(SendPolicy.DEFER, DeferredPriority.NATION_UPDATE_BEIGE_ALERT_MENTIONS);
    public static final RateLimitedSource NATION_UPDATE_EXODUS_ALERT = source(SendPolicy.DEFER, DeferredPriority.NATION_UPDATE_EXODUS_ALERT);
    public static final RateLimitedSource TREATY_UPDATE_ALERT = source(SendPolicy.DEFER, DeferredPriority.TREATY_UPDATE_ALERT);
    public static final RateLimitedSource WAR_UPDATE_BOUNTY_MENTIONS = source(SendPolicy.DEFER, DeferredPriority.WAR_UPDATE_BOUNTY_MENTIONS);
    public static final RateLimitedSource COMMAND_MANAGER_WAR_ROOM_RELAY = source(SendPolicy.DEFER, DeferredPriority.COMMAND_MANAGER_WAR_ROOM_RELAY);

    private RateLimitedSources() {
    }
}
