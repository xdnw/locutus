package link.locutus.discord.apiv3.enums;

import com.google.common.base.Predicates;
import link.locutus.discord.db.entities.DBNation;

import java.util.function.Predicate;

public enum GameTimers {
    CITY(10 * 12, f -> f.getCities() >= 10),
    PROJECT(10 * 12),
    COLOR(5 * 12),
    WAR_POLICY(5 * 12),
    DOMESTIC_POLICY(5 * 12),

    BEIGE_FROM_WAR(2 * 12)

    ;

    private final Predicate<DBNation> filter;
    private final int turns;


    GameTimers(int turns) {
        this(turns, Predicates.alwaysTrue());
    }
    GameTimers(int turns, Predicate<DBNation> applies) {
        this.turns = turns;
        this.filter = applies;
    }

    public int getTurns() {
        return turns;
    }

    public boolean applies(DBNation nation) {
        return filter.test(nation);
    }
}
