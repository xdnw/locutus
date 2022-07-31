package link.locutus.discord.apiv1.core;

import link.locutus.discord.apiv1.PoliticsAndWarAPIException;
import link.locutus.discord.config.Settings;

import java.util.*;
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

    public static SimpleBuilder builder() {
        return new SimpleBuilder();
    }

    public static ApiKeyPool<Map.Entry<String, String>> create(String... keys) {
        return builder().addKeys(keys).build();
    }

    public static class SimpleBuilder {
        Map<String, String> pool = new HashMap<>();

        public SimpleBuilder addKey(String key) {
            this.pool.putIfAbsent(key.toLowerCase(Locale.ROOT), null);
            return this;
        }

        public SimpleBuilder addKeys(String... keys) {
            for (String key : keys) addKey(key);
            return this;
        }

        public SimpleBuilder addKeys(List<String> keys) {
            for (String key : keys) addKey(key);
            return this;
        }

        public SimpleBuilder addKey(String key, String botKey) {
            this.pool.put(key.toLowerCase(Locale.ROOT), botKey.toLowerCase(Locale.ROOT));
            return this;
        }

        public ApiKeyPool<Map.Entry<String, String>> build() {
            if (pool.isEmpty()) throw new IllegalArgumentException("No api keys were provided");
            return new ApiKeyPool<>((a, b) -> a.getKey().equalsIgnoreCase(b.getKey()), new ArrayList<>(pool.entrySet()));
        }
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
