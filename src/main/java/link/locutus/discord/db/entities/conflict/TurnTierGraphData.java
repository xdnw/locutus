package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.db.entities.DBNation;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class TurnTierGraphData extends TierGraphData {
    public void update(Set<DBNation> nations) {
        boolean isDay = false;
        metricByTier.clear();
        List<ConflictMetric> metrics = Arrays.stream(ConflictMetric.values).filter(f -> f.isDay() == isDay).toList();
        for (DBNation nation : nations) {
            int aaId = nation.getAlliance_id();
            byte cities = (byte) nation.getCities();
            for (ConflictMetric metric : metrics) {
                int value = metric.get(nation);
                if (value == 0) continue;
                metricByTier.computeIfAbsent(metric, k -> new Int2ObjectOpenHashMap<>())
                        .computeIfAbsent(aaId, k -> new Byte2IntOpenHashMap()).merge(cities, value, Integer::sum);
            }
        }
    }
}
