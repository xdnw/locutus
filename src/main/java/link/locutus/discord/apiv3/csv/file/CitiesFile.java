package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.CityHeaderReader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.BytesDBCity;
import link.locutus.discord.db.entities.nation.GlobalDataWrapper;
import link.locutus.discord.util.scheduler.ThrowingFunction;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public class CitiesFile extends DataFile<DBCity, CityHeader, CityHeaderReader> {
    public CitiesFile(File file, Dictionary dict) {
        super(file, parseDateFromFile(file.getName()), () -> new CityHeader(dict), ((header, date) -> new CityHeaderReader(header, date)));
    }

    public record CityCache(Map<Integer, int[]> map, GlobalDataWrapper<CityHeader> wrapper) {
    }

    private SoftReference<CityCache> cityIdsCache = new SoftReference<>(null);

    private Map<Integer, DBCity> getCityMap(int[] offsets, GlobalDataWrapper<CityHeader> wrapper) {
        if (offsets == null) return Collections.emptyMap();
        Map<Integer, DBCity> result = new Int2ObjectOpenHashMap<>();
        for (int offset : offsets) {
            DBCity city = new BytesDBCity(wrapper, offset);
            result.put(city.getId(), city);
        }
        return result;
    }

    private Map<Integer, int[]> generateCityIdsCache(byte[] data) throws IOException {
        CityHeader header = getGlobalHeader();
        Header<DBCity> colInfo = header.readIndexes(data);
        int bytesPerRow = colInfo.bytesPerRow;

        Map<Integer, IntArrayList> offsets = new Int2ObjectOpenHashMap<>();

        for (int i = colInfo.initialOffset; i < data.length; i += bytesPerRow) {
            int nationId = header.nation_id.read(data, i + header.nation_id.getOffset());
            offsets.computeIfAbsent(nationId, k -> new IntArrayList()).add(i);
        }

        Map<Integer, int[]> offsetsArr = new Int2ObjectOpenHashMap<>();
        for (IntArrayList value : offsets.values()) {
            int[] arr = value.toArray((int[]) null);
            offsetsArr.put(value.getInt(0), arr);
        }
        return offsetsArr;
    }

    private Map<Integer, DBCity> getCities(int nationId) throws IOException {
        CityCache cached = cityIdsCache.get();
        if (cached != null) {
            synchronized (this) {
                return getCityMap(cached.map.get(nationId), cached.wrapper);
            }
        }
        synchronized (this) {
            cached = cityIdsCache.get();
            if (cached != null) return getCityMap(cached.map.get(nationId), cached.wrapper);
            byte[] data = this.getBytes();
            Map<Integer, int[]> newCache = generateCityIdsCache(data);
            cached = new CityCache(newCache, new GlobalDataWrapper<>(getDate(), getGlobalHeader(), data, null));
            cityIdsCache = new SoftReference<>(cached);
            return getCityMap(cached.map.get(nationId), cached.wrapper);
        }
    }

    public Function<Integer, Map<Integer, DBCity>> loadCities() {
        return (ThrowingFunction<Integer, Map<Integer, DBCity>>) this::getCities;
    }
}
