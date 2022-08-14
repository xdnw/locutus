package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.apiv1.enums.ResourceType;

public class Trade {
  public final int tradeId, seller, buyer, quantity, ppu, parent_id;
  public final TradeType type;
  public final ResourceType resource;
  public final long date, date_accepted;
  public boolean isBuy;

  // constructor
    public Trade(int tradeId, long date, int seller, int buyer, ResourceType resource, boolean isBuy, int quantity, int ppu, TradeType type, long date_accepted, int parent_id) {
        this.tradeId = tradeId;
        this.date = date;
        this.seller = seller;
        this.buyer = buyer;
        this.resource = resource;
        this.isBuy = isBuy;
        this.quantity = quantity;
        this.ppu = ppu;
        this.type = type;
        this.date_accepted = date_accepted;
        this.parent_id = parent_id;
    }

    public Trade(com.politicsandwar.graphql.model.Trade model) {
        this.tradeId = model.getId();
        this.date = model.getDate().toEpochMilli();
        this.seller = model.getSender_id();
        this.buyer = model.getReceiver_id();
        this.resource = ResourceType.parse(model.getOffer_resource());
        this.isBuy = model.getBuy_or_sell().equalsIgnoreCase("buy");
        this.quantity = model.getOffer_amount();
        this.ppu = model.getPrice();
        this.type = model.getType();
        this.date_accepted = model.getDate_accepted() != null ? model.getDate_accepted().toEpochMilli() : -1L;
        this.parent_id = model.getOriginal_trade_id() != null ? model.getOriginal_trade_id() : -1;
    }
    
}
