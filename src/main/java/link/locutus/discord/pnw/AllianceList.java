package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.Event;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.text.ParseException;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AllianceList {
    private final Set<Integer> ids;

    public AllianceList(Integer... ids) {
        this(Arrays.asList(ids));
    }

    public <T> AllianceList(Set<Integer> ids) {
        if (ids.isEmpty()) throw new IllegalArgumentException("Empty alliance list");
        this.ids = ids;
    }

    public AllianceList subList(Roles role, User user, GuildDB db) {
        AllianceList allowed = role.getAllianceList(user, db);
        return this.subList(allowed.getIds());
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

    public Set<DBNation> getNations(Predicate<DBNation> filter) {
        Set<DBNation> nations = new HashSet<>();
        for (DBNation nation : getNations()) {
            if (filter.test(nation)) nations.add(nation);
        }
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
        int aaId = required.getAlliance_id();
        if (aaId != nation.getAlliance_id()) {
            throw new IllegalArgumentException(nation.getNation() + " is not in the alliance: " + aaId + " for bracket: #" + required.taxId);
        }
        required.getAlliance().setTaxBracket(required, nation);
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
    public AllianceList subList(Set<Integer> aaIds) {
        Set<Integer> copy = new LinkedHashSet<>(ids);
        copy.retainAll(aaIds);
        return new AllianceList(copy);
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

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> getResourcesNeeded(Collection<DBNation> nations, double daysDefault, boolean useExisting, boolean force) throws IOException {
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> result = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances()) {
            result.putAll(alliance.getResourcesNeeded(nations, daysDefault, useExisting, force));
        }
        return result;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> calculateDisburse(Collection<DBNation> nations, double daysDefault, boolean useExisting, boolean ignoreInactives, boolean allowBeige, boolean noDailyCash, boolean noCash, boolean force) throws IOException, ExecutionException, InterruptedException {
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> result = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances()) {
            try {
                alliance.updateCities();
            } catch (ParseException e) {
                for (DBNation nation : nations) {
                    if (nation.getAlliance_id() == alliance.getAlliance_id()) {
                        result.put(nation, Map.entry(OffshoreInstance.TransferStatus.INVALID_API_KEY, ResourceType.getBuffer()));
                    }
                }

                continue;
            }
            result.putAll(alliance.calculateDisburse(nations, daysDefault, useExisting, ignoreInactives, allowBeige, noDailyCash, noCash, force));
        }
        return result;
    }

    public Set<Treaty> sendTreaty(int allianceId, TreatyType type, String message, int days) {
        Set<Treaty> treaties = new HashSet<>();
        for (DBAlliance alliance : getAlliances()) {
            if (alliance.getAlliance_id() != allianceId) {
                treaties.add(alliance.sendTreaty(allianceId, type, message, days));
            }
        }
        return treaties;
    }

    public Set<Integer> getIds() {
        return Collections.unmodifiableSet(ids);
    }

    public Map<Integer, Set<Treaty>> getTreaties(boolean update) {
        Map<Integer, Set<Treaty>> treatiesBySender = new HashMap<>();
        for (DBAlliance alliance : getAlliances()) {
            for (Map.Entry<Integer, Treaty> entry : alliance.getTreaties(update).entrySet()) {
                treatiesBySender.computeIfAbsent(entry.getKey(), f -> new HashSet<>()).add(entry.getValue());
            }
        }
        return treatiesBySender;
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    public boolean contains(DBAlliance to) {
        return ids.contains(to.getAlliance_id());
    }

    public List<Treaty> approveTreaty(Set<DBAlliance> senders) {
        Map<Integer, Set<Treaty>> treaties = getTreaties(true);

        List<Treaty> changed = new ArrayList<>();

        for (Map.Entry<Integer, Set<Treaty>> entry : treaties.entrySet()) {
            if (!senders.contains(DBAlliance.getOrCreate(entry.getKey()))) {
                continue;
            }
            for (Treaty treaty : entry.getValue()) {
                DBAlliance to = treaty.getTo();
                if (!treaty.isPending() || !contains(to)) continue;

                changed.add(to.approveTreaty(treaty.getId()));
            }
        }
        return changed;
    }

    public List<Treaty> cancelTreaty(Set<DBAlliance> senders) {
        Map<Integer, Set<Treaty>> treaties = getTreaties(true);

        List<Treaty> changed = new ArrayList<>();

        for (Map.Entry<Integer, Set<Treaty>> entry : treaties.entrySet()) {
            for (Treaty treaty : entry.getValue()) {
                if ((contains(treaty.getTo()) && senders.contains(treaty.getFrom())) ||
                        contains(treaty.getFrom()) && senders.contains(treaty.getTo())) {
                    DBAlliance self;
                    if (contains(treaty.getTo())) {
                        self = treaty.getTo();
                    } else if (contains(treaty.getFrom())) {
                        self = treaty.getFrom();
                    } else continue;

                    changed.add(self.cancelTreaty(treaty.getId()));
                }
            }
        }
        return changed;
    }

    public boolean contains(int aaId) {
        return ids.contains(aaId);
    }

    public Map<ResourceType, Double> getStockpile() throws IOException {
        Map<ResourceType, Double> stockpile = new HashMap<>();
        for (DBAlliance alliance : getAlliances()) {
            for (Map.Entry<ResourceType, Double> entry : alliance.getStockpile().entrySet()) {
                stockpile.put(entry.getKey(), stockpile.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }
        return stockpile;
    }

    public void updateCities() {
        Set<Integer> nationIds = getNations(false, 0, true).stream().map(f -> f.getId()).collect(Collectors.toSet());
        Locutus.imp().getNationDB().updateCitiesOfNations(nationIds, true, Event::post);
    }

    public Set<DBAlliancePosition> getPositions() {
        Set<DBAlliancePosition> positions = new LinkedHashSet<>();
        for (DBAlliance alliance : getAlliances()) {
            positions.addAll(alliance.getPositions());
        }
        return positions;
    }

    public int size() {
        return ids.size();
    }
}
