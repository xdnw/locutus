package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.ThrowingFunction;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class CitiesFile extends DataFile<DBCity, CityHeader> {
    public CitiesFile(File file, Dictionary dict) {
        super(file, new CityHeader(parseDateFromFile(file.getName()), dict));
    }

    public Map<Integer, Map<Integer, DBCity>> readCities(Predicate<Integer> allowedNationIds, boolean condense) throws IOException {
        Consumer<DBCity> condenseFunc = condense ? DBCity::condense : city -> {};
        Map<Integer, Map<Integer, DBCity>> result = new Int2ObjectOpenHashMap<>();
        this.reader().all(false).read(header -> {
            int nationId = header.nation_id.get();
            if (allowedNationIds.test(nationId)) {
                DBCity city = header.getCity();
                condenseFunc.accept(city);
                result.computeIfAbsent(nationId, k -> new Int2ObjectOpenHashMap<>()).put(city.id, city);
            }
        });
        return result;
    }

    private SoftReference<Map<Integer, int[]>> cityIdsCache = new SoftReference<>(null);

    private Map<Integer, DBCity> getCityMap(int[] offsets, byte[] data) {
        if (offsets == null) return Collections.emptyMap();
        Map<Integer, DBCity> result = new Int2ObjectOpenHashMap<>();
        for (int offset : offsets) {
            DBCity city = new DBCity(data, offset);
            result.put(city.id, city);
        }
        return result;
    }

    private Map<Integer, int[]> generateCityIdsCache() throws IOException {
        CityHeader header = getGlobalHeader();
        byte[] data = this.getBytes();
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
        Map<Integer, int[]> cached = cityIdsCache.get();
        if (cached != null) {
            synchronized (this) {
                return getCityMap(cached.get(nationId), getBytes());
            }
        }
        synchronized (this) {
            cached = cityIdsCache.get();
            if (cached != null) return getCityMap(cached.get(nationId), getBytes());
            Map<Integer, int[]> newCache = generateCityIdsCache();
            cityIdsCache = new SoftReference<>(newCache);
            return getCityMap(newCache.get(nationId), getBytes());
        }
    }

    public Function<Integer, Map<Integer, DBCity>> loadCities() {
        return new ThrowingFunction<Integer, Map<Integer, DBCity>>() {
            @Override
            public Map<Integer, DBCity> applyThrows(Integer nationId) throws IOException {
                return getCities(nationId);
            }
        };
    }
}
