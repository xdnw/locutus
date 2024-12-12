package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RssTradeByDay extends SimpleTable<Map.Entry<Long, Long>> {

    private final long minDay;
    private final long maxDay;
    private final Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> volumeByDay;
    private final ResourceType type;

    public static Map<Long, List<DBTrade>> getOffersByDay(Set<ResourceType> resources, long min, long max) {
        List<DBTrade> allOffers;
        if (resources.size() == ResourceType.values.length) {
            allOffers = Locutus.imp().getTradeManager().getTradeDb().getTrades(min, max);
        } else {
            allOffers = Locutus.imp().getTradeManager().getTradeDb().getTradesByResources(
            resources.stream().filter(f -> f != ResourceType.MONEY && f != ResourceType.CREDITS)
                    .collect(Collectors.toSet()), min, max);
        }
        Map<Long, List<DBTrade>> offersByDay = new LinkedHashMap<>();

        long minDay = Long.MAX_VALUE;
        for (DBTrade offer : allOffers) {
            if (offer.getDate() > max) continue;
            long turn = TimeUtil.getTurn(offer.getDate());
            long day = turn / 12;
            minDay = Math.min(minDay, day);
            offersByDay.computeIfAbsent(day, f -> new ArrayList<>()).add(offer);
        }
        offersByDay.remove(minDay);
        return offersByDay;
    }

    public static Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> getVolumeByDay(Set<ResourceType> resources, Function<Collection<DBTrade>, long[]> rssFunction, long start, long end) {
        TradeManager manager = Locutus.imp().getTradeManager();
        Map<Long, List<DBTrade>> tradesByDay = getOffersByDay(resources, start, end);

        Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> volumeByDay = new HashMap<>();

        for (Map.Entry<Long, List<DBTrade>> entry : tradesByDay.entrySet()) {
            Long day = entry.getKey();
            Collection<DBTrade> offers = entry.getValue();
            offers = manager.filterOutliers(offers);
            Collection<DBTrade> lows = manager.getLow(offers);
            Collection<DBTrade> highs = manager.getHigh(offers);

            long[] volumesLow = rssFunction.apply(lows);
            long[] volumesHigh = rssFunction.apply(highs);

            Map<ResourceType, Map.Entry<Long, Long>> volumeMap = volumeByDay.computeIfAbsent(day, f -> new HashMap<>());
            for (ResourceType type : ResourceType.values) {
                long low = volumesLow[type.ordinal()];
                long high = volumesHigh[type.ordinal()];
                Map.Entry<Long, Long> lowHigh = new AbstractMap.SimpleEntry<>(low, high);
                volumeMap.put(type, lowHigh);
            }
        }
        return volumeByDay;
    }

    public RssTradeByDay(String title, Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> volumeByDay, ResourceType type) {
        this.volumeByDay = volumeByDay;
        this.type = type;
        if (type == ResourceType.MONEY || type == ResourceType.CREDITS) {
            throw new IllegalArgumentException("Cannot graph money or credits");
        }

        this.minDay = Collections.min(volumeByDay.keySet());
        this.maxDay = Collections.max(volumeByDay.keySet());

        title = type + " " + title;
        setTitle(title);
        setTitle(title);
        setLabelX("day");
        setLabelY("volumne");
        setLabels(new String[]{"low", "high"});

        writeData();
    }

    @Override
    protected SimpleTable<Map.Entry<Long, Long>> writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            Map<ResourceType, Map.Entry<Long, Long>> volume = volumeByDay.get(day);
            if (volume == null) volume = Collections.emptyMap();

            Map.Entry<Long, Long> rssVolume = volume.get(type);
            if (rssVolume != null) {
                add(day, rssVolume);
            }
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public long getOrigin() {
        return minDay;
    }

    @Override
    public void add(long day, Map.Entry<Long, Long> rssVolume) {
        add(day - minDay, rssVolume.getKey(), rssVolume.getValue());
    }
}
