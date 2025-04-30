package link.locutus.discord.commands.manager.v2.binding.bindings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class TypedFunction<T, V> implements Function<T, V> {
    public abstract Type getType();
    public boolean isResolved() {
        return false;
    }

    public abstract String getName();

    @Override
    public String toString() {
        return getName();
    }

    public static <T, V> TypedFunction<T, V> createConstant(Type type, V value, String name) {
        return new ResolvedFunction<>(type, value, name);
    }

    public void clearCache() {

    }

    public V applyCached(T t) {
        if (isResolved()) {
            return get(t);
        } else {
            return apply(t);
        }
    }

    public abstract V get(T t);

    public static <T, V> TypedFunction<T, V> createParent(Type type, Function<T, V> function, String name, TypedFunction<T, ?> parent) {
        return createParents(type, function, name, parent == null ? null : List.of(parent));
    }

    public static <T, V> TypedFunction<T, V> createParents(Type type, Function<T, V> function, String name, Collection<TypedFunction<T, ?>> parents) {
        Map<T, V> resolved = new Object2ObjectOpenHashMap<>();
        return new TypedFunction<T, V>() {
            @Override
            public Type getType() {
                return type;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public V get(T t) {
                return resolved.get(t);
            }

            @Override
            public void clearCache() {
                resolved.clear();
                if (parents != null) {
                    for (TypedFunction parent : parents) {
                        parent.clearCache();
                    }
                }
            }

            @Override
            public V applyCached(T t) {
                if (resolved.containsKey(t)) {
                    return resolved.get(t);
                } else {
                    V v = apply(t);
                    resolved.put(t, v);
                    return v;
                }
            }

            @Override
            public V apply(T t) {
                return function.apply(t);
            }
        };
    }
}