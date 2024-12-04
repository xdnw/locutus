package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.apiv3.csv.header.NationHeaderReader;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProjectileAvg implements IAllianceMetric {
    private final MilitaryUnit unit;
    private final Function<NationHeader, IntColumn<DBNation>> getHeader;

    public ProjectileAvg(MilitaryUnit unit, Function<NationHeader, IntColumn<DBNation>> getHeader) {
        this.unit = unit;
        this.getHeader = getHeader;
    }
    @Override
    public Double apply(DBAlliance alliance) {
        Set<DBNation> nations = alliance.getNations(true, 0, true);
        long total = 0;
        int num = 0;
        for (DBNation nation : nations) {
            total += nation.getUnits(unit);
            num += nation.getCities();
        }
        return total / (double) num;
    }

    private final Map<Integer, Integer> unitsByAA = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new BiConsumer<Long, NationHeaderReader>() {
            @Override
            public void accept(Long day, NationHeaderReader r) {
                Rank position = r.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = r.header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vm_turns = r.header.vm_turns.get();
                if (vm_turns == null || vm_turns > 0) return;
                unitsByAA.merge(allianceId, getHeader.apply(r.header).get(), (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
                citiesByAA.merge(allianceId, r.header.cities.get(), (a, b) -> ((Number) a).intValue() + ((Number) b).intValue());
            }
        });
    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        Map<Integer, Double> result = unitsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / citiesByAA.get(f.getKey())));
        unitsByAA.clear();
        citiesByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
