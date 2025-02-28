package link.locutus.discord.util.scheduler;

import java.util.function.Supplier;

public class CachedSupplier<T> implements Supplier<T> {
    private Supplier<T> resolverOrDelegate;
    public CachedSupplier(final Supplier<T> resolver) {
        this.resolverOrDelegate = new Supplier<T>() {
            @Override
            public T get() {
                T value = resolver.get();
                resolverOrDelegate = () -> value;
                return value;
            }
        };
    }

    @Override
    public T get() {
        return resolverOrDelegate.get();
    }
}
