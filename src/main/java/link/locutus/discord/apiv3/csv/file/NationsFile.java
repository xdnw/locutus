package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.ColumnInfo;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.SnapshotDataWrapper;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import net.jpountz.util.SafeUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.ParseException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class NationsFile extends DataFile<DBNation, NationHeader> {
    public NationsFile(File file, Dictionary dict) {
        super(file, parseDateFromFile(file.getName()), () -> new NationHeader(dict));
    }

    private SoftReference<Map<Integer, DBNationSnapshot>> nationsCache = new SoftReference<>(null);

    public synchronized Map<Integer, DBNationSnapshot> readNations(Function<Integer, Map<Integer, DBCity>> fetchCities) throws IOException {
        Map<Integer, DBNationSnapshot> cached = nationsCache.get();
        if (cached != null) return cached;
        synchronized (this) {
            cached = nationsCache.get();
            if (cached != null) return cached;

            // set cached
            NationHeader header = getGlobalHeader();
            byte[] data = this.getBytes();
            Header<DBNation> colInfo = header.readIndexes(data);
            int bytesPerRow = colInfo.bytesPerRow;

            SnapshotDataWrapper wrapper = new SnapshotDataWrapper(getDate(), header, data, fetchCities);

            Map<Integer, DBNationSnapshot> result = new Int2ObjectOpenHashMap<>();
            for (int i = colInfo.initialOffset; i < data.length; i += bytesPerRow) {
                DBNationSnapshot nation = new DBNationSnapshot(wrapper, i);
                if (nation != null) {
                    result.put(nation.getNation_id(), nation);
                }
            }
            nationsCache = new SoftReference<>(result);
            return result;
        }
    }

//    public Map<Integer, DBNationSnapshot> readNations(Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) throws IOException {
//        Map<Integer, DBNationSnapshot> result = new Int2ObjectOpenHashMap<>();
//        this.reader().all(false).read(new ThrowingConsumer<NationHeader>() {
//            @Override
//            public void acceptThrows(NationHeader header) throws ParseException {
//                DBNationSnapshot nation = header.getNation(allowedNationIds, allowedAllianceIds, allowVm, allowNoVmCol, allowDeleted);
//                if (nation != null) {
//                    result.put(nation.getNation_id(), nation);
//                }
//            }
//        });
//        return result;
//    }
}
