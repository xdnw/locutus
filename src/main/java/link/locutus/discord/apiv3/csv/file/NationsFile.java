package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DataWrapper;
import link.locutus.discord.db.entities.nation.GlobalDataWrapper;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.function.Function;

public class NationsFile extends DataFile<DBNation, NationHeader, NationHeaderReader> {
    public NationsFile(File file, Dictionary dict) {
        super(file, parseDateFromFile(file.getName()), () -> new NationHeader(dict), (NationHeaderReader::new));
    }

    private ThreadLocal<SoftReference<Map<Integer, DBNationSnapshot>>> nationsCache = ThreadLocal.withInitial(() -> new SoftReference<>(null));

    public synchronized Map<Integer, DBNationSnapshot> readNations(CitiesFile cf) throws IOException {
        Function<Integer, Map<Integer, DBCity>> fetchCities = cf == null ? null : cf.loadCities();
        return readNations(fetchCities);
    }

    public synchronized Map<Integer, DBNationSnapshot> readNations(Function<Integer, Map<Integer, DBCity>> fetchCities) throws IOException {
        SoftReference<Map<Integer, DBNationSnapshot>> softRef = nationsCache.get();
        Map<Integer, DBNationSnapshot> cached = (softRef != null) ? softRef.get() : null;
        if (cached != null) return cached;
        synchronized (this) {
            softRef = nationsCache.get();
            cached = (softRef != null) ? softRef.get() : null;
            if (cached != null) return cached;

            NationHeader header = getGlobalHeader();
            byte[] data = this.getBytes();
            Header<DBNation> colInfo = header.readIndexes(data);
            int bytesPerRow = colInfo.bytesPerRow;

            int rows = (data.length - colInfo.initialOffset) / bytesPerRow;
            int remainder = (data.length - colInfo.initialOffset) % bytesPerRow;

            DataWrapper wrapper = new GlobalDataWrapper(getDate(), header, data, fetchCities);

            Map<Integer, DBNationSnapshot> result = new Int2ObjectOpenHashMap<>();
            for (int i = colInfo.initialOffset; i < data.length; i += bytesPerRow) {
                DBNationSnapshot nation = new DBNationSnapshot(wrapper, i);
                if (nation != null) {
                    result.put(nation.getNation_id(), nation);
                }
            }
            nationsCache.set(new SoftReference<>(result));
            return result;
        }
    }
}
