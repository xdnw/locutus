package link.locutus.discord.apiv1.core;

import link.locutus.discord.apiv1.PoliticsAndWarAPIException;
import link.locutus.discord.config.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class ApiKeyPool<T> {
    private final BiPredicate<T, T> compare;
    private List<T> apiKeyPool;
    private Map<T, AtomicInteger> usageStats;
    private int nextIndex;

    public ApiKeyPool(BiPredicate<T, T> compare, Collection<T> keys) {
        if (compare == null) compare = Object::equals;
        this.compare = compare;
        this.apiKeyPool = new ArrayList<>(keys);
        this.usageStats = new HashMap<>();
        this.nextIndex = 0;
        if (apiKeyPool.size() == 0) {
            throw new PoliticsAndWarAPIException("No API Key provided. Make sure apiKeyPool array is not empty");
        }
    }

    public ApiKeyPool(T... apiKeyPool) {
        this(null, apiKeyPool);
    }

    public ApiKeyPool(BiPredicate<T, T> compare, T... apiKeyPool) {
        this(compare, Arrays.asList(apiKeyPool));
    }

    public List<T> getKeys() {
        return apiKeyPool;
    }

    public synchronized T getNextApiKey() {
        if (this.nextIndex >= this.apiKeyPool.size()) {
            this.nextIndex = 0;
        }
        if (this.apiKeyPool.isEmpty()) throw new IllegalArgumentException("No API key found (Is it set, or out of uses? `"+ Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore API_KEY`)");
        T key = this.apiKeyPool.get(this.nextIndex++);
        usageStats.computeIfAbsent(key, f -> new AtomicInteger()).incrementAndGet();
        return key;
    }

    public synchronized void removeKey(T key) {
        if (apiKeyPool.size() == 1) throw new IllegalArgumentException("Invalid API key");
        this.apiKeyPool.removeIf(f -> compare.test(f, key));
    }

    public Map<T, AtomicInteger> getStats() {
        return usageStats;
    }

    public int size() {
        return apiKeyPool.size();
    }
}
