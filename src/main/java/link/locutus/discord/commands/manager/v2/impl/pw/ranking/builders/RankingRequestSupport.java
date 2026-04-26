package link.locutus.discord.commands.manager.v2.impl.pw.ranking.builders;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

final class RankingRequestSupport {
    private RankingRequestSupport() {
    }

    static Set<DBAlliance> alliancesOrAll(Set<DBAlliance> alliances) {
        Set<DBAlliance> source = alliances == null || alliances.isEmpty()
                ? Locutus.imp().getNationDB().getAlliances()
                : alliances;
        return immutableSet(source);
    }

    static NationList nationListOrAll(NationList nationList) {
        if (nationList != null) {
            return nationList;
        }
        return new SimpleNationList(Locutus.imp().getNationDB().getAllNations()).setFilter("*");
    }

    static Set<Integer> allianceIds(Collection<DBAlliance> alliances) {
        if (alliances == null || alliances.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            if (alliance != null) {
                result.add(alliance.getAlliance_id());
            }
        }
        if (result.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(result);
    }

    static Set<Integer> nationIds(Collection<DBNation> nations) {
        if (nations == null || nations.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (DBNation nation : nations) {
            if (nation != null) {
                result.add(nation.getNation_id());
            }
        }
        if (result.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(result);
    }

    static Set<NationOrAlliance> coalition(Set<NationOrAlliance> coalition) {
        return immutableSet(coalition);
    }

    static Set<DBNation> nullableNationSet(Set<DBNation> coalition) {
        Set<DBNation> resolved = immutableSet(coalition);
        return resolved.isEmpty() ? null : resolved;
    }

    static <T> Set<T> optionalSet(Set<T> values) {
        Set<T> resolved = immutableSet(values);
        return resolved.isEmpty() ? null : resolved;
    }

    static <T> Set<T> immutableSet(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        LinkedHashSet<T> resolved = new LinkedHashSet<>();
        for (T value : values) {
            if (value != null) {
                resolved.add(value);
            }
        }
        if (resolved.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(resolved);
    }
}
