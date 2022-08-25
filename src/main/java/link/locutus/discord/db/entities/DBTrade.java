package link.locutus.discord.db.entities;

import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class DBTrade implements Comparable<DBTrade> {
    private int tradeId;
    private int seller;
    private int buyer;
    private int quantity;
    private int ppu;
    private int parent_id;
    private TradeType type;
    private ResourceType resource;
    private long date_accepted;
    private long date;
    private boolean isBuy;

    public DBTrade(int tradeId, long date, int seller, int buyer, ResourceType resource, boolean isBuy, int quantity, int ppu, TradeType type, long date_accepted, int parent_id) {
        this.tradeId = tradeId;
        this.date = date;
        this.seller = seller;
        this.buyer = buyer;
        this.resource = resource;
        this.isBuy = (isBuy);
        this.quantity = quantity;
        this.ppu = ppu;
        this.type = type;
        this.date_accepted = date_accepted;
        this.parent_id = parent_id;
    }

    public DBTrade(com.politicsandwar.graphql.model.Trade model) {
        this.tradeId = model.getId();
        this.date = model.getDate().toEpochMilli();
        this.seller = model.getSender_id();
        this.buyer = model.getReceiver_id();
        this.resource = ResourceType.parse(model.getOffer_resource());
        this.isBuy = (model.getBuy_or_sell().equalsIgnoreCase("buy"));
        this.quantity = model.getOffer_amount();
        this.ppu = model.getPrice();
        this.type = model.getType();
        this.date_accepted = model.getDate_accepted() != null ? model.getDate_accepted().toEpochMilli() : -1L;
        this.parent_id = model.getOriginal_trade_id() != null ? model.getOriginal_trade_id() : -1;
    }

    public DBTrade(ResultSet rs) throws SQLException {
        this.tradeId = rs.getInt("tradeId");
        this.date = rs.getLong("date");
        this.seller = rs.getInt("seller");
        this.buyer = rs.getInt("buyer");
        this.resource = ResourceType.values[rs.getInt("resource")];
        this.isBuy = (rs.getBoolean("isBuy"));
        this.quantity = rs.getInt("quantity");
        this.ppu = rs.getInt("ppu");
        this.type = TradeType.values()[rs.getInt("type")];
        this.date_accepted = rs.getLong("date_accepted");
        this.parent_id = rs.getInt("parent_id");
    }

    public int getTradeId() {
        return tradeId;
    }

    public int getSeller() {
        return seller;
    }

    public int getBuyer() {
        return buyer;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getPpu() {
        return ppu;
    }

    public int getParent_id() {
        return parent_id;
    }

    public TradeType getType() {
        return type;
    }

    public ResourceType getResource() {
        return resource;
    }

    public long getDate() {
        return date;
    }

    public long getDate_accepted() {
        return date_accepted;
    }

    public boolean isBuy() {
        return isBuy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DBTrade dbTrade = (DBTrade) o;
        return tradeId == dbTrade.tradeId && seller == dbTrade.seller && buyer == dbTrade.buyer && quantity == dbTrade.quantity && ppu == dbTrade.ppu && parent_id == dbTrade.parent_id && date_accepted == dbTrade.date_accepted && date == dbTrade.date && isBuy == dbTrade.isBuy && type == dbTrade.type && resource == dbTrade.resource;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, seller, buyer, quantity, ppu, parent_id, type, resource, date_accepted, date, isBuy);
    }

    public void setBuy(boolean buy) {
        isBuy = buy;
    }

    public void setTradeId(int tradeId) {
        this.tradeId = tradeId;
    }

    public void setSeller(int seller) {
        this.seller = seller;
    }

    public void setBuyer(int buyer) {
        this.buyer = buyer;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setPpu(int ppu) {
        this.ppu = ppu;
    }

    public void setParent_id(int parent_id) {
        this.parent_id = parent_id;
    }

    public void setType(TradeType type) {
        this.type = type;
    }

    public void setResource(ResourceType resource) {
        this.resource = resource;
    }

    public void setDate_accepted(long date_accepted) {
        this.date_accepted = date_accepted;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public boolean isActive() {
        return date_accepted == -1L;
    }

    public long getTotal() {
        return quantity * (long) ppu;
    }

    @Override
    public int compareTo(@NotNull DBTrade o) {
        return Integer.compare(ppu, o.ppu);
    }
}
