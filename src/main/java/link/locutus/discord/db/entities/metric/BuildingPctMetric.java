package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.apiv3.ParsedRow;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildingPctMetric implements IAllianceMetric {
    private final Function<DataDumpParser.CityHeader, Integer> getHeader;
    private final MilitaryBuilding building;

    public BuildingPctMetric(MilitaryBuilding building, Function<DataDumpParser.CityHeader, Integer> getHeader) {
        this.getHeader = getHeader;
        this.building = building;
    }
    @Override
    public Double apply(DBAlliance alliance) {
        int count = 0;
        int cities = 0;
        Set<Building> buildings = Set.of(building);
        for (DBNation nation : alliance.getMemberDBNations()) {
            count += nation.getBuildings(buildings);
            cities += nation.getCities();
        }
        return count / (double) (cities * building.cap(f -> false));
    }

    private final Map<Integer, Integer> allianceByNationId = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> citiesByAA = new Int2IntOpenHashMap();
    private final Map<Integer, Integer> buildingsByAA = new Int2IntOpenHashMap();

    @Override
    public void setupReaders(IAllianceMetric metric, DataDumpImporter importer) {
        importer.setNationReader(metric, new TriConsumer<Long, DataDumpParser.NationHeader, ParsedRow>() {
            @Override
            public void consume(Long day, DataDumpParser.NationHeader header, ParsedRow row) {
                int position = row.get(header.alliance_position, Integer::parseInt);
                if (position <= Rank.APPLICANT.id) return;
                int allianceId = row.get(header.alliance_id, Integer::parseInt);
                if (allianceId == 0) return;
                int vmTurns = row.get(header.vm_turns, Integer::parseInt);
                if (vmTurns > 0) return;
                allianceByNationId.put(row.get(header.nation_id, Integer::parseInt), allianceId);
                int cities = row.get(header.cities, Integer::parseInt);
                citiesByAA.merge(allianceId, cities, Integer::sum);
            }
        });

        importer.setCityReader(metric, new TriConsumer<Long, DataDumpParser.CityHeader, ParsedRow>() {
            @Override
            public void consume(Long aLong, DataDumpParser.CityHeader header, ParsedRow parsedRow) {
                int nationId = parsedRow.get(header.nation_id, Integer::parseInt);
                Integer allianceId = allianceByNationId.get(nationId);
                if (allianceId == null || allianceId == 0) return;
                int num = parsedRow.get(getHeader.apply(header), Integer::parseInt);
                buildingsByAA.merge(allianceId, num, Integer::sum);
            }
        });

    }

    @Override
    public Map<Integer, Double> getDayValue(DataDumpImporter importer, long day) {
        int buildingsPerCity = building.cap(f -> false);
        Map<Integer, Double> result = buildingsByAA.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * buildingsPerCity)));
        allianceByNationId.clear();
        citiesByAA.clear();
        buildingsByAA.clear();
        return result;
    }

    @Override
    public List<AllianceMetricValue> getAllValues() {
        return null;
    }
}
