package link.locutus.discord.pnw;

import com.google.common.base.Predicates;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.bank.TaxBracketLookup;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AllianceList {
    private final Set<Integer> ids;

    public AllianceList(Integer... ids) {
        this(Arrays.asList(ids));
    }

    public <T> AllianceList(Set<Integer> ids) {
        this.ids = ids;
    }

    public AllianceList subList(Roles role, User user, GuildDB db) {
        AllianceList allowed = role.getAllianceList(user, db);
        return this.subList(allowed.getIds());
    }

    public <T> AllianceList(Collection<Integer> ids) {
        this(new IntLinkedOpenHashSet(ids));
    }

    public boolean isInAlliance(DBNation nation) {
        return ids.contains(nation.getAlliance_id());
    }

    public Set<DBNation> getNations(AllianceLookup lookup) {
        return lookup.getNationsByAlliance(ids);
    }

    public Set<DBNation> getNations(AllianceLookup lookup, boolean removeVM, int removeInactiveM, boolean removeApps) {
        Set<DBNation> nations = getNations(lookup);
        if (removeVM) nations.removeIf(f -> f.getVm_turns() != 0);
        if (removeInactiveM > 0) nations.removeIf(f -> f.active_m() > removeInactiveM);
        if (removeApps) nations.removeIf(f -> f.getPosition() <= 1);
        return nations;
    }

    public Set<DBNation> getNations(AllianceLookup lookup, Predicate<DBNation> filter) {
        Set<DBNation> nations = new HashSet<>();
        for (DBNation nation : getNations(lookup)) {
            if (filter.test(nation)) nations.add(nation);
        }
        return nations;
    }

    public Map<Integer, TaxBracket> getTaxBrackets(AllianceLookup lookup, long cacheFor) {
        Map<Integer, TaxBracket> brackets = new HashMap<>();
        TaxBracketLookup taxLookup = lookup instanceof TaxBracketLookup typed ? typed : null;
        for (DBAlliance alliance : getAlliances(lookup)) {
            for (TaxBracket bracket : alliance.getTaxBrackets(cacheFor).values()) {
                if (taxLookup != null) {
                    bracket.withLookup(taxLookup);
                }
                brackets.put(bracket.taxId, bracket);
            }
        }
        return brackets;
    }

    public boolean setTaxBracket(TaxBracket required, DBNation nation) {
        int aaId = required.getAlliance_id();
        if (aaId != nation.getAlliance_id()) {
            throw new IllegalArgumentException(nation.getNation() + " is not in the alliance: " + aaId + " for bracket: #" + required.taxId);
        }
        return required.getAlliance().setTaxBracket(required, nation);
    }

    public Set<DBAlliance> getAlliances(AllianceLookup lookup) {
        return lookup.getAlliances(ids);
    }

    public List<TaxDeposit> updateTaxes(AllianceLookup lookup, Consumer<List<TaxDeposit>> depositSink) {
        return updateTaxes(lookup, null, depositSink);
    }

    public List<TaxDeposit> updateTaxes(AllianceLookup lookup, Long startDate, Consumer<List<TaxDeposit>> depositSink) {
        List<TaxDeposit> deposits = new ObjectArrayList<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            List<TaxDeposit> taxRecs = alliance.updateTaxes(startDate, false);
            if (taxRecs == null) throw new IllegalStateException("Failed to update taxes for " + alliance.getMarkdownUrl() + ". Are you sure the API_KEY set has the scope `" + AlliancePermission.TAX_BRACKETS.name() + "`");
            deposits.addAll(taxRecs);
        }
        depositSink.accept(deposits);
        return deposits;
    }

    public AllianceList subList(Set<Integer> aaIds) {
        Set<Integer> copy = new IntLinkedOpenHashSet(ids);
        copy.retainAll(aaIds);
        return new AllianceList(copy);
    }

    public AllianceList subList(Collection<DBNation> nations) {
        Set<Integer> ids = new IntOpenHashSet();
        for (DBNation nation : nations) {
            if (!this.ids.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Nation " + nation.getNation() + " is not in the alliance: " + StringMan.getString(this.ids));
            }
            ids.add(nation.getAlliance_id());
        }
        return new AllianceList(ids);
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile(AllianceLookup lookup) throws IOException {
        return getMemberStockpile(lookup, Predicates.alwaysTrue());
    }

    public Map<DBNation, Map<ResourceType, Double>> getMemberStockpile(AllianceLookup lookup, Predicate<DBNation> fetchNation) throws IOException {
        Map<DBNation, Map<ResourceType, Double>> result = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            result.putAll(alliance.getMemberStockpile(fetchNation));
        }
        return result;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> getResourcesNeeded(AllianceLookup lookup, Collection<DBNation> nations, double daysDefault, boolean useExisting, boolean force) throws IOException {
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> result = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            result.putAll(alliance.getResourcesNeeded(nations, null, daysDefault, useExisting, force));
        }
        return result;
    }

    public Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> calculateDisburse(AllianceLookup lookup, Consumer<Set<Integer>> cityUpdater, Collection<DBNation> nations, Map<DBNation, Map<ResourceType, Double>> cachedStockpilesorNull, double daysDefault, boolean useExisting, boolean ignoreInactives, boolean allowBeige, boolean noDailyCash, boolean noCash, boolean bypassChecks, boolean force) throws IOException {
        Map<DBNation, Map.Entry<OffshoreInstance.TransferStatus, double[]>> result = new LinkedHashMap<>();
        if (force) {
            Set<Integer> nationIds = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            Objects.requireNonNull(cityUpdater, "cityUpdater").accept(nationIds);
        }
        for (DBAlliance alliance : getAlliances(lookup)) {
            Set<DBNation> nationsInAA = nations.stream().filter(f -> f.getAlliance_id() == alliance.getAlliance_id()).collect(Collectors.toSet());
            result.putAll(alliance.calculateDisburse(nationsInAA, cachedStockpilesorNull, daysDefault, useExisting, ignoreInactives, allowBeige, noDailyCash, noCash, bypassChecks, false));
        }
        return result;
    }

    public Set<Treaty> sendTreaty(AllianceLookup lookup, int allianceId, TreatyType type, String message, int days) {
        Set<Treaty> treaties = new HashSet<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            if (alliance.getAlliance_id() != allianceId) {
                treaties.add(alliance.sendTreaty(allianceId, type, message, days));
            }
        }
        return treaties;
    }

    public Set<Integer> getIds() {
        return Collections.unmodifiableSet(ids);
    }

    public Map<Integer, Set<Treaty>> getTreaties(AllianceLookup lookup, boolean update) {
        Map<Integer, Set<Treaty>> treatiesBySender = new HashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            for (Map.Entry<Integer, Treaty> entry : alliance.getTreaties(update).entrySet()) {
                treatiesBySender.computeIfAbsent(entry.getKey(), f -> new HashSet<>()).add(entry.getValue());
            }
        }
        return treatiesBySender;
    }

    public boolean isEmpty(AllianceLookup lookup) {
        return getAlliances(lookup).isEmpty();
    }

    public boolean contains(DBAlliance to) {
        return ids.contains(to.getAlliance_id());
    }

    public List<Treaty> approveTreaty(AllianceLookup lookup, Set<DBAlliance> senders) {
        Map<Integer, Set<Treaty>> treaties = getTreaties(lookup, true);
        Set<Integer> senderIds = senders.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());

        List<Treaty> changed = new ArrayList<>();

        for (Map.Entry<Integer, Set<Treaty>> entry : treaties.entrySet()) {
            if (!senderIds.contains(entry.getKey())) {
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

    public List<Treaty> cancelTreaty(AllianceLookup lookup, Set<DBAlliance> senders) {
        Map<Integer, Set<Treaty>> treaties = getTreaties(lookup, true);

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

    public Map<ResourceType, Double> getStockpile(AllianceLookup lookup) throws IOException {
        Map<ResourceType, Double> stockpile = new HashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            for (Map.Entry<ResourceType, Double> entry : alliance.getStockpile().entrySet()) {
                stockpile.put(entry.getKey(), stockpile.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
            }
        }
        return stockpile;
    }

    public void updateCities(AllianceLookup lookup, Consumer<Set<Integer>> cityUpdater) {
        Set<Integer> nationIds = getNations(lookup, false, 0, true).stream().map(DBNation::getId).collect(Collectors.toSet());
        cityUpdater.accept(nationIds);
    }

    public Set<DBAlliancePosition> getPositions(AllianceLookup lookup) {
        Set<DBAlliancePosition> positions = new ObjectLinkedOpenHashSet<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            positions.addAll(alliance.getPositions());
        }
        return positions;
    }

    public int size() {
        return ids.size();
    }

    public Map<DBNation, Integer> updateOffSpyOps(AllianceLookup lookup) {
        Map<DBNation, Integer> ops = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            ops.putAll(alliance.updateOffSpyOps());
        }
        return ops;
    }

    public Map<DBNation, Map<MilitaryUnit, Integer>> updateMilitaryBuys(AllianceLookup lookup) {
        Map<DBNation, Map<MilitaryUnit, Integer>> ops = new LinkedHashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            ops.putAll(alliance.updateMilitaryBuys());
        }
        return ops;
    }

    public Map<Integer, Double> fetchUpdateTz(AllianceLookup lookup, Set<DBNation> nations) {
        Map<Integer, Double> tz = new HashMap<>();
        for (DBAlliance alliance : getAlliances(lookup)) {
            tz.putAll(alliance.fetchUpdateTz(nations));
        }
        return tz;

    }
}
