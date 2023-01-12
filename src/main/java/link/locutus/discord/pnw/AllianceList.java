package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.util.StringMan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    public Set<DBAlliance> getAlliances() {
        Set<DBAlliance> alliances = new HashSet<>();
        for (int id : ids) {
            DBAlliance alliance = DBAlliance.get(id);
            if (alliance != null) {
                alliances.add(alliance);
            }
        }
        return alliances; }

    public List<BankDB.TaxDeposit> updateTaxes() {
        List<BankDB.TaxDeposit> deposits = new ArrayList<>();
        for (DBAlliance alliance : getAlliances()) {
            deposits.addAll(alliance.updateTaxes());
        }
        return deposits;
    }

    public AllianceList subList(Collection<DBNation> nations) {
        Set<Integer> ids = new HashSet<>();
        for (DBNation nation : nations) {
            if (!this.ids.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Nation " + nation.getNation() + " is not in the alliance: " + StringMan.getString(this.ids));
            }
            ids.add(nation.getAlliance_id());
        }
        return new AllianceList(ids);
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile() throws IOException {
        Map<DBNation, Map<ResourceType, Double>> result = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances()) {
            result.putAll(alliance.getMemberStockpile());
        }
        return result;
    }
}
