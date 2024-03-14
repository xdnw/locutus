package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.DBNationSnapshot;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.function.Predicate;

public class NationsFile extends DataFile<DBNation, NationHeader> {
    public NationsFile(File file) {
        super(file, new NationHeader(parseDateFromFile(file.getName())));
    }

    public Map<Integer, DBNationSnapshot> readNations(Predicate<Integer> allowedNationIds, Predicate<Integer> allowedAllianceIds, boolean allowVm, boolean allowNoVmCol, boolean allowDeleted) throws IOException {
       Map<Integer, DBNationSnapshot> result = new Int2ObjectOpenHashMap<>();
        this.reader().all().read(new ThrowingConsumer<NationHeader>() {
            @Override
            public void acceptThrows(NationHeader header) throws ParseException {
                DBNationSnapshot nation = header.getNation(allowedNationIds, allowedAllianceIds, allowVm, allowNoVmCol, allowDeleted);
                if (nation != null) {
                    result.put(nation.getNation_id(), nation);
                }
            }
        });
        return result;
    }
}
