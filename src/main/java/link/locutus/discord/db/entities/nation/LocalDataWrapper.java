package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.entities.DBCity;

import java.util.Map;
import java.util.function.Function;

public class LocalDataWrapper<T extends DataHeader> extends DataWrapper<T> {
    private final AllianceLookup allianceLookup;

    public LocalDataWrapper(long date, T header) {
        this(date, header, null);
    }

    public LocalDataWrapper(long date, T header, AllianceLookup allianceLookup) {
        super(date, header);
        this.allianceLookup = allianceLookup;
    }

    @Override
    public Function<Integer, Map<Integer, DBCity>> getGetCities() {
        return null;
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
