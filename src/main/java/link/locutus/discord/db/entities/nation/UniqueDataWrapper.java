package link.locutus.discord.db.entities.nation;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.entities.DBCity;

import java.util.Map;
import java.util.function.Function;

public class UniqueDataWrapper<T extends DataHeader> extends DataWrapper<T> {

    private Map<Integer, DBCity> cities = new Int2ObjectOpenHashMap<>();
    private final AllianceLookup allianceLookup;

    public UniqueDataWrapper(long date, T header) {
        this(date, header, null);
    }

    public UniqueDataWrapper(long date, T header, AllianceLookup allianceLookup) {
        super(date, header);
        this.allianceLookup = allianceLookup;
    }

    public Map<Integer, DBCity> getCities() {
        return cities;
    }

    @Override
    public Function<Integer, Map<Integer, DBCity>> getGetCities() {
        return i -> cities;
    }

    @Override
    public AllianceLookup getAllianceLookup() {
        return allianceLookup;
    }

    @Override
    public <T, V> V get(ColumnInfo<T, V> get, int offset) {
        return get.get();
    }
}
