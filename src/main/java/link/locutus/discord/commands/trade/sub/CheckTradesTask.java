package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.Offer;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.ResourceType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class CheckTradesTask implements Callable<Boolean> {
    private final ResourceType resource;
    private final TradeDB db;
    private final TradeManager manager;
    private final TradeAlertConsumer consumer;

    public CheckTradesTask(ResourceType resource, TradeAlertConsumer consumer) {
        this.resource = resource;
        this.manager = Locutus.imp().getTradeManager();
        this.db = manager.getTradeDb();
        this.consumer = consumer;
    }

    public static List<Offer> createOffers(Element table, boolean isBuy, ResourceType type) throws ParseException {
        List<Offer> offers = new ArrayList<>();

        Elements offerElems = table.getElementsByTag("tr");
        for (Element elem : offerElems) {
            Elements cells = elem.getElementsByTag("td");
            if (cells.size() != 3) continue;
            String dateStr = cells.get(0).text();
            long date;
            try {
                date = TimeUtil.parseDate(TimeUtil.MMDDYYYY_HH_MM_A, dateStr);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                date = System.currentTimeMillis();
            }
            int amt = MathMan.parseInt(cells.get(1).text());
            int ppu = MathMan.parseInt(cells.get(2).text());

            offers.add(new Offer(-1, -1, type, isBuy, amt, ppu, 0, date));
        }
        return offers;
    }

    @Override
    public Boolean call() throws Exception {
        Map<String, String> post = new HashMap<>();
        post.put("offer_resource", resource.name().toLowerCase());
        String url = "https://politicsandwar.com/api/toptradeoffers.php";

        String html = FileUtil.readStringFromURL(url, post); // , auth.getCookieManager()
        Document dom = Jsoup.parse(html);
        Elements tables = dom.getElementsByClass("nationtable");
        Element highTable = tables.get(0);
        Element lowTable = tables.get(1);

        List<Offer> highOffers = createOffers(highTable, false, resource);
        List<Offer> lowOffers = createOffers(lowTable, true, resource);


        Offer high = highOffers.isEmpty() ? null : highOffers.get(0);
        Offer low = lowOffers.isEmpty() ? null : lowOffers.get(0);

        TradeAlert alert = new TradeAlert(resource, low, high);

        if (low != null && high != null) {
            if (alert.getCurrentHigh() == alert.getPreviousHigh() && alert.getCurrentLow() == alert.getPreviousLow()) return true;

            if (alert.getCurrentHigh() < alert.getCurrentLow()) {
                if (high.getAmount() != 1 && low.getAmount() != 1) {
                    consumer.accept(null, TradeDB.TradeAlertType.MIXUP, alert, false);
                }
            } else {
                Set<Long> pingsAboveHigh = db.getSubscriptions(resource, true, true, alert.getCurrentHigh() - 1, TradeDB.TradeAlertType.ABSOLUTE);
                Set<Long> pingsBelowHigh = db.getSubscriptions(resource, true, false, alert.getCurrentHigh() + 1, TradeDB.TradeAlertType.ABSOLUTE);
                Set<Long> pingsAboveLow = db.getSubscriptions(resource, false, true, alert.getCurrentLow() - 1, TradeDB.TradeAlertType.ABSOLUTE);
                Set<Long> pingsBelowLow = db.getSubscriptions(resource, false, false, alert.getCurrentLow() + 1, TradeDB.TradeAlertType.ABSOLUTE);

                Set<Long> allPings = new HashSet<>();
                allPings.addAll(pingsAboveHigh);
                allPings.addAll(pingsBelowHigh);
                allPings.addAll(pingsAboveLow);
                allPings.addAll(pingsBelowLow);

                if (!allPings.isEmpty()) {
                    consumer.accept(allPings, TradeDB.TradeAlertType.ABSOLUTE, alert, false);
                }
                if (alert.getCurrentHigh() < alert.getPreviousHigh() &&
                        alert.getPreviousHighNation() != null &&
                        alert.getCurrentHighNation() != null &&
                        alert.getCurrentHighNation().getAlliance_id() != alert.getPreviousHighNation().getAlliance_id()) {
                    DBNation prevNation = alert.getPreviousHighNation();
                    PNWUser pnwUser = Locutus.imp().getDiscordDB().getUserFromNationId(prevNation.getNation_id());
                    if (pnwUser != null && pnwUser.getDiscordId() != null) {
                        consumer.accept(Collections.singleton(pnwUser.getDiscordId()), TradeDB.TradeAlertType.UNDERCUT, alert, true);
                    }
                }
            }
            int disparity = alert.getCurrentHigh() - alert.getCurrentLow();
            double disparityPct = (disparity * 100d) / alert.getCurrentHigh();
            if (disparityPct > 15 && alert.getCurrentHigh() > alert.getPreviousHigh() && resource != ResourceType.CREDITS) {
//                Set<Long> pings = db.getSubscriptions(resource, true, true, disparity, TradeDB.TradeAlertType.DISPARITY);
//                consumer.accept(null, TradeDB.TradeAlertType.DISPARITY, alert, false);
            }
        } else if (low == null) {
            consumer.accept(null, TradeDB.TradeAlertType.NO_HIGH, alert, false);
        } else if (high == null) {
            consumer.accept(null, TradeDB.TradeAlertType.NO_LOW, alert, false);
        }
        return true;
    }
}
