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

public class ApiKeyPool {
    private List<String> apiKeyPool;
    private Map<String, AtomicInteger> usageStats;
    private int nextIndex;

    public ApiKeyPool(Collection<String> keys) {
        this(keys.toArray(new String[0]));
    }

    public ApiKeyPool(String... apiKeyPool) {
        this.apiKeyPool = new ArrayList<>(Arrays.asList(apiKeyPool));
        this.usageStats = new HashMap<>();
        this.nextIndex = 0;
        if (apiKeyPool.length == 0) {
            throw new PoliticsAndWarAPIException("No API Key provided. Make sure apiKeyPool array is not empty");
        }
    }

    public List<String> getKeys() {
        return apiKeyPool;
    }

    public synchronized String getNextApiKey() {
        if (this.nextIndex >= this.apiKeyPool.size()) {
            this.nextIndex = 0;
        }
        if (this.apiKeyPool.isEmpty()) throw new IllegalArgumentException("No API key found (Is it set, or out of uses? `"+ Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore API_KEY`)");
        String key = this.apiKeyPool.get(this.nextIndex++);
        usageStats.computeIfAbsent(key, f -> new AtomicInteger()).incrementAndGet();
        return key;
    }

    public synchronized void removeKey(String key) {
        if (apiKeyPool.size() == 1) throw new IllegalArgumentException("Cannot remove last remaining key");
        this.apiKeyPool.removeIf(f -> f.equalsIgnoreCase(key));
    }


    public Map<String, AtomicInteger> getStats() {
        return usageStats;
    }
}
