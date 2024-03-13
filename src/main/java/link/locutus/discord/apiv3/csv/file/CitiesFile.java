package link.locutus.discord.apiv3.csv.file;

import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.DBNationSnapshot;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CitiesFile extends DataFile<DBCity, CityHeader> {
    public CitiesFile(File file) {
        super(file, new CityHeader());
    }

    public Map<Integer, Map<Integer, DBCity>> readCities(Predicate<Integer> allowedNationIds, boolean condense) throws IOException {
        Consumer<DBCity> condenseFunc = condense ? DBCity::condense : city -> {};
        Map<Integer, Map<Integer, DBCity>> result = new Int2ObjectOpenHashMap<>();
        this.reader().all().read(header -> {
            int nationId = header.nation_id.get();
            if (allowedNationIds.test(nationId)) {
                DBCity city = header.loadCity();
                condenseFunc.accept(city);
                result.computeIfAbsent(nationId, k -> new Int2ObjectOpenHashMap<>()).put(city.id, city);
            }
        });
        return result;
    }
}
