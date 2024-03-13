package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.apiv3.csv.ParsedRow;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UnitPctMetric implements IAllianceMetric {
    private final MilitaryUnit unit;
    private final Function<NationHeader, Integer> getHeader;

    public UnitPctMetric(MilitaryUnit unit, Function<NationHeader, Integer> getHeader) {
        this.unit = unit;
        this.getHeader = getHeader;
    }
    @Override
    public Double apply(DBAlliance alliance) {
        DBNation total = alliance.getMembersTotal();
        MilitaryBuilding building = unit.getBuilding();
        return (double) total.getUnits(unit) / (total.getCities() * building.cap(f -> false) * building.getUnitCap());
    }

    private final Map<Integer, Integer> unitsByAA = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new TriConsumer<Long, NationHeader, ParsedRow>() {
            @Override
            public void accept(Long day, NationHeader header, ParsedRow row) {
                int position = row.get(header.alliance_position, Integer::parseInt);
                if (position <= Rank.APPLICANT.id) return;
                int allianceId = row.get(header.alliance_id, Integer::parseInt);
                if (allianceId == 0) return;
                int vmTurns = row.get(header.vm_turns, Integer::parseInt);
                if (vmTurns > 0) return;
                int units = row.get(getHeader.apply(header), Integer::parseInt);
                unitsByAA.merge(allianceId, units, Integer::sum);
                int cities = row.getNumber(header.cities, Integer::parseInt).intValue();
                citiesByAA.merge(allianceId, cities, Integer::sum);
            }
        });
    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        MilitaryBuilding building = unit.getBuilding();
        int unitsPerCity = building.cap(f -> false) * building.getUnitCap();
        Map<Integer, Double> result = unitsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * unitsPerCity)));
        unitsByAA.clear();
        citiesByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
