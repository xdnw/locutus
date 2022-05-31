package com.boydti.discord.db.entities;

import com.boydti.discord.Locutus;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public enum DiscordMeta {
    TURN(false),

    @Deprecated // guild db has recruitment options instead
    RECRUIT(true),

    @Deprecated // guild db has api key options instead
    API_KEY(true),

    RADIATION_CONTINENT(true),

    RESOURCE_VOLUME_TYPE(true),

    BEIGE_ALERT_TASK_TURN(false),

    OPT_OUT(true),

    ;

    public static DiscordMeta[] values = DiscordMeta.values();
    private final boolean hasID;
    private final long deleteBefore;

    DiscordMeta(boolean hasID, Supplier<Long> deleteBefore) {
        this.hasID = hasID;
        this.deleteBefore = deleteBefore.get();
    }

    DiscordMeta(boolean hasID) {
        this.hasID = hasID;
        this.deleteBefore = 0L;
    }

    public boolean isHasID() {
        return hasID;
    }

    public long getDeleteBefore() {
        return deleteBefore;
    }

    public ByteBuffer get(long id) {
        return Locutus.imp().getDiscordDB().getInfo(this, id);
    }
}
