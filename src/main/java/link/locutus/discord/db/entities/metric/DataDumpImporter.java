package link.locutus.discord.db.entities.metric;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.header.NationHeader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
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

    Map<IAllianceMetric, BiConsumer<Long, NationHeader>> nationReaders = new LinkedHashMap<>();
    Map<IAllianceMetric, BiConsumer<Long, CityHeader>> cityReaders = new LinkedHashMap<>();

    public void setNationReader(IAllianceMetric metric, BiConsumer<Long, NationHeader> nationReader) {
        this.nationReaders.put(metric, nationReader);
    }

    public void setCityReader(IAllianceMetric metric, BiConsumer<Long, CityHeader> cityReader) {
        this.cityReaders.put(metric, cityReader);
    }

    public BiConsumer<Long, NationHeader> getNationReader() {
        if (nationReaders.isEmpty()) return null;
        return (day, header) -> {
            for (Map.Entry<IAllianceMetric, BiConsumer<Long, NationHeader>> entry : nationReaders.entrySet()) {
                entry.getValue().accept(day, header);
            }
        };
    }

    public BiConsumer<Long, CityHeader> getCityReader() {
        if (cityReaders.isEmpty()) return null;
        return (day, header) -> {
            for (Map.Entry<IAllianceMetric, BiConsumer<Long, CityHeader>> entry : cityReaders.entrySet()) {
                entry.getValue().accept(day, header);
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
