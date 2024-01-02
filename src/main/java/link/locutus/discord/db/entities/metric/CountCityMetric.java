package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.apiv3.ParsedRow;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CountCityMetric implements IAllianceMetric {
    private final Function<DBCity, Double> countCity;
    private final AllianceMetricMode mode;
    private final Predicate<DBNation> filter;
    private final Function<DataDumpParser.CityHeader, Integer> getHeader;

    public CountCityMetric(Function<DBCity, Double> countCity) {
        this(countCity, null, AllianceMetricMode.TOTAL);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, AllianceMetricMode mode) {
        this(countCity, null, mode);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<DataDumpParser.CityHeader, Integer> getHeader) {
        this(countCity, getHeader, AllianceMetricMode.TOTAL, f -> true);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<DataDumpParser.CityHeader, Integer> getHeader, AllianceMetricMode mode) {
        this(countCity, getHeader, mode, f -> true);
    }

    public CountCityMetric(Function<DBCity, Double> countCity, Function<DataDumpParser.CityHeader, Integer> getHeader, AllianceMetricMode mode, Predicate<DBNation> filter) {
        this.countCity = countCity;
        this.getHeader = getHeader;
        this.mode = mode;
        this.filter = filter;
    }

    @Override
    public Double apply(DBAlliance alliance) {
        double total = 0;
        int nations = 0;
        int cities = 0;
        for (DBNation nation : alliance.getMemberDBNations()) {
            if (!filter.test(nation)) continue;
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
    public void setupReaders(AllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
            @Override
            public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                int position = row.get(header.alliance_position, Integer::parseInt);
                if (position <= Rank.APPLICANT.id) return;
                int allianceId = row.get(header.alliance_id, Integer::parseInt);
                if (allianceId == 0) return;
                int vmTurns = row.get(header.vm_turns, Integer::parseInt);
                if (vmTurns > 0) return;
                allianceByNationId.put(row.get(header.nation_id, Integer::parseInt), allianceId);
                switch (mode) {
                    case PER_NATION:
                        countModeByAA.merge(allianceId, 1, Integer::sum);
                        break;
                    case PER_CITY:
                        int cities = row.get(header.cities, Integer::parseInt);
                        countModeByAA.merge(allianceId, cities, Integer::sum);
                        break;
                    case TOTAL:
                        countModeByAA.put(allianceId, 1);
                }
            }
        });

        importer.setCityReader(metric, new TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>() {
            @Override
            public void consume(Long aLong, DataDumpParser.CityHeader header, ParsedRow parsedRow) {
                int nationId = parsedRow.get(header.nation_id, Integer::parseInt);
                Integer allianceId = allianceByNationId.get(nationId);
                if (allianceId == null) return;
                double value;
                if (getHeader == null) {
                    DBCity city = parsedRow.getCity(header, nationId);
                    value = countCity.apply(city);
                } else {
                    value = parsedRow.get(getHeader.apply(header), Double::parseDouble);
                }
                countByAA.merge(allianceId, value, Double::sum);
            }
        });

    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        Map<Integer, Double> result = countByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (double) countModeByAA.get(f.getKey())));
        countByAA.clear();
        countModeByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
