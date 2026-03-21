package link.locutus.discord.db;

import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Narrow lookup surface for alliance-id collections.
 */
public interface AllianceLookup {
    Set<DBNation> getNationsByAlliance(Set<Integer> alliances);

    DBAlliance getAlliance(int allianceId);

    default Set<DBNation> getNationsByAlliance(int allianceId) {
        return getNationsByAlliance(Collections.singleton(allianceId));
    }

    default Set<DBAlliance> getAlliances(Collection<Integer> allianceIds) {
        Set<DBAlliance> alliances = new LinkedHashSet<>();
        for (int allianceId : allianceIds) {
            DBAlliance alliance = getAlliance(allianceId);
            if (alliance != null) {
                alliances.add(alliance);
            }
        }
        return alliances;
    }
}
