package link.locutus.discord.commands.war;

import link.locutus.discord.util.DeferredPriority;
import link.locutus.discord.util.RateLimitedSource;
import link.locutus.discord.util.SendPolicy;

import java.util.EnumSet;
import java.util.Set;

public enum WarRoomRateLimit implements RateLimitedSource {
    MANUAL_ROOM_CREATE(SendPolicy.DEFER, DeferredPriority.HIGH),
    AUTO_ROOM_CREATE(SendPolicy.DEFER, DeferredPriority.NORMAL),
    INITIAL_PIN(SendPolicy.DEFER, DeferredPriority.HIGH),
    PIN_REFRESH(SendPolicy.DEFER, DeferredPriority.NORMAL),
    ATTACK_MESSAGE(SendPolicy.CONDENSE, DeferredPriority.LOW),
    STATUS_UPDATE(SendPolicy.DEFER, DeferredPriority.LOW),
    ROOM_LOG(SendPolicy.CONDENSE, DeferredPriority.LOW),
    ROOM_INFO(SendPolicy.DEFER, DeferredPriority.NORMAL);

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

