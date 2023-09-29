package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.lang.reflect.Type;
import java.util.function.Function;

public abstract class TypedFunction<T, V> implements Function<T, V> {
    public abstract Type getType();
    public boolean isResolved() {
        return false;
    }

    public static <T, V> TypedFunction<T, V> create(Type type, V value) {
        return new ResolvedFunction<>(type, value);
    }

    public static <T, V> TypedFunction<T, V> create(Type type, Function<T, V> function) {
        return new TypedFunction<T, V>() {
            @Override
            public Type getType() {
                return type;
            }

            @Override
            public V apply(T t) {
                return function.apply(t);
            }
        };
    }
}