package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBCity;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public class LocalDataWrapper<T extends DataHeader> extends DataWrapper<T> {

    public LocalDataWrapper(long date, T header) {
        super(date, header);
    }

    @Override
    public Function<Integer, Map<Integer, DBCity>> getGetCities() {
        return null;
    }

    @Override
    public <T, V> V get(ColumnInfo<T, V> get, int offset) {
        return get.get();
    }
}
