package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.db.entities.DBNation;

import java.util.function.Predicate;

public interface NationFilter extends Predicate<DBNation> {
    String getFilter();

    default Predicate<DBNation> toCached() {
        return toCached(Long.MAX_VALUE);
    }

    default Predicate<DBNation> toCached(long expireAfter) {
        return this;
    }

    default NationFilter recalculate() {
        return this;
    }

    default NationFilter recalculate(long time) {
        return this;
    }
}
