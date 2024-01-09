package link.locutus.discord.db.entities.metric;

import de.siegmar.fastcsv.reader.CsvRow;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.apiv3.ParsedRow;
import link.locutus.discord.util.scheduler.TriConsumer;
import retrofit2.http.HEAD;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

class DataDumpImporter {
    private final DataDumpParser parser;
    private Map<Integer, double[]> revenueCache;

    public DataDumpImporter(DataDumpParser parser) {
        this.parser = parser;
    }

    public DataDumpParser getParser() {
        return parser;
    }

    Map<IAllianceMetric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>> nationReaders = new LinkedHashMap<>();
    Map<IAllianceMetric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>> cityReaders = new LinkedHashMap<>();

    public void setNationReader(IAllianceMetric metric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow> nationReader) {
        this.nationReaders.put(metric, nationReader);
    }

    public void setCityReader(IAllianceMetric metric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow> cityReader) {
        this.cityReaders.put(metric, cityReader);
    }

    public TriConsumer<Long, DataDumpParser.NationHeader, CsvRow> getNationReader() {
        if (nationReaders.isEmpty()) return null;
        ParsedRow parsedRow = new ParsedRow(parser);
        return (day, header, row) -> {
            parsedRow.setRow(row, day);
            for (Map.Entry<IAllianceMetric, TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>> entry : nationReaders.entrySet()) {
                entry.getValue().consume(day, header, parsedRow);
            }
        };
    }

    public TriConsumer<Long, DataDumpParser.CityHeader, CsvRow> getCityReader() {
        if (cityReaders.isEmpty()) return null;
        ParsedRow parsedRow = new ParsedRow(parser);
        return (day, header, row) -> {
            parsedRow.setRow(row, day);
            for (Map.Entry<IAllianceMetric, TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>> entry : cityReaders.entrySet()) {
                entry.getValue().consume(day, header, parsedRow);
            }
        };
    }

    public void setRevenue(Map<Integer, double[]> result) {
        this.revenueCache = result;
    }

    public Map<Integer, double[]> getRevenueCache() {
        return revenueCache;
    }

    public void clear() {
        revenueCache = null;
    }

    public Map<Integer, Double> getRevenueCache(ResourceType resourceType) {
        if (revenueCache == null) return null;
        return revenueCache.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue()[resourceType.ordinal()]));
    }
}
