package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.db.entities.DBCity;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CitiesFile extends DataFile<DBCity, CityHeader> {
    public CitiesFile(File file) {
        super(file, new CityHeader(parseDateFromFile(file.getName())));
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
}
