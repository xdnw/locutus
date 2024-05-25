package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.db.entities.DBNation;

public class ProviderParser<T> implements Parser<T> {
    private final Key<T> key;
    private final T value;

    public ProviderParser(Key<T> key, T value) {
        this.key = key;
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public T apply(ArgumentStack arg) {
        return getValue();
    }

    @Override
    public T apply(ValueStore store, Object t) {
        return getValue();
    }

    @Override
    public boolean isConsumer(ValueStore store) {
        return false;
    }

    @Override
    public Key getKey() {
        return key;
    }

    @Override
    public String getDescription() {
        return null;
    }
}
