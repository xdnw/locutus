package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

public class GlobalDataWrapper<T extends DataHeader> extends DataWrapper<T> {
    public final byte[] data;
    public final Function<Integer, Map<Integer, DBCity>> getCities;

    public GlobalDataWrapper(long date, T header, byte[] data, Function<Integer, Map<Integer, DBCity>> getCities) {
        super(date, header);
        this.data = data;
        this.getCities = getCities;
    }

    public Function<Integer, Map<Integer, DBCity>> getGetCities() {
        return getCities;
    }

    @Override
    public <T, V> V get(ColumnInfo<T, V> get, int offset) {
        try {
            return get.read(data, get.getOffset() + offset);
        } catch (IOException e) {
            String timeFormat = TimeUtil.format(TimeUtil.DD_MM_YY, getDate());
            throw new RuntimeException(this.getHeader().getAliases() + " @ " + timeFormat + " | " + e.getMessage(), e);
        }
    }
}
