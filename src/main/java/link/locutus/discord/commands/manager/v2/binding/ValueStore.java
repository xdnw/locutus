package link.locutus.discord.commands.manager.v2.binding;

import io.javalin.http.RedirectResponse;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.util.Map;
import java.util.function.Function;

public interface ValueStore<T> {
    default <V> Parser<V> addDynamicProvider(Key<V> key, Function<ValueStore, V> provider) {
        return addParser(key, new FunctionProviderParser<>(key, provider));
    }

    <V> Parser<V> addParser(Key<V> key, Parser<V> parser);

    default <V> void addProvider(Class<V> clazz, V value) {
        addProvider(Key.of(clazz), value);
    }

    default <V> void addProvider(V value) {
        Key<V> key = Key.of(value.getClass());
        addProvider(key, value);
    }

    default <V> void addProvider(Key<V> key, V value) {
        ProviderParser<V> parser = new ProviderParser<>(key, value);
        addParser(key, parser);
    }

    <V> Parser<V> get(Key<V> key);

    default <V> V getProvided(Class<V> clazz) {
        return getProvided(Key.of(clazz));
    }

    default <V> V getProvided(Key<V> key) {
        return getProvided(key, true);
    }

    default <V> V getProvided(Key<V> key, boolean throwError) {
        V parser = (V) get((Key) key);
        if (parser == null) {
            if (throwError) {
                throw new IllegalArgumentException("No parser for " + key);
            }
            return null;
        }
        try {
            return (V) get((Key) key).apply(this, null);
        } catch (RedirectResponse | IllegalArgumentException | IllegalStateException | NullPointerException response) {
            if (throwError) {
                throw response;
            }
            return null;
        }
    }

    Map<Key, Parser> getParsers();

    default Map<String, Parser> getParsersByName() {
        Map<String, Parser> map = new Object2ObjectLinkedOpenHashMap<>();
        for (Map.Entry<Key, Parser> entry : getParsers().entrySet()) {
            map.put(entry.getKey().toSimpleString(), entry.getValue());
        }
        return map;
    }
}