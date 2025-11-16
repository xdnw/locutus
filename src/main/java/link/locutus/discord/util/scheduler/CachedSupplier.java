package link.locutus.discord.util.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class CachedSupplier<T> implements Supplier<T> {
    private final Supplier<T> resolver;
    private volatile T value;
    private volatile Supplier<T> delegate;

    public static <T> CachedSupplier<T> of(Supplier<T> resolver) {
        return new CachedSupplier<>(resolver);
    }

    public static <T> CachedSupplier<T> preload(Supplier<T> resolver) {
        CachedSupplier<T> cached = new CachedSupplier<>(resolver);
        CompletableFuture.runAsync(cached::get);
        return cached;
    }

    public CachedSupplier(Supplier<T> resolver) {
        this.resolver = resolver;
        this.delegate = this::resolve;
    }

    @Override
    public T get() {
        return delegate.get();
    }

    private T resolve() {
        T v = value;
        if (v == null) {
            synchronized (this) {
                v = value;
                if (v == null) {
                    v = resolver.get();
                    value = v; // safely published via volatile
                }
            }
        }

        final T result = v;
        delegate = () -> result;
        return result;
    }

    public T getOrNull() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    /**
     * Clears the cached value and reinstates the lazy-loading delegate so
     * the next call to {@link #get()} recomputes the value.
     */
    public void unload() {
        synchronized (this) {
            value = null;
            delegate = this::resolve;
        }
    }
}