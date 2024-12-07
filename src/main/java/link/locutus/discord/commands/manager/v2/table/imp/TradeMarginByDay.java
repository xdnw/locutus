package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;

public class TradeMarginByDay extends SimpleTable<Void> {
//if (end == null) end = Long.MAX_VALUE;
//List<DBTrade> allOffers = manager.getTradeDb().getTrades(start, end);
//Map<Long, List<DBTrade>> offersByDay = new LinkedHashMap<>();
//
//long minDay = Long.MAX_VALUE;
//long maxDay = TimeUtil.getDay();
//for (DBTrade offer : allOffers) {
//    long turn = TimeUtil.getTurn(offer.getDate());
//    long day = turn / 12;
//    minDay = Math.min(minDay, day);
//    offersByDay.computeIfAbsent(day, f -> new ArrayList<>()).add(offer);
//}
//offersByDay.remove(minDay);
//minDay++;
//
//Map<Long, Map<ResourceType, Double>> marginsByDay = new HashMap<>();
//
//for (Map.Entry<Long, List<DBTrade>> entry : offersByDay.entrySet()) {
//    long day = entry.getKey();
//    List<DBTrade> offers = entry.getValue();
//    Map<ResourceType, Double> dayMargins = new HashMap<>();
//
//    Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> avg = manager.getAverage(offers);
//    Map<ResourceType, Double> lows = avg.getKey();
//    Map<ResourceType, Double> highs = avg.getValue();
//    for (ResourceType type : ResourceType.values) {
//        Double low = lows.get(type);
//        Double high = highs.get(type);
//        if (low != null && high != null) {
//            double margin = high - low;
//            if (percent) margin = 100 * margin / high;
//            dayMargins.put(type, margin);
//        }
//    }
//
//    marginsByDay.put(day, dayMargins);
//}
//
//String title = "Resource margin " + (percent ? " % " : "") + "by day";
//
//List<ResourceType[]> tableTypes = new ArrayList<>();
//

//tableTypes.add(new ResourceType[]{ResourceType.FOOD});
//tableTypes.add(new ResourceType[]{
//        ResourceType.COAL,
//        ResourceType.OIL,
//        ResourceType.URANIUM,
//        ResourceType.LEAD,
//        ResourceType.IRON,
//        ResourceType.BAUXITE,
//});
//tableTypes.add(new ResourceType[]{
//        ResourceType.GASOLINE,
//        ResourceType.MUNITIONS,
//        ResourceType.STEEL,
//        ResourceType.ALUMINUM
//});
//
//for (ResourceType[] types : tableTypes) {
//    double[] buffer = new double[types.length];
//    String[] labels = Arrays.asList(types).stream().map(f -> f.getName()).toArray(String[]::new);
//    TimeNumericTable<Map<ResourceType, Double>> table = new TimeNumericTable<>(title,"day", "ppu", labels) {
//        @Override
//        public void add(long day, Map<ResourceType, Double> cost) {
//            for (int i = 0; i < types.length; i++) {
//                buffer[i] = cost.getOrDefault(types[i], buffer[i]);
//            }
//            add(day, buffer);
//        }
//    };
//
//    for (long day = minDay; day <= maxDay; day++) {
//        long dayOffset = day - minDay;
//        Map<ResourceType, Double> margins = marginsByDay.getOrDefault(day, Collections.emptyMap());
//        table.add(dayOffset, margins);
//    }
//
//
//    table.write(channel, TimeFormat.DAYS_TO_DATE, TableNumberFormat.SI_UNIT, GraphType.LINE, minDay, attachJson, attachCsv);
    private final long start;
    private final long end;
    private final boolean percent;
    private final TradeManager manager;
    private final Map<Long, Map<ResourceType, Double>> marginsByDay = new HashMap<>();
    private long minDay;
    private long maxDay;

    public TradeMarginByDay(@Timestamp long start, @Default @Timestamp Long end,
                            @Arg("Use the margin percent instead of absolute difference")
                            @Default("true") boolean percent) {
        this.start = start;
        this.end = (end == null) ? Long.MAX_VALUE : end;
        this.percent = percent;
        this.manager = manager;
        setTitle("Resource margin " + (percent ? " % " : "") + "by day");
        setLabelX("day");
        setLabelY("ppu");
        writeData();
    }

    @Override
    protected SimpleTable writeData() {
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
    public void add(long day, Map<ResourceType, Double> cost) {
    }
}