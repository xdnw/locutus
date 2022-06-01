package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.pnw.DBNation;

import java.lang.reflect.Type;
import java.util.function.Function;

public class SimpleNationPlaceholder<T> implements NationPlaceholder<T> {
    private final String name;
    private final Type type;
    private final Function<DBNation, T> func;

    public SimpleNationPlaceholder(String name, Type type, Function<DBNation, T> func) {
        this.name = name;
        this.type = type;
        this.func = func;
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getType() {
        return (Class<T>) type;
    }

    @Override
    public T apply(DBNation nation) {
        return (T) func;
    }
}
