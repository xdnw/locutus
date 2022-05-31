package com.boydti.discord.util.trade;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.TradeDB;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.apiv1.enums.ResourceType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

public class OptimalTradeTask implements Callable<Long> {
    private final int days;
    private final double investment;
    private final Map<ResourceType, Map<Integer, AtomicInteger>> sold, bought;
    private final Map<ResourceType, Integer> maxPrice, minPrice;
    private final SpreadSheet sheet;

    public OptimalTradeTask(double investment, int days) throws GeneralSecurityException, IOException {
        this.investment = investment;
        this.days = days;

        sold = new EnumMap<>(ResourceType.class);
        bought = new EnumMap<>(ResourceType.class);
        this.maxPrice = new EnumMap<>(ResourceType.class);
        this.minPrice = new EnumMap<>(ResourceType.class);

        this.sheet = null; // TODO fix
//        this.sheet = SpreadSheet.create(Settings.INSTANCE.Drive.OPTIMAL_TRADE_OUTPUT);
    }

    @Override
    public Long call() throws Exception {
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
        for (Offer offer : db.getOffers(cutoffMs)) {
            int ppu = offer.getPpu();
            if (offer.getResource() != ResourceType.CREDITS) {
                if (offer.getAmount() <= 5) continue;
                if (offer.getResource() != ResourceType.FOOD) {
                    if (offer.getPpu() < 1000 || offer.getPpu() > 5000) continue;
                } else {
                    if (offer.getPpu() < 50 || offer.getPpu() > 150) continue;
                }
                if (ppu <= 1 || ppu >= 10000) continue;
            } else {
                if (ppu < 15000000 || ppu >= 30000000) continue;
                ppu /= 10000;
            }

            Map<ResourceType, Map<Integer, AtomicInteger>> map = offer.isBuy() ? sold : bought;
            Map<Integer, AtomicInteger> rssMap = map.get(offer.getResource());
            if (rssMap == null) {
                rssMap = new HashMap<>();
                map.put(offer.getResource(), rssMap);
            }
            AtomicInteger cumulative = rssMap.get(ppu);
            if (cumulative == null) {
                cumulative = new AtomicInteger();
                rssMap.put(ppu, cumulative);
            }
            cumulative.addAndGet(offer.getAmount());
        }
        AtomicInteger minPpu = new AtomicInteger();
        AtomicInteger maxPpu = new AtomicInteger();

        Queue<Trade> optimalTrades = new PriorityQueue<>();

        for (Map.Entry<ResourceType, Map<Integer, AtomicInteger>> entry : bought.entrySet()) {
            ResourceType type = entry.getKey();
            Map<Integer, AtomicInteger> rssBought = entry.getValue();
            while (rssBought.size() > 1) {
                getMinMax(rssBought, minPpu, maxPpu);

                AtomicInteger minAmt = rssBought.get(minPpu.get());
                AtomicInteger maxAmt = rssBought.get(maxPpu.get());

                int quantity = Math.min(minAmt.intValue(), maxAmt.intValue());
                minAmt.set(minAmt.get() - quantity);
                maxAmt.set(maxAmt.get() - quantity);

                if (minAmt.get() == 0) rssBought.remove(minPpu.get());
                if (maxAmt.get() == 0) rssBought.remove(maxPpu.get());

                // Amt, ppu buy, ppu sell, avg
                long requiredCapitol = (long) quantity * minPpu.get();
                long profit = (maxPpu.get() - minPpu.get()) * quantity;
                double capitolPerProfit = requiredCapitol / (double) profit;

                Trade trade = new Trade(type, quantity, minPpu.get(), maxPpu.get(), capitolPerProfit);
                optimalTrades.add(trade);
            }
        }

        sheet.reset();
        sheet.setHeader("Resource", "Amount", "Buy (ppu)", "Sell (ppu)");

        long profit = 0;

        double investmentLeft = investment;

        for (Trade trade : optimalTrades) {
            long total = trade.amount * trade.ppuBuy;
            if (total > investmentLeft) {
                break;
            } else {
                investmentLeft -= total;
                profit += trade.amount * (trade.ppuSell - trade.ppuBuy);

                Integer currentMax = maxPrice.getOrDefault(trade.type, 0);
                maxPrice.put(trade.type, Math.max(currentMax, trade.ppuBuy));

                Integer currentMin = minPrice.getOrDefault(trade.type, Integer.MAX_VALUE);
                minPrice.put(trade.type, Math.min(currentMin, trade.ppuSell));

                sheet.addRow(trade.type.name(), trade.amount, trade.ppuBuy, trade.ppuSell);
            }
        }
        sheet.set(0, 7);

        return profit;
    }

    public SpreadSheet getSheet() {
        return sheet;
    }

    public Map<ResourceType, Integer> getMaxPrice() {
        return maxPrice;
    }

    public Map<ResourceType, Integer> getMinPrice() {
        return minPrice;
    }

    public double getInvestment() {
        return investment;
    }

    public int getDays() {
        return days;
    }

    public Map<ResourceType, Map<Integer, AtomicInteger>> getBought() {
        return bought;
    }

    public Map<ResourceType, Map<Integer, AtomicInteger>> getSold() {
        return sold;
    }

    public void getMinMax(Map<Integer, AtomicInteger> map, AtomicInteger min, AtomicInteger max) {
        min.set(Integer.MAX_VALUE);
        max.set(Integer.MIN_VALUE);
        for (Map.Entry<Integer, AtomicInteger> entry : map.entrySet()) {
            min.set(Math.min(entry.getKey(), min.get()));
            max.set(Math.max(entry.getKey(), max.get()));
        }
    }

    private static class Trade implements Comparable<Trade> {
        public final int amount;
        public final int ppuBuy;
        public final int ppuSell;
        public final double avg;
        private final ResourceType type;

        public Trade(ResourceType type, int amount, int ppuBuy, int ppuSell, double avg) {
            this.type = type;
            this.amount = amount;
            this.ppuBuy = ppuBuy;
            this.ppuSell = ppuSell;
            this.avg = avg;
        }

        @Override
        public int compareTo(@NotNull Trade o) {
            return Double.compare(avg, o.avg); // Higher is better
        }

        @Override
        public String toString() {
            return "```Buy " + amount + " x " + type + " for $" + ppuBuy + " and sell for $" + ppuSell + "```";
        }
    }
}
