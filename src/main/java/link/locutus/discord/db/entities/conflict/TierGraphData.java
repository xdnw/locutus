package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import link.locutus.discord.db.ConflictManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TierGraphData {
    protected final Map<ConflictMetric, Map<Byte, Integer>> metricByTier = new EnumMap<>(ConflictMetric.class);
    public Map<Byte, Integer> getOrCreate(ConflictMetric metric) {
        return metricByTier.computeIfAbsent(metric, k -> new Byte2IntOpenHashMap());
    }

    public Map<Byte, Integer> get(ConflictMetric metric) {
        return metricByTier.get(metric);
    }

    public void save(ConflictManager manager, int id, boolean isPrimary, long turn) {
        manager.addGraphData(getEntries(id, isPrimary, turn));
    }

    public List<ConflictMetric.Entry> getEntries(int conflictId, boolean isPrimary, long turn) {
        return metricByTier.entrySet().stream()
                .flatMap(e -> e.getValue().entrySet().stream().map(f -> new ConflictMetric.Entry(e.getKey(), conflictId, isPrimary, turn, f.getKey(), f.getValue())))
                .toList();
    }

    public void clear() {
        metricByTier.clear();
    }
}
