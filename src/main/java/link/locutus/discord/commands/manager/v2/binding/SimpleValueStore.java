package link.locutus.discord.commands.manager.v2.binding;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;

import java.lang.reflect.Type;
import java.util.*;

public class SimpleValueStore<T> implements ValueStore<T> {
    public Map<Type, Map<Key, Parser>> bindings = new LinkedHashMap<>();
    public Set<Type> allowedAnnotations = new ObjectLinkedOpenHashSet<>();

    public SimpleValueStore() {
    }

    @Override
    public <V extends T> Parser<V> addParser(Key<V> key, Parser<V> parser) {
        for (Class<?> type : key.getAnnotationTypes()) {
            if (type == Arg.class) {
                throw new IllegalArgumentException("Cannot add Arg annotation to key " + key);
            }
        }
        allowedAnnotations.addAll(key.getAnnotationTypes());
        bindings.computeIfAbsent(key.getType(), f -> new LinkedHashMap<>()).put(key, parser);
        return parser;
    }

    @Override
    public <V extends T> Parser<V> get(Key<V> key) {
        if (key.getType() == ValueStore.class) {
            return new ProviderParser<>((Key) key, this);
        }
        if (!key.getAnnotationTypes().isEmpty()) {
            Set<Class<?>> types = new ObjectLinkedOpenHashSet<>(key.getAnnotationTypes());
            types.removeIf(f -> !allowedAnnotations.contains(f));
            if (types.size() != key.getAnnotationTypes().size()) {
                key = Key.of(key.getType(), types.toArray(new Class[0]));
            }
        }

        Map<Key, Parser> allowed = bindings.getOrDefault(key.getType(), Collections.emptyMap());

        return allowed.get(key);
    }

    @Override
    public Map<Key, Parser> getParsers() {
        Map<Key, Parser> parsers = new LinkedHashMap<>();
        for (Map<Key, Parser> value : bindings.values()) {
            parsers.putAll(value);
        }
        return parsers;
    }
}
