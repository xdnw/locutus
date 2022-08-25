package link.locutus.discord.event.trade;

import link.locutus.discord.apiv1.enums.ResourceType;

public class TradeNoSellEvent {
    private final ResourceType type;

    public TradeNoSellEvent(ResourceType type) {
        this.type = type;
    }
}
