package link.locutus.discord.commands.war;

import link.locutus.discord.util.DeferredPriority;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.SendPolicy;

import java.util.EnumSet;
import java.util.Set;

public enum WarRoomRateLimit implements RateLimitedSource {
    MANUAL_ROOM_CREATE(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_MANUAL_ROOM_CREATE),
    AUTO_ROOM_CREATE(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_AUTO_ROOM_CREATE),
    INITIAL_PIN(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_INITIAL_PIN),
    PIN_REFRESH(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_PIN_REFRESH),
    ATTACK_MESSAGE(SendPolicy.CONDENSE, DeferredPriority.WAR_ROOM_ATTACK_MESSAGE),
    STATUS_UPDATE(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_STATUS_UPDATE),
    ROOM_CLEANUP(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_ROOM_CLEANUP),
    ROOM_LOG(SendPolicy.CONDENSE, DeferredPriority.WAR_ROOM_ROOM_LOG),
    ROOM_INFO(SendPolicy.DEFER, DeferredPriority.WAR_ROOM_ROOM_INFO);

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

    private final SendPolicy sendPolicy;
    private final DeferredPriority deferredPriority;

    WarRoomRateLimit(SendPolicy sendPolicy, DeferredPriority deferredPriority) {
        this.sendPolicy = sendPolicy;
        this.deferredPriority = deferredPriority;
    }

    public static WarRoomRateLimit forRoomCreation(WarCatReason reason) {
        return MANUAL_REASONS.contains(reason) ? MANUAL_ROOM_CREATE : AUTO_ROOM_CREATE;
    }

    public static WarRoomRateLimit forPinUpdate(boolean update) {
        return update ? PIN_REFRESH : INITIAL_PIN;
    }

    @Override
    public SendPolicy sendPolicy() {
        return sendPolicy;
    }

    @Override
    public DeferredPriority deferredPriority() {
        return deferredPriority;
    }
}

