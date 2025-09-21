package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBCity;

import java.util.Map;
import java.util.function.Function;

public abstract class DataWrapper<T extends DataHeader> {
    public final long date;
    public final T header;

    public DataWrapper(long date, T header) {
        this.header = header;
        this.date = date;
    }

    public T getHeader() {
        return header;
    }

    public long getDate() {
        return date;
    }

    public abstract Function<Integer, Map<Integer, DBCity>> getGetCities();

    public <T, V> V getSafe(ColumnInfo<T, V> get, int offset) {
        if (get.getOffset() == -1) return get.getDefault();
        return get(get, offset);
    }

    public abstract <T, V> V get(ColumnInfo<T, V> get, int offset);
}
