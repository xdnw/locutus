package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.AllianceLookup;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AllianceSnapshotContext implements AllianceLookup {
    private final long snapshotDate;
    private final Supplier<Map<Integer, DBAlliance>> rawAlliancesSupplier;
    private final Supplier<? extends Collection<? extends DBNation>> allNationsSupplier;

    private volatile Map<Integer, DBAlliance> rawAlliances;
    private volatile Map<Integer, DataDumpDBAlliance> materializedAlliances;

    public AllianceSnapshotContext(long snapshotDate, Supplier<Map<Integer, DBAlliance>> rawAlliancesSupplier,
            Supplier<? extends Collection<? extends DBNation>> allNationsSupplier) {
        this.snapshotDate = snapshotDate;
        this.rawAlliancesSupplier = rawAlliancesSupplier;
        this.allNationsSupplier = allNationsSupplier;
    }

    public long getSnapshotDate() {
        return snapshotDate;
    }

    private Map<Integer, DBAlliance> rawAlliances() {
        Map<Integer, DBAlliance> cached = rawAlliances;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = rawAlliances;
            if (cached == null) {
                Map<Integer, DBAlliance> loaded = rawAlliancesSupplier == null ? null : rawAlliancesSupplier.get();
                cached = loaded == null ? Collections.emptyMap() : new Int2ObjectOpenHashMap<>(loaded);
                rawAlliances = cached;
            }
            return cached;
        }
    }

    private Collection<? extends DBNation> allNations() {
        Collection<? extends DBNation> nations = allNationsSupplier == null ? null : allNationsSupplier.get();
        return nations == null ? Collections.emptySet() : nations;
    }

    @Override
    public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
        if (alliances.isEmpty()) {
            return Collections.emptySet();
        }
        Set<DBNation> result = new LinkedHashSet<>();
        for (DBNation nation : allNations()) {
            if (alliances.contains(nation.getAlliance_id())) {
                result.add(nation);
            }
        }
        return result;
    }

    @Override
    public DBAlliance getAlliance(int allianceId) {
        if (allianceId == 0) {
            return null;
        }
        Map<Integer, DataDumpDBAlliance> cached = materializedAlliances;
        if (cached != null) {
            DataDumpDBAlliance existing = cached.get(allianceId);
            if (existing != null) {
                return existing;
            }
        }
        synchronized (this) {
            if (materializedAlliances == null) {
                materializedAlliances = new Int2ObjectOpenHashMap<>();
            }
            DataDumpDBAlliance existing = materializedAlliances.get(allianceId);
            if (existing != null) {
                return existing;
            }

            DBAlliance raw = rawAlliances().get(allianceId);
            DataDumpDBAlliance created;
            if (raw != null) {
                Double snapshotScore = raw instanceof DataDumpDBAlliance dataAlliance ? dataAlliance.getSnapshotScore() : null;
                created = new DataDumpDBAlliance(raw, this, snapshotScore);
            } else {
                created = new DataDumpDBAlliance(allianceId, "AA:" + allianceId, "", "", snapshotDate,
                        NationColor.GRAY, this, null);
            }
            materializedAlliances.put(allianceId, created);
            return created;
        }
    }

    public Set<DBAlliance> getAlliances() {
        Set<Integer> ids = new LinkedHashSet<>(rawAlliances().keySet());
        for (DBNation nation : allNations()) {
            if (nation.getAlliance_id() != 0) {
                ids.add(nation.getAlliance_id());
            }
        }
        Set<DBAlliance> result = new LinkedHashSet<>();
        for (int allianceId : ids) {
            DBAlliance alliance = getAlliance(allianceId);
            if (alliance != null) {
                result.add(alliance);
            }
        }
        return result;
    }

    public int rankFor(DBAlliance alliance, NationFilter filter) {
        Predicate<DBNation> predicate = filter == null ? nation -> true : filter.toCached(snapshotDate);
        Map<Integer, Double> scoresByAlliance = new Int2ObjectOpenHashMap<>();
        for (DBNation nation : allNations()) {
            int allianceId = nation.getAlliance_id();
            if (allianceId == 0 || !predicate.test(nation)) {
                continue;
            }
            scoresByAlliance.merge(allianceId, nation.getScore(), Double::sum);
        }
        java.util.List<Integer> rankedAllianceIds = scoresByAlliance.entrySet().stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue(Comparator.reverseOrder())
                .thenComparingInt(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .toList();
        int index = rankedAllianceIds.indexOf(alliance.getAlliance_id());
        return index == -1 ? Integer.MAX_VALUE : index + 1;
    }
}