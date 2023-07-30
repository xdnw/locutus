package link.locutus.discord.db.handlers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AttackQuery {
    private final WarDB db;

    public long start;
    public long end;
    public Map<Integer, DBWar> wars;
    public Predicate<AttackType> attackTypeFilter;
    public Predicate<AbstractCursor> preliminaryFilter;
    public Predicate<AbstractCursor> attackFilter;

    public AttackQuery(WarDB db) {
        this.db = db;
    }

    public AttackQuery setStart(long start) {
        this.start = start;
        return this;
    }

    public AttackQuery setEnd(long end) {
        this.end = end;
        return this;
    }

    public AttackQuery withWars(Map<Integer, DBWar> wars) {
        this.wars = wars;
        return this;
    }

    public AttackQuery withWars(Set<DBWar> wars) {
        this.wars = wars.stream().collect(Collectors.toMap(DBWar::getWarId, Function.identity()));
        return this;
    }

    public AttackQuery withWar(DBWar war) {
        this.wars = Collections.singletonMap(war.getWarId(), war);
        return this;
    }

    public AttackQuery withWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        Map<Integer, DBWar> result = new Int2ObjectOpenHashMap<>();
        if (alliances != null) {
            synchronized (warsByAllianceId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByAllianceId.entrySet()) {
                    if (alliances.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        if (nations != null) {
            synchronized (warsByNationId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByNationId.entrySet()) {
                    if (nations.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        else if (alliances == null) {
            synchronized (warsById) {
                if (warFilter == null) {

                    result.putAll(warsById);
                } else {
                    for (Map.Entry<Integer, DBWar> warEntry : warsById.entrySet()) {
                        if (warFilter.test(warEntry.getValue())) {
                            result.put(warEntry.getKey(), warEntry.getValue());
                        }
                    }
                }
            }
        }
    }

    public AttackQuery withActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        wars = db.activeWars.getActiveWars(nationId, warPredicate);
    }

    public AttackQuery setWarFilter(Predicate<DBWar> warFilter) {
        this.warFilter = warFilter;
        return this;
    }

    public AttackQuery setAttackTypeFilter(Predicate<AttackType> attackTypeFilter) {
        this.attackTypeFilter = attackTypeFilter;
        return this;
    }

    public AttackQuery setPreliminaryFilter(Predicate<AbstractCursor> preliminaryFilter) {
        this.preliminaryFilter = preliminaryFilter;
        return this;
    }

    public AttackQuery setAttackFilter(Predicate<AbstractCursor> attackFilter) {
        this.attackFilter = attackFilter;
        return this;
    }

    public List<AbstractCursor> getList() {

    }

    public Map<DBWar, List<AbstractCursor>> getMap() {

    }
}

