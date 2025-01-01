package link.locutus.discord.util.task.multi;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.network.IProxy;
import link.locutus.discord.network.ProxyHandler;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiResult {
    private final Map<Integer, NetworkRow> network;
    private final List<SameNetworkTrade> trade;
    private final int nationId;
    private long dateFetched = 0;

    public MultiResult(int nationId) {
        this.network = new Int2ObjectOpenHashMap<>();
        this.trade = new ObjectArrayList<>();
        this.nationId = nationId;
    }

    public MultiResult(int nationId, Map<Integer, NetworkRow> network, List<SameNetworkTrade> trade) {
        this.network = network;
        this.trade = trade;
        this.nationId = nationId;
    }

    public MultiResult setDateFetched(long dateFetched) {
        this.dateFetched = dateFetched;
        return this;
    }

    public Map<Integer, NetworkRow> getNetwork() {
        return network;
    }

    public List<SameNetworkTrade> getTrade() {
        return trade;
    }

    public MultiResult updateIfOutdated(Auth auth, long timeout, boolean allowDeleted) {
        if (!allowDeleted && DBNation.getById(nationId) == null) {
            return this;
        }
        long now = System.currentTimeMillis();
        if (System.currentTimeMillis() - dateFetched > timeout) {
            try {
                update(auth);
            } catch (IOException | ParseException e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public MultiResult update(Auth auth) throws IOException, ParseException {
        dateFetched = System.currentTimeMillis();

        PW.withLogin(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String url = "https://politicsandwar.com/index.php?id=178&nation_id=" + nationId;
                String html = auth.readStringFromURL(PagePriority.ACTIVE_PAGE, url, null, false);

                Document doc = Jsoup.parse(html);
                Elements tables = doc.select(".nationtable");
                Element networkTable = tables.get(1);

                Elements netRows = networkTable.select("tr");
                for (int i = 1; i < netRows.size(); i++) {
                    Element row = netRows.get(i);
                    Elements cols = row.select("td");
                    if (cols.size() != 8) continue;

                    long lastAccessFromSharedIP = parseDate(cols.get(1).text());
                    int numberOfSharedIPs = Integer.parseInt(cols.get(2).text());
                    long lastActiveMs = parseDate(cols.get(3).text());
                    int otherId = Integer.parseInt(cols.get(0).text());
                    int allianceId = parseIdFromLink(cols.get(6));
                    long dateCreated = parseDate(cols.get(7).text());

                    NetworkRow networkRow = new NetworkRow(otherId, lastAccessFromSharedIP, numberOfSharedIPs, lastActiveMs, allianceId, dateCreated);
                    NetworkRow previousNetwork = getNetwork().get(otherId);
                    if (previousNetwork != null) {
                        if (networkRow.lastAccessFromSharedIP != -1) {
                            previousNetwork.lastAccessFromSharedIP = networkRow.lastAccessFromSharedIP;
                        }
                        if (networkRow.numberOfSharedIPs != -1) {
                            previousNetwork.numberOfSharedIPs = networkRow.numberOfSharedIPs;
                        }
                        if (networkRow.lastActiveMs != -1) {
                            previousNetwork.lastActiveMs = networkRow.lastActiveMs;
                        }
                        if (networkRow.allianceId != -1) {
                            previousNetwork.allianceId = networkRow.allianceId;
                        }
                        if (networkRow.dateCreated != -1) {
                            previousNetwork.dateCreated = networkRow.dateCreated;
                        }
                    } else {
                        previousNetwork = networkRow;
                    }
                    getNetwork().put(otherId, networkRow);
                }

                Element sameNetworkTradesTable = tables.get(3);
                parseTrades(sameNetworkTradesTable, getTrade());
                return null;
            }
        }, auth);
        save();
        return this;
    }

    private void save() {
        Locutus lc = Locutus.imp();
        if (lc == null) return;
        DiscordDB db = lc.getDiscordDB();
        if (db == null) return;
        db.addMultiReportLastUpdated(nationId, dateFetched);
        List<NetworkRow> networkRows = new ObjectArrayList<>(getNetwork().values());
        saveNetwork(db, networkRows);
        saveTrades(db, getTrade());
    }

    private void saveTrades(DiscordDB db, List<SameNetworkTrade> trades) {
        if (db == null || trades.isEmpty()) return;
        if (!trades.isEmpty()) db.addSameNetworkTrades(trades);
        List<SameNetworkTrade> inverse = new ObjectArrayList<>();
        // swap buying and selling nations
        for (SameNetworkTrade trade : trades) {
            inverse.add(new SameNetworkTrade(trade.buyingNation, trade.sellingNation, trade.dateOffered, trade.resource, trade.amount, trade.ppu));
        }
        db.addSameNetworkTrades(inverse);
    }

    private void saveNetwork(DiscordDB db, List<NetworkRow> networkRows) {
        if (db == null || networkRows.isEmpty()) return;
        db.addNetworks(this.nationId, networkRows);
        List<NetworkRow> inverse = new ObjectArrayList<>();
        DBNation nation = DBNation.getById(nationId);
        if (nation == null) return;
        int aaId = nation.getAlliance_id();
        long lastActiveMs = nation.lastActiveMs();
        long dateCreated = nation.getDate();
        for (NetworkRow row : networkRows) {
            inverse.add(new NetworkRow(nationId, row.lastAccessFromSharedIP, row.numberOfSharedIPs, lastActiveMs, aaId, dateCreated));
        }
//        db.addNetworks(inverse);
    }

    private static int parseIdFromLink(Element element) {
        String href = element.select("a").attr("href");
        if (href == null || href.isEmpty()) {
            String text = element.text();
            if (text.equals("None")) {
                return 0;
            }
            return -1;
        }
        String[] parts = href.split("=");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    private static long parseDate(String dateStr) throws ParseException {
        if (dateStr.equalsIgnoreCase("N/A")) return 0;
        return TimeUtil.YYYY_MM_DD_HH_MM_SS_A.parse(dateStr).getTime();
    }

    private static void parseTrades(Element table, List<SameNetworkTrade> trades) throws ParseException {
        Set<SameNetworkTrade> tradeSet = new ObjectLinkedOpenHashSet<>(trades);
        Elements rows = table.select("tr");
        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            Elements cols = row.select("td");
            if (cols.size() != 6) continue;

            int sellingNation = parseIdFromLink(cols.get(0));
            int buyingNation = parseIdFromLink(cols.get(1));
            long dateOffered = TimeUtil.MMDDYYYY_HH_MM_A.parse(cols.get(2).text()).getTime();
            ResourceType resource = parseResourceType(cols.get(3).select("img").attr("src"));
            int amount = Integer.parseInt(cols.get(3).text().replaceAll("[^\\d]", ""));
            String ppuText = cols.get(4).text();
            Matcher matcher = Pattern.compile("\\((\\d+) ea\\)").matcher(ppuText);
            int ppu = matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;

            SameNetworkTrade trade = new SameNetworkTrade(sellingNation, buyingNation, dateOffered, resource, amount, ppu);
            tradeSet.add(trade);
        }
        trades.clear();
        trades.addAll(tradeSet);
    }

    private static ResourceType parseResourceType(String src) {
        String[] parts = src.split("[\\./]");
        String resourceName = parts[parts.length - 2];
        return ResourceType.parse(resourceName);
    }

    public static class NetworkRow {
        public int id;
        public long lastAccessFromSharedIP;
        public int numberOfSharedIPs;
        public long lastActiveMs;
        public int allianceId;
        public long dateCreated;

        public NetworkRow(int id, long lastAccessFromSharedIP, int numberOfSharedIPs, long lastActiveMs, int allianceId, long dateCreated) {
            this.id = id;
            this.lastAccessFromSharedIP = lastAccessFromSharedIP;
            this.numberOfSharedIPs = numberOfSharedIPs;
            this.lastActiveMs = lastActiveMs;
            this.allianceId = allianceId;
            this.dateCreated = dateCreated;
        }
    }

    public static class SameNetworkTrade {
        public int sellingNation;
        public int buyingNation;
        public long dateOffered;
        public ResourceType resource;
        public int amount;
        public int ppu;

        public SameNetworkTrade(int sellingNation, int buyingNation, long dateOffered, ResourceType resource, int amount, int ppu) {
            this.sellingNation = sellingNation;
            this.buyingNation = buyingNation;
            this.dateOffered = dateOffered;
            this.resource = resource;
            this.amount = amount;
            this.ppu = ppu;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SameNetworkTrade that = (SameNetworkTrade) o;

            if (sellingNation != that.sellingNation) return false;
            if (buyingNation != that.buyingNation) return false;
            if (dateOffered != that.dateOffered) return false;
            if (amount != that.amount) return false;
            if (ppu != that.ppu) return false;
            return resource == that.resource;
        }

        @Override
        public int hashCode() {
            int result = sellingNation;
            result = 31 * result + buyingNation;
            result = 31 * result + (int) (dateOffered ^ (dateOffered >>> 32));
            result = 31 * result + (resource != null ? resource.hashCode() : 0);
            result = 31 * result + amount;
            result = 31 * result + ppu;
            return result;
        }
    }
}
