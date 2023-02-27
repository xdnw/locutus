package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ArgumentStack;

import java.util.function.Function;

public class FunctionProviderParser<T> implements Parser<T> {
    private final Key<T> key;
    private final Function<ValueStore, T> provider;

    public FunctionProviderParser(Key<T> key, Function<ValueStore, T> provider) {
        this.key = key;
        this.provider = provider;
    }

    @Override
    public T apply(ArgumentStack arg) {
        return provider.apply(arg.getStore());
    }

    @Override
    public T apply(ValueStore store, Object t) {
        return provider.apply(store);
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
