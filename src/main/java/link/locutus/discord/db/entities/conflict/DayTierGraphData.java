package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.db.entities.DBNation;

import java.util.Map;
import java.util.Set;

public class DayTierGraphData extends TierGraphData {
    public void update(Set<DBNation> nations) {
        metricByTier.clear();
        Map<Integer, Map<Byte, Integer>> nationsByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.NATION, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> infraByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.INFRA, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> beigeByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.BEIGE, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> soldierCapacityByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.SOLDIER_CAPACITY, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> tankCapacityByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.TANK_CAPACITY, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> aircraftCapacityByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.AIRCRAFT_CAPACITY, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> shipCapacityByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.SHIP_CAPACITY, k -> new Int2ObjectOpenHashMap<>());
        for (DBNation nation : nations) {
            int aaId = nation.getAlliance_id();
            byte cities = (byte) nation.getCities();
            int infra = (int) Math.round(nation.getInfra());
            boolean isBeige = nation.isBeige();
            int researchBits = nation.getResearchBits(null);
            int cityCount = nation.getCities();
            nationsByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, 1, Integer::sum);
            infraByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, infra, Integer::sum);
            if (isBeige) beigeByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, 1, Integer::sum);
            soldierCapacityByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(
                    cities,
                    MilitaryUnit.SOLDIER.getMaxMMRCap(cityCount, researchBits, nation::hasProject),
                    Integer::sum
            );
            tankCapacityByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(
                    cities,
                    MilitaryUnit.TANK.getMaxMMRCap(cityCount, researchBits, nation::hasProject),
                    Integer::sum
            );
            aircraftCapacityByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(
                    cities,
                    MilitaryUnit.AIRCRAFT.getMaxMMRCap(cityCount, researchBits, nation::hasProject),
                    Integer::sum
            );
            shipCapacityByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(
                    cities,
                    MilitaryUnit.SHIP.getMaxMMRCap(cityCount, researchBits, nation::hasProject),
                    Integer::sum
            );
        }
    }
}
