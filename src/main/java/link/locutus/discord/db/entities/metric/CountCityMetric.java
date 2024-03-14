package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.column.NumberColumn;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CountCityMetric implements IAllianceMetric {
    private final Function<DBCity, Double> countCity;
    private final AllianceMetricMode mode;
    private final Predicate<DBNation> filter;
    private final Function<CityHeader, NumberColumn<DBCity, Number>> getHeader;

    public CountCityMetric(Function<DBCity, Double> countCity) {
        this(countCity, null, AllianceMetricMode.TOTAL);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, AllianceMetricMode mode) {
        this(countCity, null, mode);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<CityHeader, ? extends NumberColumn<DBCity, ? extends Number>> getHeader) {
        this(countCity, getHeader, AllianceMetricMode.TOTAL, f -> true);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<CityHeader, ? extends NumberColumn<DBCity, ? extends Number>> getHeader, AllianceMetricMode mode) {
        this(countCity, getHeader, mode, f -> true);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<CityHeader, ? extends NumberColumn<DBCity, ? extends Number>> getHeader, AllianceMetricMode mode, Predicate<DBNation> filter) {
        this.countCity = countCity;
        this.getHeader = (Function<CityHeader, NumberColumn<DBCity, Number>>) getHeader;
        this.mode = mode;
        this.filter = filter;
    }

    @Override
    public Double apply(DBAlliance alliance) {
        double total = 0;
        int nations = 0;
        int cities = 0;
        for (DBNation nation : alliance.getMemberDBNations()) {
            if (filter != null && !filter.test(nation)) continue;
            nations++;
            for (DBCity city : nation._getCitiesV3().values()) {
                cities++;
                total += countCity.apply(city);
            }
        }
        if (total == 0) return 0d;
        return switch (mode) {
            case TOTAL -> total;
            case PER_NATION -> total / nations;
            case PER_CITY -> total / cities;
        };
    }

    private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
    private final Map<Integer, Double> countByAA = new Int2DoubleOpenHashMap();
    private final Map<Integer, Integer> countModeByAA = new Int2IntOpenHashMap();
    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new BiConsumer<Long, NationHeader>() {
            @Override
            public void accept(Long day, NationHeader header) {
                Rank position = header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vmTurns = header.vm_turns.get();
                if (vmTurns == null || vmTurns > 0) return;
                int nationId = header.nation_id.get();
                allianceByNationId.put(nationId, allianceId);
                switch (mode) {
                    case PER_NATION:
                        countModeByAA.merge(allianceId, 1, Integer::sum);
                        break;
                    case PER_CITY:
                        int cities = header.cities.get();
                        countModeByAA.merge(allianceId, cities, Integer::sum);
                        break;
                    case TOTAL:
                        countModeByAA.put(allianceId, 1);
                }
            }
        });

        importer.setCityReader(metric, new BiConsumer<Long, CityHeader>() {
            @Override
            public void accept(Long day, CityHeader header) {
                int nationId = header.nation_id.get();
                Integer allianceId = allianceByNationId.get(nationId);
                if (allianceId == null) return;
                double value;
                if (getHeader == null) {
                    DBCity city = header.getCity();
                    value = countCity.apply(city);
                } else {
                    value = getHeader.apply(header).get().doubleValue();
                }
                countByAA.merge(allianceId, value, Double::sum);
            }
        });

    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        Map<Integer, Double> result = countByAA.entrySet().stream().filter(f -> countModeByAA.containsKey(f.getKey())).collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (double) countModeByAA.get(f.getKey())));
        countByAA.clear();
        countModeByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
