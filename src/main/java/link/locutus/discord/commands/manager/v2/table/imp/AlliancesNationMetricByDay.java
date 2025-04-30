package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AlliancesNationMetricByDay extends SimpleTable<Void> {
    private final Map<Long, double[]> valuesByDay;
    private final long minDay;
    private final long maxDay;
    private final @NotNull List<Integer> allianceIdsSorted;

    public static Set<DBAlliance> resolveAlliances(Set<DBAlliance> alliances) {
        if (alliances == null) alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, 15);
        if (alliances.size() > 100) {
            alliances.removeIf(f -> f.getNations(true, 10080, true).isEmpty());
        }
        if (alliances.isEmpty()) throw new IllegalArgumentException("No alliances found");
        return alliances;
    }

    public static Map<Long, double[]> generateData(Consumer<Long> progressMessage, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Arg("The alliances to include. Defaults to top 15") @Default Set<DBAlliance> alliances, @Default Predicate<DBNation> filter, @Switch("a") boolean includeApps) throws IOException, ParseException {
        alliances = resolveAlliances(alliances);
        List<Integer> allianceIdsSorted = alliances.stream().map(DBAlliance::getAlliance_id).sorted().collect(Collectors.toList());
        Set<Integer> allianceIdsSet = new IntOpenHashSet(allianceIdsSorted);
        Predicate<DBNation> filterFinal = filter == null ? f -> allianceIdsSet.contains(f.getAlliance_id()) : f -> allianceIdsSet.contains(f.getAlliance_id()) && filter.test(f);

        Map<Long, double[]> valuesByDay = new Long2ObjectLinkedOpenHashMap<>();

        double[] buffer = new double[alliances.size()];
        int[] counts = new int[alliances.size()];

        Map<Integer, Integer> allianceIndex = new Int2IntOpenHashMap();
        for (int i = 0; i < allianceIdsSorted.size(); i++) {
            int aaId = allianceIdsSorted.get(i);
            allianceIndex.put(aaId, i);
        }
        AtomicLong timer = new AtomicLong(System.currentTimeMillis());
        long dayStart = TimeUtil.getDay(start);
        long dayEnd = Math.min(TimeUtil.getDay(), TimeUtil.getDay(end + TimeUnit.HOURS.toDays(23)));
        DataDumpParser parser = Locutus.imp().getDataDumper(true).load();

        for (long day : parser.getDays(true, true)) {
            Arrays.fill(buffer, 0);
            Arrays.fill(counts, 0);

            if (day < dayStart || day > dayEnd) continue;
            Map<Integer, DBNationSnapshot> nations = parser.getNations(day);
            for (Map.Entry<Integer, DBNationSnapshot> entry : nations.entrySet()) {
                DBNationSnapshot nation = entry.getValue();
                if (!filterFinal.test(nation)) continue;
                int allianceId = nation.getAlliance_id();
                if (allianceId == 0 || !allianceIdsSet.contains(allianceId)) continue;
                if (!includeApps && nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;

                double value = metric.apply(nation);
                buffer[allianceIndex.get(allianceId)] = value;

                switch (mode) {
                    case PER_NATION:
                        counts[allianceIndex.get(allianceId)]++;
                        break;
                    case PER_CITY:
                        counts[allianceIndex.get(allianceId)] += nation.getCities();
                        break;
                }
            }
            switch (mode) {
                case PER_CITY:
                case PER_NATION:
                    for (int i = 0; i < buffer.length; i++) {
                        buffer[i] = buffer[i] / counts[i];
                    }
                    break;
            }

            valuesByDay.put(day, buffer.clone());

            if (System.currentTimeMillis() - timer.get() > 10000) {
                timer.getAndSet(System.currentTimeMillis());
                if (progressMessage != null) progressMessage.accept(day);
            }
        }
        return valuesByDay;
    }

    public static AlliancesNationMetricByDay create(TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Arg("The alliances to include. Defaults to top 15") @Default Set<DBAlliance> alliances, @Default Predicate<DBNation> filter, @Switch("a") boolean includeApps) throws IOException, ParseException {
        alliances = resolveAlliances(alliances);
        return new AlliancesNationMetricByDay(generateData(null, metric, start, end, mode, alliances, filter, includeApps), metric, start, end, alliances);
    }

    public AlliancesNationMetricByDay(Map<Long, double[]> valuesByDay, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, @Arg("The alliances to include. Defaults to top 15") @Default Set<DBAlliance> alliances) throws IOException, ParseException {
        alliances = resolveAlliances(alliances);
        this.valuesByDay = valuesByDay;
        this.allianceIdsSorted = alliances.stream().map(DBAlliance::getAlliance_id).sorted().collect(Collectors.toList());
        String[] labels = allianceIdsSorted.stream().map(f -> PW.getName(f, true)).toArray(String[]::new);
        this.minDay = TimeUtil.getDay(start);
        this.maxDay = Math.min(TimeUtil.getDay(), TimeUtil.getDay(end + TimeUnit.HOURS.toDays(23)));

        setTitle(metric.getName() + " by day");
        setLabelX("day");
        setLabelY(metric.getName());
        setLabels(labels);

        writeData();
    }

    @Override
    public long getOrigin() {
        return minDay;
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (long day = minDay; day <= maxDay; day++) {
            add(day, (Void) null);
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
    public void add(long day, Void cost) {
        double[] values = valuesByDay.get(day);
        if (values == null) {
            values = new double[allianceIdsSorted.size()];
        }
        add(day - minDay, values);
    }
}
