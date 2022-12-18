package link.locutus.discord.event.trade;

import link.locutus.discord.apiv1.enums.ResourceType;

public class TradeNoBuyEvent {
    private final ResourceType type;

    public TradeNoBuyEvent(ResourceType type) {
        this.type = type;
    }
}
