package link.locutus.discord.util.trade;

import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.domains.subdomains.HighestbuyContainer;
import link.locutus.discord.apiv1.domains.subdomains.LowestbuyContainer;
import link.locutus.discord.apiv1.domains.subdomains.TradeContainer;
import link.locutus.discord.apiv1.enums.ResourceType;

import static link.locutus.discord.util.TimeUtil.YYYY_MM_DD_HH_MM_SS;

public class Offer implements Comparable<Offer> {
    private final Integer seller;
    private final Integer buyer;

    private boolean isBuy;

    private final ResourceType resource;
    private final int amount;
    private final int ppu;
    private final long total;
    private final int tradeId;
    private final long epochms;

    public Offer(ResourceType type, HighestbuyContainer buy) {
        this.resource = type;
        ppu = MathMan.parseInt(buy.getPrice());
        seller = MathMan.parseInt(buy.getNationid());
        buyer = null;
        amount = MathMan.parseInt(buy.getAmount());
        total = amount * ppu;
        tradeId = 0;
        epochms = TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_HH_MM_SS, buy.getDate());
    }

    public Offer(ResourceType type, LowestbuyContainer buy) {
        this.resource = type;
        ppu = MathMan.parseInt(buy.getPrice());
        seller = null;
        buyer = MathMan.parseInt(buy.getNationid());
        amount = MathMan.parseInt(buy.getAmount());
        total = amount * ppu;
        tradeId = 0;
        epochms = TimeUtil.parseDate(TimeUtil.YYYY_MM_DD_HH_MM_SS, buy.getDate());
    }

    public Offer(TradeContainer trade) {
        Integer idOffer = trade.getOffererNationId().isEmpty() ? null : Integer.parseInt(trade.getOffererNationId());
        Integer idReceive = trade.getAccepterNationId().isEmpty() ? null : Integer.parseInt(trade.getAccepterNationId());
        seller = idOffer;
        buyer = idReceive;

        if (trade.getOfferType().equalsIgnoreCase("sell")) {
            isBuy = false;
        } else if (trade.getOfferType().equalsIgnoreCase("buy")) {
            isBuy = true;
        } else {
            throw new UnsupportedOperationException("Invalid type " + trade.getOfferType());
        }

        resource = ResourceType.valueOf(trade.getResource().toUpperCase());

        amount = Integer.parseInt(trade.getQuantity());
        ppu = Integer.parseInt(trade.getPrice());
        tradeId = Integer.parseInt(trade.getTradeId());

        this.epochms = TimeUtil.parseDate(YYYY_MM_DD_HH_MM_SS, trade.getDate());
        this.total = (long) ppu * amount;
    }

    public Offer(Integer seller, Integer buyer, ResourceType resource, boolean isBuy, int amount, int ppu, int tradeId, long epochms) {
        this.seller = seller;
        this.buyer = buyer;
        this.resource = resource;
        this.isBuy = isBuy;
        this.amount = amount;
        this.ppu = ppu;
        this.total = (long) ppu * amount;
        this.tradeId = tradeId;
        this.epochms = epochms;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public Integer getSeller() {
        return seller;
    }

    public Integer getBuyer() {
        return buyer;
    }

    public ResourceType getResource() {
        return resource;
    }

    public int getAmount() {
        return amount;
    }

    public int getPpu() {
        return ppu;
    }

    public long getTotal() {
        return total;
    }

    public int getTradeId() {
        return tradeId;
    }

    public long getEpochms() {
        return epochms;
    }

    @Override
    public String toString() {
        return "Offer{" +
                "seller='" + seller + '\'' +
                ", buyer='" + buyer + '\'' +
                ", resource=" + resource +
                ", amount=" + amount +
                ", ppu=" + ppu +
                ", total=" + total +
                '}';
    }

    @Override
    public int compareTo(Offer o) {
        return Integer.compare(this.getPpu(), o.getPpu());
    }
}