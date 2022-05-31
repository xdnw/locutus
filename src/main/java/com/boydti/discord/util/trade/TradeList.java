package com.boydti.discord.util.trade;

import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.apiv1.enums.ResourceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TradeList {
    private final ResourceType type;
    private final List<Offer> offers;

    public TradeList(ResourceType type) {
        this.type = type;
        this.offers = new ArrayList<>();
    }

    public ResourceType getType() {
        return type;
    }

    public List<Offer> getOffers() {
        return offers;
    }

    public void clear() {
        offers.clear();
    }

    public void clearBuy() {
        offers.removeIf(offer -> offer.getBuyer() != null);
    }

    public void clearSell() {
        offers.removeIf(offer -> offer.getSeller() != null);
    }

    public void removeIf(Predicate<Offer> predicate) {
        offers.removeIf(predicate);
    }

    public void addOffer(Offer offer) {
        offers.add(offer);
    }

    public List<Offer> iterator(boolean buy) {
        return offers.stream().filter(offer -> (buy ? offer.getBuyer() : offer.getSeller()) != null).collect(Collectors.toList());
    }

    public List<Offer> iterator(boolean buy, int threshold) {
        return iterator(buy).stream().filter(offer -> offer.getAmount() >= threshold).collect(Collectors.toList());
    }

    public int getAmount(DBNation nation, int ppu, boolean isBuy) {
        for (Offer offer : offers) {
            if (offer.getPpu() == ppu) {
                Integer transactor = isBuy ? (offer.getBuyer()) : (offer.getSeller());
                if (transactor == null) continue;
                if (nation.getNation_id() == transactor) {
                    return offer.getAmount();
                }
            }
        }
        return 0;
    }

    public int getDisparity(int threshold) {
        List<Offer> buy = iterator(true, threshold);
        List<Offer> sell = iterator(false, threshold);
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
