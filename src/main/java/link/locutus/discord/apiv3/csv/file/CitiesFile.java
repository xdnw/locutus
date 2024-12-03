package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.db.entities.DBCity;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
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

    private SoftReference<Map<Integer, List<Integer>>> cityIdsCache = new SoftReference<>(null);
    private SoftReference<Map<Integer, DBCity>>

    private List<Integer> getCityIds(int nationId) {
        Map<Integer, List<Integer>> cached = cityIdsCache.get();
        if (cached != null) {
            synchronized (this) {
                return cached.get(nationId);
            }
        }
        synchronized (this) {
            cached = cityIdsCache.get();
            if (cached != null) {
                return cached.get(nationId);
            }
            int[] cityIds = readCityIds();
            synchronized (cached) {
                cached.put(nationId, cityIds);
            }
            return cityIds;
        }

    }

    public Function<Integer, Map<Integer, DBCity>> loadCities() {
        return new Function<Integer, Map<Integer, DBCity>>() {
            @Override
            public Map<Integer, DBCity> apply(Integer nationId) {

            }
        };
    }
}
