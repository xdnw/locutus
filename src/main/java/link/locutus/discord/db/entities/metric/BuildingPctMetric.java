package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv3.csv.column.IntColumn;
import link.locutus.discord.apiv3.csv.header.CityHeader;
import link.locutus.discord.apiv3.csv.header.NationHeader;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BuildingPctMetric implements IAllianceMetric {
    private final Function<CityHeader, IntColumn<DBCity>> getHeader;
    private final MilitaryBuilding building;

    public BuildingPctMetric(MilitaryBuilding building, Function<CityHeader, IntColumn<DBCity>> getHeader) {
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
        importer.setNationReader(metric, new BiConsumer<Long, NationHeader>() {
            @Override
            public void accept(Long day, NationHeader header) {
                int position = header.header.alliance_position.get();
                if (position.id <= Rank.APPLICANT.id) return;
                int allianceId = header.alliance_id.get();
                if (allianceId == 0) return;
                Integer vm_turns = header.vm_turns.get();
                if (vm_turns == null || vm_turns > 0) return;
                allianceByNationId.put(header.nation_id.get(), allianceId);
                int cities = header.cities.get();
                citiesByAA.merge(allianceId, cities, Integer::sum);
            }
        });

        importer.setCityReader(metric, new BiConsumer<Long, CityHeader>() {
            @Override
            public void accept(Long day, CityHeader header) {
                int nationId = header.nation_id.get();
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
        Map<Integer, Double> result = buildingsByAA.entrySet().stream().filter(f -> citiesByAA.containsKey(f.getKey())).collect(Collectors.toMap(Map.Entry::getKey, f -> (double) f.getValue() / (citiesByAA.get(f.getKey()) * buildingsPerCity)));
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
