package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AllianceList {
    private final Set<Integer> ids;

    public AllianceList(Integer... ids) {
        this(Arrays.asList(ids));
    }

    public <T> AllianceList(Set<Integer> ids) {
        if (ids.isEmpty()) throw new IllegalArgumentException("Empty alliance list");
        this.ids = ids;
    }

    public <T> AllianceList(Collection<Integer> ids) {
        this(new LinkedHashSet<>(ids));
    }

    public boolean isInAlliance(DBNation nation) {
        return ids.contains(nation.getNation_id());
    }

    public Set<DBNation> getNations() {
        return Locutus.imp().getNationDB().getNations(ids);
    }

    public Set<DBNation> getNations(boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = getNations();
        if (removeVM) nations.removeIf(f -> f.getVm_turns() != 0);
        if (removeInactiveM > 0) nations.removeIf(f -> f.getActive_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition() <= 1);
        return nations;
    }

    public Map<Integer, TaxBracket> getTaxBrackets(boolean useCache) {
        Map<Integer, TaxBracket> brackets = new HashMap<>();
        for (int id : ids) {
            DBAlliance alliance = DBAlliance.get(id);
            if (alliance != null) {
                brackets.putAll(alliance.getTaxBrackets(useCache));
            }
        }
        return brackets;
    }

    public String setTaxBracket(TaxBracket required, DBNation nation) {
        if (required.getAllianceId() != nation.getAlliance_id()) {
            throw new IllegalArgumentException(nation.getNation() + " is not in the alliance: " + required.getAllianceId() + " for bracket: #" + required.taxId);
        }
        DBAlliance.get(required.getAllianceId()).setTaxBracket(required, nation);
        return null;
    }
}
