package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.lang.reflect.Type;
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

    public static <T, V> TypedFunction<T, V> create(Type type, V value, String name) {
        return new ResolvedFunction<>(type, value, name);
    }

    public static <T, V> TypedFunction<T, V> create(Type type, Function<T, V> function, String name) {
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
            public V apply(T t) {
                return function.apply(t);
            }
        };
    }
}