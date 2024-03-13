package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBAlliance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnitMetric implements IAllianceMetric {
    private final MilitaryUnit unit;
    private final Function<NationHeader, Integer> getHeader;

    public UnitMetric(MilitaryUnit unit, Function<NationHeader, Integer> getHeader) {
        this.unit = unit;
        this.getHeader = getHeader;
    }
    @Override
    public Double apply(DBAlliance alliance) {
        return (double) alliance.getMembersTotal().getUnits(unit);
    }

    private final Map<Integer, Integer> unitsByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new BiConsumer<Long, NationHeader>() {
            @Override
            public void accept(Long day, NationHeader header) {
                int position = header.alliance_position.get();
                if (position <= Rank.APPLICANT.id) return;
                int allianceId = row.get(header.alliance_id, Integer::parseInt);
                if (allianceId == 0) return;
                int vmTurns = row.get(header.vm_turns, Integer::parseInt);
                if (vmTurns > 0) return;
                int units = row.get(getHeader.apply(header), Integer::parseInt);
                unitsByAA.merge(allianceId, units, Integer::sum);
            }
        });
    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        Map<Integer, Double> result = unitsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue()));
        unitsByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        unitsByAA.clear();
        return null;
    }
}
