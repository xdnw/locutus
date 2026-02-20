package link.locutus.discord.web.commands.binding.value_types;

import java.util.List;
import java.util.Map;

/**
 * Response for conflictAlliances endpoint.
 * - `alliance_names`: global map of allianceId -> allianceName (skipped if
 * unknown)
 * - `conflict_alliances`: map conflictId -> [[side1 alliance ids], [side2
 * alliance ids]]
 */
public class ConflictAlliances {
    public Map<Integer, String> alliance_names;
    public Map<Integer, List<List<Integer>>> conflict_alliances;

    public ConflictAlliances() {
    }

    public ConflictAlliances(Map<Integer, List<List<Integer>>> conflict_alliances) {
        this.conflict_alliances = conflict_alliances;
    }

    public ConflictAlliances(Map<Integer, String> alliance_names,
            Map<Integer, List<List<Integer>>> conflict_alliances) {
        this.alliance_names = alliance_names;
        this.conflict_alliances = conflict_alliances;
    }
}
