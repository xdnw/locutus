package link.locutus.discord.util.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
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
import link.locutus.discord.apiv1.domains.subdomains.TradeContainer;
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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TradeManager {
    private final Map<ResourceType, Integer> high;
    private final Map<ResourceType, Integer> low;
    private final Map<ResourceType, Integer> highNation;
    private final Map<ResourceType, Integer> lowNation;

    private final double[] highAvg;
    private final double[] lowAvg;

    private final Map<ResourceType, Double> stockPile;
    private final TradeDB tradeDb;

    public TradeManager() throws SQLException, ClassNotFoundException {
        this.stockPile = new ConcurrentHashMap<>();
        this.tradeDb = new TradeDB();
        this.high = tradeDb.getTradePrice(true);
        this.low = tradeDb.getTradePrice(false);
        this.lowNation = new HashMap<>();
        this.highNation = new HashMap<>();

        long cutOff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        List<Offer> trades = getTradeDb().getOffers(cutOff);
        if (trades.isEmpty() && Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS > 0) {
            try {
                updateTradeList(true, false);
                trades = getTradeDb().getOffers(cutOff);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = getAverage(trades);
        lowAvg = PnwUtil.resourcesToArray(averages.getKey());
        lowAvg[0] = 1;
        lowAvg[ResourceType.CREDITS.ordinal()] = 25_000_000;
        highAvg = PnwUtil.resourcesToArray(averages.getValue());
        highAvg[0] = 1;
        highAvg[ResourceType.CREDITS.ordinal()] = 25_000_000;
    }

    public Collection<Transfer> toTransfers(Collection<Offer> offers, boolean onlyMoneyTrades) {
        ArrayList<Transfer> allTransfers = new ArrayList<>();
        for (Offer offer : offers) {
            int per = offer.getPpu();
            ResourceType type = offer.getResource();
            if (per > 1 && (per < 10000 || (type != ResourceType.FOOD && per < 100000))) {
                if (onlyMoneyTrades) continue;
            } else if (!onlyMoneyTrades) {
                continue;
            }
            long amount = offer.getAmount();
            if (per <= 1) {
                amount = offer.getTotal();
                type = ResourceType.MONEY;
            }
            Transfer transfer = new Transfer(offer.getEpochms(), null, offer.getSeller(), false, offer.getBuyer(), false, 0, type, amount);
            allTransfers.add(transfer);
        }
        return allTransfers;
    }

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(long cutOff) {
        List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(cutOff);
        return getAverage(trades);
    }

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(List<Offer> trades) {
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

    public Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> getAverage(List<Offer> trades, Function<ResourceType, Integer> minF, Function<ResourceType, Integer> maxF) {
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

        for (Offer offer : trades) {
            ResourceType type = offer.getResource();
            int factor = type == ResourceType.FOOD ? 1 : 25;
            if (offer.getPpu() <= 20 * factor || offer.getPpu() > (type == ResourceType.FOOD ? 200 : 10000)) {
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
                ppuArr[0] += offer.getAmount();
                ppuArr[1] += offer.getPpu() * (long) offer.getAmount();
            } else {
                int arrI = offer.getPpu() - min;
                if (arrI >= 0 && arrI < ppuArr.length) {
                    ppuArr[arrI] += offer.getAmount();
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

    public long[] totalByResource(Collection<Offer> offers) {
        long[] total = new long[ResourceType.values.length];
        for (Offer offer : offers) {
            total[offer.getResource().ordinal()] += offer.getTotal();
        }
        return total;
    }

    public long[] volumeByResource(Collection<Offer> offers) {
        long[] volume = new long[ResourceType.values.length];
        for (Offer offer : offers) {
            volume[offer.getResource().ordinal()] += offer.getAmount();
        }
        return volume;
    }

    public Collection<Offer> filterOutliers(Collection<Offer> offers) {
        List<Offer> result = new ArrayList<>(offers.size());
        for (Offer offer : offers) {
            ResourceType type = offer.getResource();
            int factor = type == ResourceType.FOOD ? 1 : 25;
            if (offer.getPpu() <= 20 * factor || offer.getPpu() > (type == ResourceType.FOOD ? 200 : 10000)) {
                continue;
            }
            result.add(offer);
        }
        return result;
    }

    public Collection<Offer> getLow(Collection<Offer> offers) {
        return offers.stream().filter(f -> f.isBuy()).collect(Collectors.toList());
    }

    public Collection<Offer> getHigh(Collection<Offer> offers) {
        return offers.stream().filter(f -> !f.isBuy()).collect(Collectors.toList());
    }

    public Map<Integer, double[]> ppuByNation(Collection<Offer> offers) {
        return ppuByNation(offers, false);
    }

    public Map<Integer, double[]> ppuByNation(Collection<Offer> offers, boolean byAA) {
        Map<Integer, long[]> volume = new HashMap<>();
        Map<Integer, double[]> ppu = new HashMap<>();

        int len = ResourceType.values.length;

        for (Offer offer : offers) {
            int sender = offer.getSeller();
            int receiver = offer.getBuyer();

            if (byAA) {
                DBNation senNation = Locutus.imp().getNationDB().getNation(sender);
                DBNation recNation = Locutus.imp().getNationDB().getNation(receiver);
                sender = senNation == null ? 0 : senNation.getAlliance_id();
                receiver = recNation == null ? 0 : recNation.getAlliance_id();
            }

            int ord = offer.getResource().ordinal();
            int amt = (int) offer.getAmount();
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

    public TradeDB getTradeDb() {
        return tradeDb;
    }

    public Double setStockStr(String type, Number value) {
        return setStock(ResourceType.valueOf(type.toUpperCase()), value.doubleValue());
    }

    public int getPrice(ResourceType type, boolean isBuy) {
        if (type == ResourceType.MONEY) return 1;
        return isBuy ? high.get(type) : low.get(type);
    }

    public int getHigh(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return high.getOrDefault(type, 0);
    }

    public DBNation getLowNation(ResourceType type) {
        Integer id = lowNation.get(type);
        return id == null ? null : Locutus.imp().getNationDB().getNation(id);
    }

    public DBNation getHighNation(ResourceType type) {
        Integer id = highNation.get(type);
        return id == null ? null : Locutus.imp().getNationDB().getNation(id);
    }

    public int getLow(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return low.getOrDefault(type, 0);
    }

    public double getHighAvg(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return highAvg[type.ordinal()];
    }

    public double getLowAvg(ResourceType type) {
        if (type == ResourceType.MONEY) return 1;
        return lowAvg[type.ordinal()];
    }

    public Map<ResourceType, Integer> getHigh() {
        return high;
    }

    public Map<ResourceType, Integer> getLow() {
        return low;
    }

    public void setHigh(ResourceType type, Offer offer) {
        if (offer == null) {
            this.high.remove(type);
            this.highNation.remove(type);
        } else {
            this.high.put(type, offer.getPpu());
            this.highNation.put(type, offer.getBuyer());
            tradeDb.setTradePrice(type, offer.getPpu(), true);
        }
    }

    public void setLow(ResourceType type, Offer offer) {
        if (offer == null) {
            this.low.remove(type);
            this.lowNation.remove(type);
        } else {
            this.low.put(type, offer.getPpu());
            this.lowNation.put(type, offer.getSeller());
            tradeDb.setTradePrice(type, offer.getPpu(), false);
        }
    }

    public Double setStock(ResourceType type, double value) {
        if (type == null) {
            new Exception().printStackTrace();
        }
        return this.stockPile.put(type, value);
    }

    public double getStock(ResourceType type) {
        return stockPile.getOrDefault(type, 0d);
    }

    public double getBalance() {
        return stockPile.getOrDefault(ResourceType.MONEY, 0d);
    }

    public synchronized boolean updateTradeList(boolean force, boolean alerts) throws IOException {
        List<TradeContainer> trades = new ArrayList<>();
        if (force) {
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.MONEY) continue;
                trades.addAll(Locutus.imp().getPnwApi().getTradehistory(10000, type).getTrades());
            }
        } else {
            trades = Locutus.imp().getPnwApi().getTradehistoryByAmount(10000).getTrades();
        }

        Offer latest = tradeDb.getLatestTrade();
        int latestId = latest == null ? -1 : latest.getTradeId();

        Set<Integer> alertedNations = new HashSet<>();
        List<Offer> offers = new ArrayList<>();
        for (TradeContainer trade : trades) {
            Offer offer = new Offer(trade);

            if (offer.getTradeId() > latestId && alerts) {
                handleTradeAlerts(offer, alertedNations);
            }
            offers.add(offer);
        }
        tradeDb.addTrades(offers);

        return true;
    }

    public void handleTradeAlerts(Offer offer, Set<Integer> alertedNations) {
        if (offer.getResource() == ResourceType.CREDITS) return;
        if (offer.getTotal() <= 50000) return; // Don't do alerts for less than 50k
        if (offer.getPpu() < 10 || offer.getPpu() > 10000) return;

        Integer acceptingNationId = offer.isBuy() ? offer.getBuyer() : offer.getSeller();
        if (alertedNations.contains(acceptingNationId)) return;

        DBNation acceptingNation = DBNation.byId(acceptingNationId);
        if (acceptingNation == null || acceptingNation.getPosition() <= 1 || acceptingNation.getAlliance_id() == 0) return;

        DBNation offeringNation = DBNation.byId(offer.isBuy() ? offer.getSeller() : offer.getBuyer());
        if (offeringNation == null || offeringNation.getAlliance_id() == acceptingNation.getAlliance_id()) return;

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

        if (!tradePartners.contains(offeringNation.getAlliance_id())) return;

        alertedNations.add(acceptingNationId);

        String type = offer.isBuy() ? "buy" : "sell";
        String offerStr = MathMan.format(offer.getAmount()) + "x" + offer.getResource() + " @ $" + MathMan.format(offer.getPpu()) + " -> " + offeringNation.getNation();
        StringBuilder message = new StringBuilder();

        message.append(user.getAsMention() + " (see pins to opt out)");
        message.append("\nYou accepted a " + type + " offer directly from a nation that is not a trade partner (" + offerStr + ")");
        message.append("\nConsider instead:");
        message.append("\n1. Creating trade offers, see the trading guide for more info: <https://docs.google.com/document/d/1sO4TnONEg3nPMr3SXK_Q1zeYj--V42xa-hhVKHxhv1A/edit#heading=h.723u4ibh8mxy>");
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

    public synchronized double getGlobalRadiation(Continent continent) {
        long currentTurn = TimeUtil.getTurn();

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

        try {
            String url = "" + Settings.INSTANCE.PNW_URL() + "/world/radiation/";
            String html = FileUtil.readStringFromURL(url);
            Document dom = Jsoup.parse(html);

            Elements elems = dom.select("h3:contains(Radiation Index)");
            for (Element elem : elems) {
                String continentStr = elem.text().toLowerCase().replace("radiation index", "").trim().replace(" ", "_").toUpperCase();
                try {
                    Continent currentContinent = Continent.valueOf(continentStr);

                    double rads = Double.parseDouble(elem.nextElementSibling().text().trim());

                    long[] pair = new long[]{(long) (rads * 100), currentTurn};
                    byte[] bytes = ArrayUtil.toByteArray(pair);
                    Locutus.imp().getDiscordDB().setInfo(DiscordMeta.RADIATION_CONTINENT, currentContinent.ordinal(), bytes);
                    radiation.put(currentContinent, new AbstractMap.SimpleEntry<>(rads, currentTurn));

                } catch (IllegalArgumentException ignore) {
                }
            }
        } catch (IOException e) {
            AlertUtil.error("Could not fetch radiation (1)", new Exception());
        }

        Map.Entry<Double, Long> pair = radiation.get(continent);
        if (pair == null || pair.getValue() < currentTurn - 1) {
            AlertUtil.error("Could not fetch radiation (2)", new Exception());
        }
        return pair == null ? 32.37d : pair.getKey();
    }

    private Map<NationColor, Integer> tradeBonus;
    private long tradeBonusTurn = 0L;

    public synchronized int getTradeBonus(NationColor color) {
        Callable<Map<NationColor, Integer>> task;
        task = new Callable<Map<NationColor, Integer>>() {
            @Override
            public Map<NationColor, Integer> call() throws Exception {
                Map<NationColor, Integer> map = new HashMap<>();
                String html = FileUtil.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/leaderboards/display=color");
                Document dom = Jsoup.parse(html);
                Element table = dom.getElementsByClass("nationtable").get(0);
                Elements rows = table.getElementsByTag("tr");
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements columns = row.getElementsByTag("td");
                    String rowColor = columns.get(0).child(0).attr("title");
                    Integer value = MathMan.parseInt(columns.get(5).text());
                    NationColor color = NationColor.valueOf(rowColor.toUpperCase());
                    map.put(color, value);
                }
                return map;
            }
        };
        if (tradeBonus == null) {
            try {
                tradeBonus = task.call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            long currentTurn = TimeUtil.getTurn();
            if (currentTurn != tradeBonusTurn) {
                Map<NationColor, Integer> tradeBonusTmp = TimeUtil.runTurnTask(TradeManager.class.getSimpleName() + ".bonus", aLong -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                tradeBonusTurn = currentTurn;
                if (tradeBonusTmp != null && !tradeBonusTmp.isEmpty()) {
                    tradeBonus = tradeBonusTmp;
                }
            }
        }
        return tradeBonus != null ? tradeBonus.getOrDefault(color, 0) : 0;
    }

//    public void updateSheets() throws GeneralSecurityException, IOException {
//        Map<ResourceType, Map<Integer, LongAdder>> sold = new EnumMap<>(ResourceType.class);
//        Map<ResourceType, Map<Integer, LongAdder>> bought = new EnumMap<>(ResourceType.class);
//
//        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(7).toEpochSecond() * 1000L;
//
//        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
//        for (Offer offer : db.getOffers(cutoffMs)) {
//            // Ignore outliers
//            int ppu = offer.getPpu();
//            if (offer.getResource() != ResourceType.CREDITS) {
//                if (ppu <= 1 || ppu >= 10000) continue;
//            } else {
//                if (ppu < 15000000 || ppu >= 30000000) continue;
//                ppu /= 10000;
//            }
//
//            Map<ResourceType, Map<Integer, LongAdder>> map = offer.isBuy() ? sold : bought;
//            Map<Integer, LongAdder> rssMap = map.get(offer.getResource());
//            if (rssMap == null) {
//                rssMap = new HashMap<>();
//                map.put(offer.getResource(), rssMap);
//            }
//            LongAdder cumulative = rssMap.get(ppu);
//            if (cumulative == null) {
//                cumulative = new LongAdder();
//                rssMap.put(ppu, cumulative);
//            }
//            cumulative.add(offer.getAmount());
//        }
//
//        updateSheet(Settings.INSTANCE.Drive.TRADE_VOLUME_SPREADSHEET_BUY, bought);
//        updateSheet(Settings.INSTANCE.Drive.TRADE_VOLUME_SPREADSHEET_SELL, sold);
//    }

    public void updateSheet(String sheetId, Map<ResourceType, Map<Integer, LongAdder>> prices) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(sheetId);
        List<Object> header = new ArrayList<>();
        header.add("PPU");
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.MONEY) {
                header.add(value.name());
            }
        }
        sheet.setHeader(header);

        for (int i = 30; i < 5000; i++) {
            header.set(0, Integer.toString(i));
            for (int j = 1; j < ResourceType.values.length; j++) {
                ResourceType value = ResourceType.values[j];
                Map<Integer, LongAdder> soldByType = prices.get(value);
                if (soldByType == null) {
                    header.set(j, null);
                    continue;
                }
                LongAdder amt = soldByType.get(i);
                Object previous = header.get(j);
                if (!(previous instanceof Long)) {
                    previous = 0L;
                }
                if (amt == null) {
                    header.set(j, previous);
                    continue;
                }
                header.set(j, (Long) previous + amt.longValue());
            }

            sheet.addRow(header);
        }

        sheet.set(0, 7);
    }
}