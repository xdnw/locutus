package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.db.entities.DBNation;

import java.util.Map;
import java.util.Set;

public class DayTierGraphData extends TierGraphData {
    public void update(Set<DBNation> nations) {
        metricByTier.clear();
        Map<Integer, Map<Byte, Integer>> nationsByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.NATION, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> infraByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.INFRA, k -> new Int2ObjectOpenHashMap<>());
        Map<Integer, Map<Byte, Integer>> beigeByAllianceByTier = metricByTier.computeIfAbsent(ConflictMetric.BEIGE, k -> new Int2ObjectOpenHashMap<>());
        for (DBNation nation : nations) {
            int aaId = nation.getAlliance_id();
            byte cities = (byte) nation.getCities();
            int infra = (int) Math.round(nation.getInfra());
            boolean isBeige = nation.isBeige();
            nationsByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, 1, Integer::sum);
            infraByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, infra, Integer::sum);
            if (isBeige) beigeByAllianceByTier.computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, 1, Integer::sum);
        }
    }
}
