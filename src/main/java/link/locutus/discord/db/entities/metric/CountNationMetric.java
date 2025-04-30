package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.column.NumberColumn;
import link.locutus.discord.apiv3.csv.header.CityHeaderReader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CountNationMetric implements IAllianceMetric {
    private final Function<DBNation, Number> countNation;
    private final AllianceMetricMode mode;
    private final Predicate<DBNation> filter;
    private final Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader;
    private Predicate<Integer> allianceFilter;
    private boolean includeCities;
    private Runnable finalizeTask;
    private boolean includeVM;
    private boolean includeApplicants;


    public CountNationMetric(Function<DBNation, Number> countNation, AllianceMetricMode mode) {
        this(countNation, null, mode);
    }

    public CountNationMetric(Function<DBNation, Number> countNation) {
        this(countNation, null, AllianceMetricMode.TOTAL);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader) {
        this(countNation, getHeader, AllianceMetricMode.TOTAL, f -> true);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader, AllianceMetricMode mode) {
        this(countNation, getHeader, mode, f -> true);
    }

    public CountNationMetric(Function<DBNation, Number> countNation, Function<NationHeader, ? extends NumberColumn<DBNation, ? extends Number>> getHeader, AllianceMetricMode mode, Predicate<DBNation> filter) {
        this.countNation = countNation;
        this.getHeader = getHeader;
        this.mode = mode;
        this.filter = filter;
    }

    public CountNationMetric allianceFilter(Predicate<Integer> filter) {
        this.allianceFilter = filter;
        return this;
    }

    public CountNationMetric includeCities() {
        this.includeCities = true;
        return this;
    }

    @Override
    public Double apply(DBAlliance alliance) {
        if (allianceFilter != null && !allianceFilter.test(alliance.getId())) return null;
        double total = 0;
        int nations = 0;
        int cities = 0;
        for (DBNation nation : alliance.getMemberDBNations()) {
            if (filter != null && !filter.test(nation)) continue;
            nations++;
            cities += nation.getCities();
            total += this.countNation.apply(nation).doubleValue();
        }
        if (total == 0) return 0d;
        return switch (mode) {
            case TOTAL -> total;
            case PER_NATION -> total / nations;
            case PER_CITY -> total / cities;
        };
    }

    private final Map<Integer, Double> countByAA = new Int2DoubleOpenHashMap();
    private final Map<Integer, Integer> countAllianceMetricModeByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        if (includeCities) {
            Map<Integer, DBNationSnapshot> nationMap = new Int2ObjectOpenHashMap<>();
            importer.setNationReader(metric, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    if (!includeApplicants) {
                        Rank position = r.header.alliance_position.get();
                        if (position.id <= Rank.APPLICANT.id) return;
                    }
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    if (allianceFilter != null && !allianceFilter.test(allianceId)) return;
                    if (!includeVM) {
                        Integer vm_turns = r.header.vm_turns.get();
                        if (vm_turns == null || vm_turns > 0) return;
                    }
                    DBNationSnapshot nation = r.getNation(includeVM, true);
                    if (nation != null) {
                        nationMap.put(r.header.nation_id.get(), nation);
                    }
                }
            });

            importer.setCityReader(metric, new BiConsumer<Long, CityHeaderReader>() {
                @Override
                public void accept(Long day, CityHeaderReader r) {
                    int nationId = r.header.nation_id.get();
                    DBNationSnapshot nation = nationMap.get(nationId);
                    if (nation == null) return;
                    DBCity city = r.getCity();
                    nation.addCity(city);
                }
            });

            finalizeTask = new Runnable() {
                @Override
                public void run() {
                    for (Map.Entry<Integer, DBNationSnapshot> entry : nationMap.entrySet()) {
                        DBNationSnapshot nation = entry.getValue();
                        if (filter != null && !filter.test(nation)) continue;
                        double amt = countNation.apply(nation).doubleValue();
                        countByAA.merge(nation.getAlliance_id(), amt, Double::sum);
                        switch (mode) {
                            case PER_NATION:
                                countAllianceMetricModeByAA.merge(nation.getAlliance_id(), 1, Integer::sum);
                                break;
                            case PER_CITY:
                                countAllianceMetricModeByAA.merge(nation.getAlliance_id(), nation.getCities(), Integer::sum);
                                break;
                            case TOTAL:
                                countAllianceMetricModeByAA.put(nation.getAlliance_id(), 1);
                        }
                    }

                    nationMap.clear();
                }
            };
        } else {
            importer.setNationReader(metric, new BiConsumer<Long, NationHeaderReader>() {
                @Override
                public void accept(Long day, NationHeaderReader r) {
                    if (!includeApplicants) {
                        Rank position = r.header.alliance_position.get();
                        if (position.id <= Rank.APPLICANT.id) return;
                    }
                    int allianceId = r.header.alliance_id.get();
                    if (allianceId == 0) return;
                    if (allianceFilter != null && !allianceFilter.test(allianceId)) return;
                    if (!includeVM) {
                        Integer vm_turns = r.header.vm_turns.get();
                        if (vm_turns == null || vm_turns > 0) return;
                    }
                    double amt;
                    if (getHeader != null) {
                        amt = getHeader.apply(r.header).get().doubleValue();
                    } else {
                        DBNation nation = r.getNation(includeVM, true);
                        if (filter != null && !filter.test(nation)) return;
                        amt = countNation.apply(nation).doubleValue();
                    }
                    countByAA.merge(allianceId, amt, Double::sum);
                    switch (mode) {
                        case PER_NATION:
                            countAllianceMetricModeByAA.merge(allianceId, 1, Integer::sum);
                            break;
                        case PER_CITY:
                            int cities = r.header.cities.get();
                            countAllianceMetricModeByAA.merge(allianceId, cities, Integer::sum);
                            break;
                        case TOTAL:
                            countAllianceMetricModeByAA.put(allianceId, 1);
                    }
                }
            });
        }

    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        if (finalizeTask != null) finalizeTask.run();
        Map<Integer, Double> result = countByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (double) countAllianceMetricModeByAA.get(f.getKey())));
        countByAA.clear();
        countAllianceMetricModeByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }

    public void includeVM() {
        this.includeVM = true;
    }

    public void includeApplicants() {
        this.includeApplicants = true;
    }
}
