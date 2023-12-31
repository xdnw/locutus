package link.locutus.discord.commands.manager.v2.binding.bindings;

import java.lang.reflect.Type;

public class ResolvedFunction<T, V> extends TypedFunction<T, V> {
    private final V object;
    private final Type type;
    private final String name;

    public ResolvedFunction(Type type, V object, String name) {
        this.type = type;
        this.object = object;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public V get(T t) {
        return object;
    }

    @Override
    public V applyCached(T t) {
        return object;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public final V apply(T t) {
        return object;
    }

    public V get() {
        return object;
    }

    @Override
    public boolean isResolved() {
        return true;
    }
}