package link.locutus.discord.util.trade;

import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.QueryOrder;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.trade.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.sheet.SpreadSheet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TradeManager {

    private int[] low, high;
    private double[] highAvg;
    private double[] lowAvg;
    private final link.locutus.discord.db.TradeDB tradeDb;

    private Map<Integer, DBTrade> activeTradesById = new ConcurrentHashMap<>();

    public TradeManager() throws SQLException, ClassNotFoundException {
        this.tradeDb = new link.locutus.discord.db.TradeDB();
    }

    private void updateLowHighCache() {
        double[] low = ResourceType.getBuffer();
        Arrays.fill(low, Double.MAX_VALUE);
        double[] high = ResourceType.getBuffer();
        for (DBTrade trade : activeTradesById.values()) {
            if (trade.getType() != TradeType.GLOBAL) continue;
            low[trade.getResource().ordinal()] = Math.min(low[trade.getResource().ordinal()], trade.getPpu());
            high[trade.getResource().ordinal()] = Math.max(high[trade.getResource().ordinal()], trade.getPpu());
        }
    }

    private void loadActiveTrades() {
        activeTradesById = tradeDb.getActiveTrades().stream().collect(Collectors.toConcurrentMap(DBTrade::getTradeId, Function.identity()));
        updateLowHighCache();
    }

    public synchronized void load() {
        if (lowAvg != null) return;
        long cutOff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        List<DBTrade> trades = getTradeDb().getTrades(cutOff);
        if (trades.isEmpty() && Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS > 0) {
            Locutus.imp().runEventsAsync(this::updateTradeList);
            trades = getTradeDb().getTrades(cutOff);
        }

        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = getAverage(trades);
        lowAvg = PnwUtil.resourcesToArray(averages.getKey());
        lowAvg[0] = 1;
        lowAvg[ResourceType.CREDITS.ordinal()] = 25_000_000;
        highAvg = PnwUtil.resourcesToArray(averages.getValue());
        highAvg[0] = 1;
        highAvg[ResourceType.CREDITS.ordinal()] = 25_000_000;
    }

    public Collection<Transfer> toTransfers(Collection<DBTrade> offers, boolean onlyMoneyTrades) {
        ArrayList<Transfer> allTransfers = new ArrayList<>();
        for ( DBTrade offer : offers) {
            int per = offer.getPpu();
            ResourceType type = offer.getResource();
            if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                if (onlyMoneyTrades) continue;
            } else if (!onlyMoneyTrades) {
                continue;
            }
            long amount = offer.getQuantity();
            if (per <= 1) {
                amount = offer.getTotal();
                type = ResourceType.MONEY;
            }
            Transfer transfer = new Transfer(offer.getDate(), null, offer.getSeller(), false, offer.getBuyer(), false, 0, type, amount);
            allTransfers.add(transfer);
        }
        return allTransfers;
    }

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(long cutOff) {
        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(cutOff);
        return getAverage(trades);
    }

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(List<DBTrade> trades) {
        return getAverage(trades, new Function<ResourceType, Integer>() {
            @Override
            public Integer apply(ResourceType type) {
                switch (type) {
                    default:
                    case CREDITS:
                        return -1;
                    case FOOD:
                        return 50;
                    case COAL:
                    case OIL:
                    case URANIUM:
                    case LEAD:
                    case IRON:
                    case BAUXITE:
                    case GASOLINE:
                    case MUNITIONS:
                    case STEEL:
                    case ALUMINUM:
                        return 1000;
                }
            }
        }, new Function<ResourceType, Integer>() {
            @Override
            public Integer apply(ResourceType type) {
                switch (type) {
                    default:
                    case CREDITS:
                        return -1;
                    case FOOD:
                        return 200;
                    case COAL:
                    case OIL:
                    case URANIUM:
                    case LEAD:
                    case IRON:
                    case BAUXITE:
                    case GASOLINE:
                    case MUNITIONS:
                    case STEEL:
                    case ALUMINUM:
                        return 10000;
                }
            }
        });
    }

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(List<DBTrade> trades, Function<ResourceType, Integer> minF, Function<ResourceType, Integer> maxF) {
        long[][] ppuHigh = new long[ResourceType.values.length][];
        long[][] ppuLow = new long[ResourceType.values.length][];
        int[] ppuWindowLow = new int[ResourceType.values.length];
        int[] ppuWindowHigh = new int[ResourceType.values.length];

        for (int i = 0; i < ResourceType.values.length; i++) {
            ResourceType type = ResourceType.values[i];
            if (type == ResourceType.MONEY) continue;
            int min = minF.apply(type);
            int max = maxF.apply(type);

            ppuWindowLow[i] = min;
            ppuWindowHigh[i] = max;

            if (min == -1) {
                ppuHigh[i] = new long[2];
                ppuLow[i] = new long[2];
                continue;
            }

            int len = max - min + 1;
            ppuHigh[i] = new long[len];
            ppuLow[i] = new long[len];
        }

        for ( DBTrade offer : trades) {
            ResourceType type = offer.getResource();
            int factor = type == ResourceType.FOOD ? 1 : 25;
            if (offer.getPpu() <= 20 * factor || offer.getPpu() > 300 * factor) {
                continue;
            }
            long[] ppuArr;
            if (offer.isBuy()) {
                ppuArr = ppuLow[type.ordinal()];
            } else {
                ppuArr = ppuHigh[type.ordinal()];
            }
            int min = ppuWindowLow[type.ordinal()];
            if (min == -1) {
                ppuArr[0] += offer.getQuantity();
                ppuArr[1] += offer.getPpu() * (long) offer.getQuantity();
            } else {
                int arrI = offer.getPpu() - min;
                if (ppuArr == null) System.out.println("remove:||Null ppu " + type + " | " + offer.getTradeId());
                if (arrI >= 0 && arrI < ppuArr.length) {
                    ppuArr[arrI] += offer.getQuantity();
                }
            }
        }
        Map<ResourceType, Double> lowMap = new ConcurrentHashMap<>();
        Map<ResourceType, Double> highMap = new ConcurrentHashMap<>();
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.MONEY) continue;
            int i = type.ordinal();
            int min = ppuWindowLow[type.ordinal()];
            long[] lowArr = ppuLow[i];
            long[] highArr = ppuHigh[i];
            double low,high;
            if (min == -1) {
                low = lowArr[1] / (double) lowArr[0];
                high = lowArr[1] / (double) lowArr[0];
            } else {
                low = min + ArrayUtil.getMedian(lowArr);
                high = min + ArrayUtil.getMedian(highArr);
            }
            lowMap.put(type, low);
            highMap.put(type, high);
        }

        return new AbstractMap.SimpleEntry<>(lowMap, highMap);

    }

    public Map<Integer, double[]> inflows(Collection<Transfer> transfers) {
        return inflows(transfers, false);
    }

    public Map<Integer, double[]> inflows(Collection<Transfer> transfers, boolean byAA) {
        Map<Integer, double[]> totals = new HashMap<>();
        for (Transfer transfer : transfers) {
            int sender = transfer.getSender();
            int receiver = transfer.getReceiver();

            if (byAA) {
                DBNation senNation = Locutus.imp().getNationDB().getNation(sender);
                DBNation recNation = Locutus.imp().getNationDB().getNation(receiver);
                sender = senNation == null ? 0 : senNation.getAlliance_id();
                receiver = recNation == null ? 0 : recNation.getAlliance_id();
            }

            double[] senderTotal = totals.computeIfAbsent(sender, f -> new double[ResourceType.values.length]);
            senderTotal[transfer.getRss().ordinal()] -= transfer.getAmount();

            double[] receiverTotal = totals.computeIfAbsent(receiver, f -> new double[ResourceType.values.length]);
            receiverTotal[transfer.getRss().ordinal()] += transfer.getAmount();
        }
        return totals;
    }

    public long[] totalByResource(Collection<DBTrade> offers) {
        long[] total = new long[ResourceType.values.length];
        for ( DBTrade offer : offers) {
            total[offer.getResource().ordinal()] += offer.getTotal();
        }
        return total;
    }

    public long[] volumeByResource(Collection<DBTrade> offers) {
        long[] volume = new long[ResourceType.values.length];
        for ( DBTrade offer : offers) {
            volume[offer.getResource().ordinal()] += offer.getQuantity();
        }
        return volume;
    }

    public Collection<DBTrade> filterOutliers(Collection<DBTrade> offers) {
        List<DBTrade> result = new ArrayList<>(offers.size());
        for ( DBTrade offer : offers) {
            ResourceType type = offer.getResource();
            int factor = type == ResourceType.FOOD ? 1 : 25;
            if (offer.getPpu() <= 20 * factor || offer.getPpu() > (type == ResourceType.FOOD ? 300 : 10000)) {
                continue;
            }
            result.add(offer);
        }
        return result;
    }

    public Collection<DBTrade> getLow(Collection<DBTrade> offers) {
        return offers.stream().filter(f -> f.isBuy()).collect(Collectors.toList());
    }

    public Collection<DBTrade> getHigh(Collection<DBTrade> offers) {
        return offers.stream().filter(f -> !f.isBuy()).collect(Collectors.toList());
    }

    public Map<Integer, double[]> ppuByNation(Collection<DBTrade> offers) {
        return ppuByNation(offers, false);
    }

    public Map<Integer, double[]> ppuByNation(Collection<DBTrade> offers, boolean byAA) {
        Map<Integer, long[]> volume = new HashMap<>();
        Map<Integer, double[]> ppu = new HashMap<>();

        int len = ResourceType.values.length;

        for ( DBTrade offer : offers) {
            int sender = offer.getSeller();
            int receiver = offer.getBuyer();

            if (byAA) {
                DBNation senNation = Locutus.imp().getNationDB().getNation(sender);
                DBNation recNation = Locutus.imp().getNationDB().getNation(receiver);
                sender = senNation == null ? 0 : senNation.getAlliance_id();
                receiver = recNation == null ? 0 : recNation.getAlliance_id();
            }

            int ord = offer.getResource().ordinal();
            int amt = (int) offer.getQuantity();
            double total = offer.getTotal();

            volume.computeIfAbsent(sender, f -> new long[len])[ord] += amt;
            volume.computeIfAbsent(receiver, f -> new long[len])[ord] += amt;

            ppu.computeIfAbsent(sender, f -> new double[len])[ord] += total;
            ppu.computeIfAbsent(receiver, f -> new double[len])[ord] += total;
        }

        for (Map.Entry<Integer, double[]> entry : ppu.entrySet()) {
            long[] myVol = volume.get(entry.getKey());
            double[] myPpu = entry.getValue();
            for (int i = 0; i < len; i++) {
                if (myVol[i] != 0) myPpu[i] /= myVol[i];
            }
        }
        return ppu;
    }

    public Map<ResourceType, int[]> getPriceHistory() {
        Auth auth = Locutus.imp().getRootAuth();
        return PnwUtil.withLogin(new Callable<Map<ResourceType, int[]>>() {
            @Override
            public Map<ResourceType, int[]> call() throws Exception {
                Map<ResourceType, int[]> result = new EnumMap<ResourceType, int[]>(ResourceType.class);

                String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/trade/history/";
                String html = auth.readStringFromURL(url, Collections.emptyMap());

                String var = "var histcatexplong =";
                int varI = html.indexOf(var);
                int start = html.indexOf('[', varI);
                int end = StringMan.findMatchingBracket(html, start);
                String json = html.substring(start, end + 1)
                        .replaceAll("\\}[ ]*,[ ]*\\]", "}]")
                        .replaceAll("\\][ ]*,[ ]*\\]", "]]");

                JsonParser jsonParser = new JsonParser();
                JsonArray array = jsonParser.parse(json).getAsJsonArray();
                for (JsonElement elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    ResourceType type = ResourceType.valueOf(obj.get("key").getAsString().toUpperCase());

                    JsonArray values = obj.getAsJsonArray("values");
                    int[] ppuHistory = new int[values.size()];
                    for (int i = 0; i < values.size(); i++) {
                        ppuHistory[i] = values.get(i).getAsJsonArray().get(1).getAsInt();
                    }
                    result.put(type, ppuHistory);
                }
                return result;
            }
        }, auth);
    }

    public long[] getVolumeHistory(ResourceType type) {
        String key = "volume." + type;
        long[] result = TimeUtil.runDayTask(key, new Function<Long, long[]>() {
            @Override
            public long[] apply(Long aLong) {
                try {
                    String url = "" + Settings.INSTANCE.PNW_URL() + "/world-graphs/graphID=%s";
                    String html = FileUtil.readStringFromURL(String.format(url, type.getGraphId()));

                    String var = String.format("total_%s_over_time_Trace1", type.name().toLowerCase());
                    int varI = html.indexOf(var);
                    int start = html.indexOf("{", varI);
                    int end = StringMan.findMatchingBracket(html, start);
                    String json = html.substring(start, end + 1);
                    JsonParser jsonParser = new JsonParser();
                    JsonArray totals = jsonParser.parse(json).getAsJsonObject().getAsJsonArray("y");
                    long[] volume = new long[totals.size()];
                    for (int i = 0; i < volume.length; i++) {
                        volume[i] = Long.parseLong(totals.get(i).getAsString());
                    }
                    byte[] array = ArrayUtil.toByteArray(volume);
                    Locutus.imp().getDiscordDB().setInfo(DiscordMeta.RESOURCE_VOLUME_TYPE, type.ordinal(), array);

                    return volume;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        if (result == null) {
            ByteBuffer data = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.RESOURCE_VOLUME_TYPE, type.ordinal());
            result = ArrayUtil.toLongArray(data.array());
        }
        return result;
    }

    public link.locutus.discord.db.TradeDB getTradeDb() {
        return tradeDb;
    }

    public int getPrice(ResourceType type, boolean isBuy) {
        if (type == ResourceType.MONEY) return 1;
        return isBuy ? high[type.ordinal()] : low[type.ordinal()];
    }

    public int getHigh(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return high[type.ordinal()];
    }

    public int getLow(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return low[type.ordinal()];
    }

    public double getHighAvg(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return highAvg[type.ordinal()];
    }

    public double getLowAvg(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return lowAvg[type.ordinal()];
    }

    public Map<ResourceType, Double> getHigh() {
        Map<ResourceType, Double> result = new EnumMap<ResourceType, Double>(ResourceType.class);
        for (int i = 0; i < high.length; i++) {
            result.put(ResourceType.values[i], (double) high[i]);
        }
        return result;
    }

    public Map<ResourceType, Double> getLow() {
        Map<ResourceType, Double> result = new EnumMap<ResourceType, Double>(ResourceType.class);
        for (int i = 0; i < low.length; i++) {
            result.put(ResourceType.values[i], (double) low[i]);
        }
        return result;
    }



    private boolean fetchNewTradesNextTick = true;

    public synchronized boolean updateTradeList(Consumer<Event> eventConsumer) throws IOException {
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        // get last trade
        List<DBTrade> latestTrades = tradeDb.getTrades(f -> f.order("tradeId", QueryOrder.OrderDirection.DESC).limit(1));
        DBTrade latest = latestTrades.isEmpty() ? null : latestTrades.get(0);
        int latestId = latest == null ? 0 : latest.getTradeId();
        long latestDate = latest == null ? 0 : latest.getDate();
        if (latest == null || latestDate < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)) {

            ArrayDeque<DBTrade> trades = new ArrayDeque<>();
            api.fetchTradesWithInfo(f -> f.setMin_id(latestId + 1), new Predicate<Trade>() {
                @Override
                public boolean test(Trade trade) {
                    trades.add(new DBTrade(trade));
                    if (trades.size() > 1000) {
                        tradeDb.saveTrades(new ArrayList<>(trades));
                        trades.clear();
                    }
                    return false;
                }
            });
            tradeDb.saveTrades(new ArrayList<>(trades));

        } else {
            boolean mixupAlerts = (System.currentTimeMillis() - latestDate) < TimeUnit.MINUTES.toMillis(30);

            boolean fetchedNewTrades = false;
            List<Trade> fetched = new ArrayList<>();

            if (fetchNewTradesNextTick) {
                List<Trade> trades = api.fetchTradesWithInfo(f -> f.setMin_id(latestId + 1), f -> true);
                fetched.addAll(trades);

                fetchedNewTrades = true;
                fetchNewTradesNextTick = false;
            }

            // fetch new trades
            {
                List<Integer> idsToFetch = new ArrayList<>(activeTradesById.keySet());
                int idToAdd = latestId + 1;
                if (!fetchedNewTrades) {
                    while (idsToFetch.size() < 999) {
                        idsToFetch.add(idToAdd++);
                    }
                    Collections.sort(idsToFetch);
                }

                api.iterateIdChunks(idsToFetch, 999, new Consumer<List<Integer>>() {
                    @Override
                    public void accept(List<Integer> integers) {
                        List<Trade> trades = api.fetchTradesWithInfo(f -> f.setId(integers), f -> true);
                        fetched.addAll(trades);
                    }
                });
                int finalIdToAdd = idToAdd;
                if (!fetchedNewTrades && (idsToFetch.size() > 999 || fetched.stream().anyMatch(t -> t.getId() >= finalIdToAdd - 1))) {
                    fetchNewTradesNextTick = true;
                }

                processTrades(fetched, idsToFetch, mixupAlerts, eventConsumer);
            }




            List<DBTrade> newTrades = new ArrayList<>();
        }
        updateLowHighCache();



//        List<TradeContainer> trades = new ArrayList<>();
//        if (force) {
//            for (ResourceType type : ResourceType.values) {
//                if (type == ResourceType.MONEY) continue;
//                PoliticsAndWarV2 api = Locutus.imp().getPnwApi();
//                System.out.println("API " + api);
//                List<TradeContainer> apiTrades = api.getTradehistory(10000, type).getTrades();
//                trades.addAll(apiTrades);
//            }
//        } else {
//            trades = Locutus.imp().getPnwApi().getTradehistoryByAmount(10000).getTrades();
//        }
//
//        DBTrade latest = tradeDb.getLatestTrade();
//        int latestId = latest == null ? -1 : latest.getTradeId();
//
//        Set<Integer> alertedNations = new HashSet<>();
//        List<DBTrade> DBTrades = new ArrayList<>();
//        for (TradeContainer trade : trades) {
//             DBTrade offer = new DBTrade(trade);
//
//            if (offer.getTradeId() > latestId && alerts) {
//                handleTradeAlerts(DBTrade, alertedNations);
//            }
//            DBTrades.add(DBTrade);
//        }
//        tradeDb.addTrades(DBTrades);

        return true;
    }

    // cached
    private Map<ResourceType, List<TradeSubscription>> subscriptionsByRss;
    private long lastSubscriptionUpdate;

    public Map<ResourceType, List<TradeSubscription>> getSubscriptions() {
        // cache by minute
        Map<ResourceType, List<TradeSubscription>> localSubMap = this.subscriptionsByRss;
        if (localSubMap != null && System.currentTimeMillis() - lastSubscriptionUpdate < TimeUnit.MINUTES.toMillis(1)) {
            return localSubMap;
        }
        lastSubscriptionUpdate = System.currentTimeMillis();
        List<TradeSubscription> subscriptions = tradeDb.getSubscriptions(f -> {});
        localSubMap = new EnumMap<>(ResourceType.class);
        for (TradeSubscription subscription : subscriptions) {
            localSubMap.computeIfAbsent(subscription.getResource(), k -> new ArrayList<>()).add(subscription);
        }
        this.subscriptionsByRss = localSubMap;
        return localSubMap;
    }

    public Map<ResourceType, DBTrade> getTopTrades(boolean isBuy) {
        Map<ResourceType, DBTrade> result = new EnumMap<ResourceType, DBTrade>(ResourceType.class);
        activeTradesById.forEach((id, trade) -> {
            if (trade.isBuy() == isBuy && trade.getType() == TradeType.GLOBAL) {
                DBTrade existing = result.get(trade.getResource());
                if (
                        existing == null ||
                    (isBuy ? trade.getPpu() > existing.getPpu() : trade.getPpu() < existing.getPpu()) ||
                    (trade.getPpu() == existing.getPpu() && trade.getDate() < existing.getDate()))
                {
                    result.put(trade.getResource(), trade);
                }
            }
        });
        return result;
    }

    private void processTrades(List<Trade> trades, List<Integer> idsRequested, boolean mixupAlerts, Consumer<Event> eventConsumer) {
        Map<ResourceType, DBTrade> topBuys = getTopTrades(true);
        Map<ResourceType, DBTrade> topSells = getTopTrades(false);

        Map<Integer, DBTrade> newTrades = new HashMap<>();
        for (Trade trade : trades) {
            DBTrade dbTrade = new DBTrade(trade);
            newTrades.put(dbTrade.getTradeId(), dbTrade);
        }

        Set<DBTrade> toDelete = new HashSet<>();
        Set<DBTrade> toSave = new HashSet<>();

        Set<ResourceType> resourcesAffected = new HashSet<>();

        // Delete trades
        for (int id : idsRequested) {
            if (newTrades.containsKey(id)) continue;
            DBTrade existing = activeTradesById.remove(id);
            if (existing != null) {
                existing.setDate_accepted(0);
                toDelete.add(existing);
                resourcesAffected.add(existing.getResource());

                if (eventConsumer != null) eventConsumer.accept(new TradeDeleteEvent(existing));
            }
        }

        for (Map.Entry<Integer, DBTrade> entry : newTrades.entrySet()) {
            DBTrade previous = activeTradesById.get(entry.getKey());
            DBTrade current = entry.getValue();

            if (!current.equals(previous)) {
                resourcesAffected.add(current.getResource());
                toSave.add(current);

                if (eventConsumer != null) {
                    if (previous == null) {
                        eventConsumer.accept(new TradeCreateEvent(previous));
                    } else if (current.isActive()) {
                        eventConsumer.accept(new TradeUpdateEvent(previous, current));
                    } else {
                        eventConsumer.accept(new TradeCompleteEvent(previous, current));
                    }
                }
                if (current.isActive()) {
                    activeTradesById.put(current.getTradeId(), current);
                } else {
                    activeTradesById.remove(current.getTradeId());
                }
            }
        }

        if (eventConsumer != null && mixupAlerts) {
            Map<ResourceType, List<TradeSubscription>> subscriptions = getSubscriptions();

            Map<ResourceType, DBTrade> topBuysNew = getTopTrades(true);
            Map<ResourceType, DBTrade> topSellsNew = getTopTrades(false);

            for (ResourceType type : resourcesAffected) {
                List<TradeSubscription> subscriptionsToCall = new ArrayList<>();

                DBTrade topBuyOld = topBuys.get(type);
                DBTrade topSellOld = topSells.get(type);
                DBTrade topBuy = topBuysNew.get(type);
                DBTrade topSell = topSellsNew.get(type);

                List<TradeSubscription> rssSubs = subscriptions.get(type);
                if (rssSubs != null) {
                    for (TradeSubscription subscription : rssSubs) {
                        if (subscription.applies(topBuy, topSell, topBuyOld, topSellOld)) {
                            subscriptionsToCall.add(subscription);
                        }
                    }
                }
                if (!subscriptionsToCall.isEmpty()) {
                    eventConsumer.accept(new BulkTradeSubscriptionEvent(subscriptionsToCall, topBuyOld, topSellOld, topBuy, topSell));
                }
            }
        }

        tradeDb.saveTrades(toSave);
        tradeDb.deleteTradesById(toDelete.stream().map(DBTrade::getTradeId).toList());



        // mixups
        // undercut
        // subscriptions
    }

    public void handleTradeAlerts(DBTrade offer, Set<Integer> alertedNations) {
        if (offer.getResource() == ResourceType.CREDITS) return;
        if (offer.getTotal() <= 50000) return; // Don't do alerts for less than 50k
        if (offer.getPpu() < 10 || offer.getPpu() > 10000) return;

        Integer acceptingNationId = offer.isBuy() ? offer.getBuyer() : offer.getSeller();
        if (alertedNations.contains(acceptingNationId)) return;

        DBNation acceptingNation = DBNation.byId(acceptingNationId);
        if (acceptingNation == null || acceptingNation.getPosition() <= 1 || acceptingNation.getAlliance_id() == 0) return;

        DBNation DBTradeingNation = DBNation.byId(offer.isBuy() ? offer.getSeller() : offer.getBuyer());
        if (DBTradeingNation == null || DBTradeingNation.getAlliance_id() == acceptingNation.getAlliance_id()) return;

        if (offer.isBuy() && offer.getPpu() < getHigh(offer.getResource())) return; // bought cheaper than market
        if (!offer.isBuy() && offer.getPpu() > getLow(offer.getResource())) return; // bought higher than market

        GuildDB db = Locutus.imp().getGuildDBByAA(acceptingNation.getAlliance_id());
        if (db == null || !db.isWhitelisted() || !Boolean.TRUE.equals(db.getOrNull(GuildDB.Key.RESOURCE_CONVERSION))) return;

        User user = acceptingNation.getUser();
        if (user == null) return;
        Member member = db.getGuild().getMember(user);
        if (member == null) return;

        Role pingOptOut = Roles.AUDIT_ALERT_OPT_OUT.toRole(db.getGuild());
        if (pingOptOut != null && member.getRoles().contains(pingOptOut)) return;

        GuildMessageChannel rssChannel = db.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
        GuildMessageChannel auditChannel = db.getOrNull(GuildDB.Key.MEMBER_AUDIT_ALERTS);
        if (rssChannel == null || auditChannel == null) return;

        Set<Integer> tradePartners = db.getAllies(true);
        tradePartners.addAll(db.getCoalition(Coalition.TRADE));

        if (!tradePartners.contains(DBTradeingNation.getAlliance_id())) return;

        alertedNations.add(acceptingNationId);

        String type = offer.isBuy() ? "buy" : "sell";
        String DBTradeStr = MathMan.format(offer.getQuantity()) + "x" + offer.getResource() + " @ $" + MathMan.format(offer.getPpu()) + " -> " + DBTradeingNation.getNation();
        StringBuilder message = new StringBuilder();

        message.append(user.getAsMention() + " (see pins to opt out)");
        message.append("\nYou accepted a " + type + " DBTrade directly from a nation that is not a trade partner (" + DBTradeStr + ")");
        message.append("\nConsider instead:");
        message.append("\n1. Creating trade DBTrades, see the trading guide for more info: <https://docs.google.com/document/d/1sO4TnONEg3nPMr3SXK_Q1zeYj--V42xa-hhVKHxhv1A/edit#heading=h.723u4ibh8mxy>");
        message.append("\n2. Buy from the alliance by asking in " + rssChannel.getAsMention());
        message.append("\n3. Sell to the alliance by depositing with the note `#cash`");
        Set<String> tradePartnerNames = new LinkedHashSet<>();
        for (Integer aaId : tradePartners) {
            DBAlliance aa = DBAlliance.getOrCreate(aaId);
            if (aa.exists()) tradePartnerNames.add(aa.getName());
        }
        message.append("\n4. Trade with nations in any of our trade partners: `" + StringMan.getString(tradePartnerNames) + "`");
//        link.locutus.discord.util.RateLimitUtil.queue(auditChannel.sendMessage(message.toString()));
    }

    private final Map<Continent, Map.Entry<Double, Long>> radiation = new ConcurrentHashMap<>();

    public synchronized double getGlobalRadiation() {
        double global = 0;
        for (Continent continent : Continent.values) {
            double rads = getGlobalRadiation(continent);
            global += rads;
        }
        global /= 5;
        return global;
    }

    public double getGlobalRadiation(Continent continent) {
        return getGlobalRadiation(continent, false);
    }

    public synchronized double getGlobalRadiation(Continent continent, boolean forceUpdate) {
        long currentTurn = TimeUtil.getTurn();

        if (!forceUpdate) {
            Map.Entry<Double, Long> valuePair = radiation.get(continent);
            if (valuePair != null) {
                if (valuePair.getValue() == currentTurn) return valuePair.getKey();
            }

            ByteBuffer radsStr = Locutus.imp().getDiscordDB().getInfo(DiscordMeta.RADIATION_CONTINENT, continent.ordinal());
            if (radsStr != null) {
                double rads = radsStr.getLong() / 100d;
                long turn = radsStr.getLong();

                radiation.put(continent, new AbstractMap.SimpleEntry<>(rads, turn));
                if (turn == currentTurn) {
                    return rads;
                }
            }
        }
        GameInfo gameInfo = Locutus.imp().getV3().getGameInfo();
        Radiation info = gameInfo.getRadiation();

        setRadiation(Continent.NORTH_AMERICA, info.getNorth_america());
        setRadiation(Continent.SOUTH_AMERICA, info.getSouth_america());
        setRadiation(Continent.EUROPE, info.getEurope());
        setRadiation(Continent.AFRICA, info.getAfrica());
        setRadiation(Continent.ASIA, info.getAsia());
        setRadiation(Continent.AUSTRALIA, info.getAustralia());
        setRadiation(Continent.ANTARCTICA, info.getAntarctica());

        this.gameDate = gameInfo.getGame_date();

        return radiation.get(continent).getKey();
    }

    private Instant gameDate;

    public Instant getGameDate() {
        if (this.gameDate == null) {
            getGlobalRadiation(Continent.AFRICA, true);
        }
        return this.gameDate;
    }

    private void setRadiation(Continent continent, double rads) {
        long currentTurn = TimeUtil.getTurn();
        long[] pair = new long[]{(long) (rads * 100), currentTurn};
        byte[] bytes = ArrayUtil.toByteArray(pair);
        Locutus.imp().getDiscordDB().setInfo(DiscordMeta.RADIATION_CONTINENT, continent.ordinal(), bytes);
        radiation.put(continent, new AbstractMap.SimpleEntry<>(rads, currentTurn));
    }

    public void updateColorBlocs() {
        for (Color color : Locutus.imp().getV3().getColors()) {
            NationColor dbColor = NationColor.fromV3(color);
            dbColor.setTurnBonus(color.getTurn_bonus());
            dbColor.setVotedName(color.getBloc_name());
        }
        tradeDb.saveColorBlocs();
    }
}