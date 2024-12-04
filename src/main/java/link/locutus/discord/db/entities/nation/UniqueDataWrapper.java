package link.locutus.discord.db.entities.nation;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBCity;

import java.util.Map;
import java.util.function.Function;

public class UniqueDataWrapper<T extends DataHeader> extends DataWrapper<T> {

    private Map<Integer, DBCity> cities = new Int2ObjectOpenHashMap<>();

    public UniqueDataWrapper(long date, T header) {
        super(date, header);
    }

    public Map<Integer, DBCity> getCities() {
        return cities;
    }

    @Override
    public Function<Integer, Map<Integer, DBCity>> getGetCities() {
        return i -> cities;
    }

    @Override
    public <T, V> V get(ColumnInfo<T, V> get, int offset) {
        return get.get();
    }
}
