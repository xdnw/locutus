package link.locutus.discord.commands.manager.v2.binding;

import java.lang.reflect.Type;
import java.util.*;

public class SimpleValueStore<T> implements ValueStore<T> {
    public Map<Type, Map<Key, Parser>> bindings = new LinkedHashMap<>();
    public Set<Type> allowedAnnotations = new LinkedHashSet<>();

    public SimpleValueStore() {
    }

    @Override
    public <V extends T> Parser<V> addParser(Key<V> key, Parser<V> parser) {
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
            Set<Class<?>> types = new LinkedHashSet<>(key.getAnnotationTypes());
//            List<Annotation> set = new ArrayList<>(Arrays.asList(key.getAnnotations()));

            types.removeIf(f -> !allowedAnnotations.contains(f));
            if (types.size() != key.getAnnotationTypes().size()) {
                key = Key.of(key.getType(), types.toArray(new Class[0]));
            }
        }

        Map<Key, Parser> allowed = bindings.getOrDefault(key.getType(), Collections.emptyMap());

        return allowed.get(key);
    }
}
