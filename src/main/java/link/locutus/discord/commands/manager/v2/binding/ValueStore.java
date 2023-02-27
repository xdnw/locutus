package link.locutus.discord.commands.manager.v2.binding;

import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;

import java.util.function.Function;

public interface ValueStore<T> {
    default <V extends T> Parser<V> addDynamicProvider(Key<V> key, Function<ValueStore, V> provider) {
        return addParser(key, new FunctionProviderParser<>(key, provider));
    }

    <V extends T> Parser<V> addParser(Key<V> key, Parser<V> parser);

    default <V extends T> Parser<V> addProvider(Class<ParametricCallable> clazz, ParametricCallable value) {
        return addProvider(clazz, value);
    }

    default <V extends T> void addProvider(Class<ParameterData> parameterDataClass, V value) {
        Key key = Key.of(value.getClass());
        addProvider(key, value);
    }

    default <V extends T> void addProvider(Key<V> key, V value) {
        ProviderParser<V> parser = new ProviderParser<>(key, value);
        addParser(key, parser);
    }

    <V extends T> Parser<V> get(Key<V> key);

    default <V> V getProvided(Class<V> clazz) {
        return getProvided(Key.of(clazz));
    }

    default <V> V getProvided(Key<V> key) {
        return (V) get((Key) key).apply(this, null);
    }
}
