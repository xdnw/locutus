package link.locutus.discord.db.handlers;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AttackQuery {

    private final WarDB warDb;
    public ObjectOpenHashSet<DBWar> wars;
    public Predicate<AttackType> attackTypeFilter;
    public Predicate<AbstractCursor> preliminaryFilter;
    public Predicate<AbstractCursor> attackFilter;

    public AttackQuery(WarDB warDb) {
        this.warDb = warDb;
    }

    public Set<DBWar> getWars() {
        return wars;
    }

    public WarDB getDb() {
        return warDb;
    }

    public AttackQuery withWars(Collection<DBWar> wars) {
        this.wars = wars instanceof ObjectOpenHashSet ? (ObjectOpenHashSet<DBWar>) wars : new ObjectOpenHashSet<>(wars);
        return this;
    }

    public AttackQuery withWars(long start, long end) {
        if (end == Long.MAX_VALUE) {
            if (start <= 0) {
                return withAllWars();
            } else {
                return withWars(getDb().getWars(f -> f.getDate() >= start));
            }
        } else {
            return withWars(getDb().getWars(f -> f.getDate() >= start && f.getDate() <= end));
        }
    }

    public AttackQuery withWars(Map<Integer, DBWar> wars) {
        this.wars = new ObjectOpenHashSet<>(wars.values());
        return this;
    }

    public AttackQuery withWarSet(ObjectOpenHashSet<DBWar> wars) {
        this.wars = wars;
        return this;
    }

    public AttackQuery withWarSet(Function<WarDB, Set<DBWar>> dbConsumer) {
        return withWars(dbConsumer.apply(getDb()));
    }

    public AttackQuery withWarMap(Function<WarDB, Map<Integer, DBWar>> dbConsumer) {
        return withWars(dbConsumer.apply(getDb()));
    }

    public AttackQuery withWar(DBWar war) {
        this.wars = new ObjectOpenHashSet<>(1);
        this.wars.add(war);
        return this;
    }

    public AttackQuery afterDate(long start) {
        long warCutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.removeIf(e -> e.getDate() < warCutoff);
        appendPreliminaryFilter(f -> f.getDate() >= start);
        return this;
    }

    public AttackQuery beforeDate(long end) {
        wars.removeIf(e -> e.getDate() > end);
        appendPreliminaryFilter(f -> f.getDate() <= end);
        return this;

    }

    public AttackQuery between(long start, long end) {
        long warCutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.removeIf(e -> e.getDate() < warCutoff || e.getDate() > end);
        appendPreliminaryFilter(f -> f.getDate() >= start && f.getDate() <= end);
        return this;
    }

    public AttackQuery withType(AttackType type) {
        appendAttackFilter(f -> f.getAttack_type() == type);
        return this;
    }

    public AttackQuery withWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        withWars(getDb().getWarsForNationOrAlliance(nations, alliances, warFilter));
        return this;
    }

    public AttackQuery withActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        wars = getDb().getActiveWars(nationId, warPredicate);
        return this;
    }

    public AttackQuery withWars(Predicate<DBWar> warFilter) {
        withWars(getDb().getWars(warFilter));
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

    public AttackQuery appendPreliminaryFilter(Predicate<AbstractCursor> filter) {
        preliminaryFilter = preliminaryFilter == null ? filter : preliminaryFilter.and(filter);
        return this;
    }

    public AttackQuery appendAttackFilter(Predicate<AbstractCursor> filter) {
        attackFilter = attackFilter == null ? filter : attackFilter.and(filter);
        return this;
    }

    public AttackQuery appendAttackTypeFilter(Predicate<AttackType> filter) {
        attackTypeFilter = attackTypeFilter == null ? filter : attackTypeFilter.and(filter);
        return this;
    }

    public AttackQuery withAllWars() {
        return this.withWarSet(getDb().getWars());
    }

    public AttackQuery withActiveWars() {
        return this.withWars(getDb().getActiveWars());
    }

    public List<AbstractCursor> getList() {
        if (wars == null) {
            withAllWars();
        }
        return getDb().getAttacks(wars, attackTypeFilter, preliminaryFilter, attackFilter);
    }

    public Map<DBWar, List<AbstractCursor>> getMap() {
        if (wars == null) {
            withAllWars();
        }
        return getDb().getAttacksByWar(wars, attackTypeFilter, preliminaryFilter, attackFilter);
    }

    public AttackQuery withTypes(AttackType... types) {
        Set<AttackType> typeSet = new LinkedHashSet<>();
        Collections.addAll(typeSet, types);
        appendAttackTypeFilter(typeSet::contains);
        return this;
    }

    public AttackCost toCost(BiPredicate<DBWar, AbstractCursor> isPrimary,
                             String nameA,
                             String nameB,
                             boolean buildings,
                             boolean ids,
                             boolean victories,
                             boolean logWars,
                             boolean attacks) {
        if (wars == null) {
            withAllWars();
        }
        Predicate<AbstractCursor> attackFilterFinal = attackFilter == null ? f -> true : attackFilter;
        AttackCost cost = new AttackCost(nameA, nameB, buildings, ids, victories, logWars, attacks);
        getDb().iterateAttacks(wars, attackTypeFilter, preliminaryFilter, new Consumer<AbstractCursor>() {
            @Override
            public void accept(AbstractCursor attack) {
                if (!attackFilterFinal.test(attack)) {
                    return;
                }
                DBWar war = wars.get(new DBWar.DBWarKey(attack.getWar_id()));
                cost.addCost(attack, isPrimary.test(war, attack));
            }
        });
        return cost;
    }
}

