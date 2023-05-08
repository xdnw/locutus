package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.db.entities.DBNation;

import java.util.function.Predicate;

public interface NationFilter extends Predicate<DBNation> {
    String getFilter();

    default Predicate<DBNation> toCached(long expireAfter) {
        return this;
    }
}
