package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.db.entities.DBNation;

import java.util.function.Function;

public interface NationPlaceholder<T> extends Function<DBNation,T> {
    String getName();

    Class<T> getType();
}