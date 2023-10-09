package link.locutus.discord.db.handlers;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AttackQuery {

    public Map<Integer, DBWar> wars;
    public Predicate<AttackType> attackTypeFilter;
    public Predicate<AbstractCursor> preliminaryFilter;
    public Predicate<AbstractCursor> attackFilter;

    public AttackQuery() {

    }

    public WarDB getDb() {
        return Locutus.imp().getWarDb();
    }

    public AttackQuery withWars(Map<Integer, DBWar> wars) {
        this.wars = wars;
        return this;
    }

    public AttackQuery withWarSet(Function<WarDB, Set<DBWar>> dbConsumer) {
        wars = dbConsumer.apply(getDb()).stream().collect(Collectors.toMap(DBWar::getWarId, Function.identity()));
        return this;
    }

    public AttackQuery withWarMap(Function<WarDB, Map<Integer, DBWar>> dbConsumer) {
        wars = dbConsumer.apply(getDb());
        return this;
    }

    public AttackQuery withWars(Collection<DBWar> wars) {
        this.wars = wars.stream().collect(Collectors.toMap(DBWar::getWarId, Function.identity()));
        return this;
    }

    public AttackQuery withWar(DBWar war) {
        this.wars = Collections.singletonMap(war.getWarId(), war);
        return this;
    }

    public AttackQuery afterDate(long start) {
        long warCutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.entrySet().removeIf(e -> e.getValue().getDate() < warCutoff);
        appendPreliminaryFilter(f -> f.getDate() >= start);
        return this;
    }

    public AttackQuery beforeDate(long end) {
        wars.entrySet().removeIf(e -> e.getValue().getDate() > end);
        appendPreliminaryFilter(f -> f.getDate() <= end);
        return this;

    }

    public AttackQuery between(long start, long end) {
        long warCutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.entrySet().removeIf(e -> e.getValue().getDate() < warCutoff || e.getValue().getDate() > end);
        appendPreliminaryFilter(f -> f.getDate() >= start && f.getDate() <= end);
        return this;
    }

    public AttackQuery withType(AttackType type) {
        appendAttackFilter(f -> f.getAttack_type() == type);
        return this;
    }

    public AttackQuery withWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        wars = getDb().getWarsForNationOrAlliance(nations, alliances, warFilter);
        return this;
    }

    public AttackQuery withActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        wars = getDb().getActiveWars(nationId, warPredicate);
        return this;
    }

    public AttackQuery withWars(Predicate<DBWar> warFilter) {
        wars = getDb().getWars(warFilter);
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
        return this.withWars(getDb().getWars());
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
}

