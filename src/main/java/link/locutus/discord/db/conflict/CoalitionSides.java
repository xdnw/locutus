package link.locutus.discord.db.conflict;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.db.entities.conflict.DamageStatGroup;

import java.util.Map;

public class CoalitionSides {
    public final CoalitionSide col1;
    public final CoalitionSide col2;

    private final Map<Integer, Map<Integer, DamageStatGroup>> warsVsAlliance2;

    public CoalitionSides(Conflict conflict, String col1Name, String col2Name) {
        col1 = new CoalitionSide(conflict, col1Name, true);
        col2 = new CoalitionSide(conflict, col2Name, false);
        col1.setOther(col2);
        col2.setOther(col1);
        warsVsAlliance2 = new Int2ObjectOpenHashMap<>();
    }

    public long getLatestGraphTurn() {
        WarStatistics stat1 = col1.getWarDataOrNull();
        WarStatistics stat2 = col2.getWarDataOrNull();
        long max = 0;
        if (stat1 != null) {
            max = Math.max(max, stat1.getLatestGraphTurn());
        }
        if (stat2 != null) {
            max = Math.max(max, stat2.getLatestGraphTurn());
        }
        return max;
    }

    public void clearWarData() {
        warsVsAlliance2.clear();
        col1.clearWarData();
        col2.clearWarData();
    }

    public Map<Integer, Map<Integer, DamageStatGroup>> getDataWithWars() {
        col1.get();
        return warsVsAlliance2;
    }
}
