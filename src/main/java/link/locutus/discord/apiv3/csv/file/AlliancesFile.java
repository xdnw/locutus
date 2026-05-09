package link.locutus.discord.apiv3.csv.file;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv3.csv.header.AllianceHeader;
import link.locutus.discord.apiv3.csv.header.AllianceHeaderReader;
import link.locutus.discord.db.entities.DBAlliance;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Map;

public class AlliancesFile extends DataFile<DBAlliance, AllianceHeader, AllianceHeaderReader> {
    private SoftReference<Map<Integer, DBAlliance>> alliancesCache = new SoftReference<>(null);

    public AlliancesFile(File file, Dictionary dict) {
        super(file, parseDateFromFile(file.getName()), () -> new AllianceHeader(dict), AllianceHeaderReader::new);
    }

    public synchronized Map<Integer, DBAlliance> readAlliances() throws IOException {
        Map<Integer, DBAlliance> cached = alliancesCache.get();
        if (cached != null) {
            return cached;
        }

        Map<Integer, DBAlliance> alliances = new Int2ObjectOpenHashMap<>();
        reader().required(header -> java.util.List.of(
                header.alliance_id,
                header.date_created,
                header.name,
                header.acronym,
                header.color,
                header.score,
                header.flag_url)).read(reader -> {
            DBAlliance alliance = reader.getAlliance();
            alliances.put(alliance.getAlliance_id(), alliance);
        });
        alliancesCache = new SoftReference<>(alliances);
        return alliances;
    }
}