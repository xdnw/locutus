package link.locutus.discord.db.entities.conflict;

import it.unimi.dsi.fastutil.bytes.Byte2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.db.ConflictManager;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TierGraphData {
    protected final Map<ConflictMetric, Map<Integer, Map<Byte, Integer>>> metricByTier = new EnumMap<>(ConflictMetric.class);

    public Map<Byte, Integer> getOrCreate(ConflictMetric metric, int allianceId) {
        return metricByTier.computeIfAbsent(metric, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(allianceId, k -> new Byte2IntOpenHashMap());
    }

    public void save(ConflictManager manager, int id, boolean isPrimary, long turn) {
        manager.addGraphData(getEntries(id, isPrimary, turn));
    }

    public List<ConflictMetric.Entry> getEntries(int conflictId, boolean isPrimary, long turn) {
        List<ConflictMetric.Entry> entries = new ObjectArrayList<>();
        for (Map.Entry<ConflictMetric, Map<Integer, Map<Byte, Integer>>> conflictEntry : metricByTier.entrySet()) {
            ConflictMetric metric = conflictEntry.getKey();
            for (Map.Entry<Integer, Map<Byte, Integer>> allianceEntry : conflictEntry.getValue().entrySet()) {
                int allianceId = allianceEntry.getKey();
                for (Map.Entry<Byte, Integer> tierEntry : allianceEntry.getValue().entrySet()) {
                    int tier = tierEntry.getKey();
                    int value = tierEntry.getValue();
                    entries.add(new ConflictMetric.Entry(metric, conflictId, allianceId, isPrimary, turn, tier, value));
                }
            }
        }
        return entries;
    }

    public void clear() {
        metricByTier.clear();
    }
}
