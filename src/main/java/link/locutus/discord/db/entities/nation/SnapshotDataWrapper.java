package link.locutus.discord.db.entities.nation;

import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBCity;

import java.util.Map;
import java.util.function.Function;

public class SnapshotDataWrapper<T extends DataHeader> {
    public final byte[] data;
    public final Function<Integer, Map<Integer, DBCity>> getCities;
    public final long date;
    public final T header;

    public SnapshotDataWrapper(long date, T header, byte[] data, Function<Integer, Map<Integer, DBCity>> getCities) {
        this.header = header;
        this.date = date;
        this.data = data;
        this.getCities = getCities;
    }
}
