package link.locutus.discord.commands.war;

import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.RateLimitedSources;

import java.util.EnumSet;
import java.util.Set;

public final class WarRoomRateLimit {
    public static final RateLimitedSource MANUAL_ROOM_CREATE = RateLimitedSources.WAR_ROOM_MANUAL_ROOM_CREATE;
    public static final RateLimitedSource AUTO_ROOM_CREATE = RateLimitedSources.WAR_ROOM_AUTO_ROOM_CREATE;
    public static final RateLimitedSource INITIAL_PIN = RateLimitedSources.WAR_ROOM_INITIAL_PIN;
    public static final RateLimitedSource PIN_REFRESH = RateLimitedSources.WAR_ROOM_PIN_REFRESH;
    public static final RateLimitedSource ATTACK_MESSAGE = RateLimitedSources.WAR_ROOM_ATTACK_MESSAGE;
    public static final RateLimitedSource STATUS_UPDATE = RateLimitedSources.WAR_ROOM_STATUS_UPDATE;
    public static final RateLimitedSource ROOM_CLEANUP = RateLimitedSources.WAR_ROOM_ROOM_CLEANUP;
    public static final RateLimitedSource ROOM_LOG = RateLimitedSources.WAR_ROOM_ROOM_LOG;
    public static final RateLimitedSource ROOM_INFO = RateLimitedSources.WAR_ROOM_ROOM_INFO;

    private static final Set<WarCatReason> MANUAL_REASONS = EnumSet.of(
            WarCatReason.WARCAT_COMMAND,
            WarCatReason.WARPIN_COMMAND,
            WarCatReason.WAR_PAGE,
            WarCatReason.COMMAND_ARGUMENT,
            WarCatReason.SYNC_COMMAND,
            WarCatReason.COUNTER_SHEET,
            WarCatReason.WARCAT_SHEET,
            WarCatReason.WARROOM_COMMAND,
            WarCatReason.PURGE_COMMAND
    );

    public static RateLimitedSource forRoomCreation(WarCatReason reason) {
        return MANUAL_REASONS.contains(reason) ? MANUAL_ROOM_CREATE : AUTO_ROOM_CREATE;
    }

    public static RateLimitedSource forPinUpdate(boolean update) {
        return update ? PIN_REFRESH : INITIAL_PIN;
    }

    private WarRoomRateLimit() {
    }
}

