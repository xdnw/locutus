package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import link.locutus.discord.db.entities.DBNation;

import java.util.Map;
import java.util.Set;

public class DayTierGraphData extends TierGraphData {
    public void update(Set<DBNation> nations) {
        metricByTier.clear();
        Map<Byte, Integer> nationsByTier = metricByTier.computeIfAbsent(ConflictMetric.NATION, k -> new Byte2IntOpenHashMap());
        Map<Byte, Integer> infraByTier = metricByTier.computeIfAbsent(ConflictMetric.INFRA, k -> new Byte2IntOpenHashMap());
        Map<Byte, Integer> beigeByTier = metricByTier.computeIfAbsent(ConflictMetric.BEIGE, k -> new Byte2IntOpenHashMap());
        for (DBNation nation : nations) {
            byte cities = (byte) nation.getCities();
            int infra = (int) Math.round(nation.getInfra());
            boolean isBeige = nation.isBeige();
            nationsByTier.merge(cities, 1, Integer::sum);
            infraByTier.merge(cities, infra, Integer::sum);
            if (isBeige) beigeByTier.merge(cities, 1, Integer::sum);
        }
    }
}
