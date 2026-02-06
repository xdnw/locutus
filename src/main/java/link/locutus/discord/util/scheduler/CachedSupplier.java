package link.locutus.discord.util.scheduler;

import link.locutus.discord.util.StringMan;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CachedSupplier<T> implements Supplier<T> {
    private final Supplier<T> resolver;
    private volatile T value;
    private volatile Supplier<T> delegate;

    public static <T> CachedSupplier<T> of(Supplier<T> resolver) {
        return new CachedSupplier<>(resolver);
    }

    public static <T> CachedSupplier<T> ofValue(T value) {
        return new CachedSupplier<>(value);
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

    public CachedSupplier(T value) {
        this.resolver = null;
        this.value = value;
        this.delegate = () -> value;
    }

    public void setValue(T value) {
        synchronized (this) {
            this.value = value;
            this.delegate = () -> value;
        }
    }

    public T setValueIfAbsent(T value) {
        synchronized (this) {
            if (this.value == null) {
                this.value = value;
                this.delegate = () -> value;
            }
            return value;
        }
    }

    public T setValueIfAbsent(Supplier<T> supplier) {
        synchronized (this) {
            if (this.value == null) {
                T value = supplier.get();
                this.value = value;
                this.delegate = () -> value;
            }
            return this.value;
        }
    }

    public T setValueIfAbsentElseApply(Supplier<T> create, Consumer<T> apply) {
        synchronized (this) {
            if (this.value == null) {
                T value = create.get();
                this.value = value;
                this.delegate = () -> value;
                return value;
            } else {
                T v = this.value;
                apply.accept(v);
                return v;
            }
        }
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
    public boolean unload() {
        synchronized (this) {
            if (value == null) {
                return false; // already unloaded
            }
            value = null;
            delegate = this::resolve;
            return true;
        }
    }
}