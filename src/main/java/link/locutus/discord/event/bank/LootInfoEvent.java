package link.locutus.discord.event.bank;

import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.event.Event;

public class LootInfoEvent extends Event {
    private final LootEntry loot;

    public LootInfoEvent(LootEntry loot) {
        this.loot = loot;
        setTime(loot.getDate());
    }

    public LootEntry getLoot() {
        return loot;
    }
}
