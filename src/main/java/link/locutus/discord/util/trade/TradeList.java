package link.locutus.discord.util.trade;

import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBTrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TradeList {
    private final ResourceType type;
    private final List<DBTrade> offers;

    public TradeList(ResourceType type) {
        this.type = type;
        this.offers = new ArrayList<>();
    }

    public ResourceType getType() {
        return type;
    }

    public List<DBTrade> getOffers() {
        return offers;
    }

    public void clear() {
        offers.clear();
    }

    public void clearBuy() {
        offers.removeIf(offer -> offer.getBuyer() != 0);
    }

    public void clearSell() {
        offers.removeIf(offer -> offer.getSeller() != 0);
    }

    public void removeIf(Predicate<DBTrade> predicate) {
        offers.removeIf(predicate);
    }

    public void addOffer(DBTrade offer) {
        offers.add(offer);
    }

    public List<DBTrade> iterator(boolean buy) {
        return offers.stream().filter(offer -> (buy ? offer.getBuyer() : offer.getSeller()) != 0).collect(Collectors.toList());
    }

    public List<DBTrade> iterator(boolean buy, int threshold) {
        return iterator(buy).stream().filter(offer -> offer.getQuantity() >= threshold).collect(Collectors.toList());
    }

    public int getAmount(DBNation nation, int ppu, boolean isBuy) {
        for (DBTrade offer : offers) {
            if (offer.getPpu() == ppu) {
                Integer transactor = isBuy ? (offer.getBuyer()) : (offer.getSeller());
                if (transactor == null) continue;
                if (nation.getNation_id() == transactor) {
                    return offer.getQuantity();
                }
            }
        }
        return 0;
    }

    public int getDisparity(int threshold) {
        List<DBTrade> buy = iterator(true, threshold);
        List<DBTrade> sell = iterator(false, threshold);
        if (buy.isEmpty()) {
            buy = iterator(true);
        }
        if (sell.isEmpty()) {
            sell = iterator(false);
        }
        if (sell.isEmpty() || buy.isEmpty()) {
            return Integer.MIN_VALUE;
        }
        int buyPPU = Collections.max(buy).getPpu();
        int sellPPU = Collections.min(sell).getPpu();
        return sellPPU - buyPPU;
    }
}
