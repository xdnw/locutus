package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;
import java.util.*;

public class ConflictEndpoints extends PageHelper {
    @Command(viewable = true)
    @ReturnType(value = ConflictAlliances.class)
    public Object conflictAlliances(@Me GuildDB db, ConflictManager manager, Set<Conflict> conflicts) {
        Int2ObjectMap<String> allianceNames = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<List<List<Integer>>> byConflictFast = new Int2ObjectOpenHashMap<>();

        if (conflicts == null || conflicts.isEmpty()) {
            return new ConflictAlliances(allianceNames, byConflictFast);
        }

        Map<Integer, List<List<Integer>>> byConflict = manager.getConflictAlliances(conflicts);
        // copy into fastutil map for consistency/performance while exposing a generic
        // Map in the DTO
        for (Map.Entry<Integer, List<List<Integer>>> e : byConflict.entrySet())
            byConflictFast.put(e.getKey(), e.getValue());

        for (List<List<Integer>> sides : byConflictFast.values()) {
            for (List<Integer> side : sides) {
                for (Integer aaId : side) {
                    int key = aaId.intValue();
                    if (!allianceNames.containsKey(key)) {
                        String name = manager.getAllianceNameOrNull(aaId);
                        if (name != null)
                            allianceNames.put(key, name);
                    }
                }
            }
        }

        return new ConflictAlliances(allianceNames, byConflictFast);
    }
}
