package link.locutus.discord.apiv1.core;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.PoliticsAndWarAPIException;
import link.locutus.discord.db.guild.GuildKey;

import java.util.*;

public class ApiKeyPool {
    private final List<ApiKey> apiKeyPool;
    private int nextIndex;

    /**
     * Creates a new ApiKeyPool with the given keys.
     * @param keys The keys to use.
     */
    public ApiKeyPool(Collection<ApiKey> keys) {
        this.apiKeyPool = new ArrayList<>(keys);
        this.nextIndex = 0;
        if (apiKeyPool.size() == 0) {
            throw new PoliticsAndWarAPIException("No API Key provided, Make sure apiKeyPool array is not empty.");
        }
    }

    public static SimpleBuilder builder() {
        return new SimpleBuilder();
    }

    public static ApiKeyPool create(int nationId, String key) {
        return builder().addKey(nationId, key).build();
    }

    public static ApiKeyPool create(ApiKey key) {
        return builder().addKey(key).build();
    }

    public List<ApiKey> getKeys() {
        return apiKeyPool;
    }

    public synchronized ApiKey getNextApiKey() {
        if (this.nextIndex >= this.apiKeyPool.size()) {
            this.nextIndex = 0;
        }
        if (this.apiKeyPool.isEmpty())
            throw new IllegalArgumentException("No API key found: " + GuildKey.API_KEY.getCommandMention() + "`)");
        ApiKey key = this.apiKeyPool.get(this.nextIndex++);
        key.use();
        return key;
    }

    public synchronized void removeKey(ApiKey key) {
        key.setValid(false);
        if (apiKeyPool.size() == 1) throw new IllegalArgumentException("Invalid API key.");
        this.apiKeyPool.removeIf(f -> f.equals(key));
    }

    public int size() {
        return apiKeyPool.size();
    }

    public static class ApiKey {
        private final String key;
        private int nationId;
        private String botKey;
        private boolean valid;
        private int usage;

        public ApiKey(int nationId, String key, String botKey) {
            this.nationId = nationId;
            this.key = key;
            this.botKey = botKey;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public void deleteApiKey() {
            setValid(false);
            Locutus.imp().getDiscordDB().deleteApiKey(key);
            if (this.botKey != null && !botKey.isEmpty()) {
                Locutus.imp().getDiscordDB().deleteBotKey(key);
            }
        }

        public void deleteBotKey() {
            if (botKey != null) {
                Locutus.imp().getDiscordDB().deleteBotKey(botKey);
            }
            botKey = null;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ApiKey)) return false;
            return ((ApiKey) obj).key.equalsIgnoreCase(key);
        }

        public ApiKey use() {
            usage++;
            return this;
        }

        public int getUsage() {
            return usage;
        }

        public String getKey() {
            return key;
        }

        public String getBotKey() {
            return botKey;
        }

        public int getNationId() {
            if (nationId == -1) {
                nationId = Locutus.imp().getDiscordDB().getNationFromApiKey(key);
            }
            return nationId;
        }
    }

    public static class SimpleBuilder {
        private final Map<String, ApiKey> keys = new LinkedHashMap<>();

        public SimpleBuilder addKey(int nationId, String key) {
            return addKey(nationId, key, null);
        }

        public boolean isEmpty() {
            return keys.isEmpty();
        }

        @Deprecated
        public SimpleBuilder addKeyUnsafe(String key) {
            return addKeyUnsafe(key, null);
        }

        @Deprecated
        public SimpleBuilder addKeyUnsafe(String key, String botKey) {
            return addKey(-1, key, botKey);
        }

        @Deprecated
        public SimpleBuilder addKeysUnsafe(String... keys) {
            for (String key : keys) addKeyUnsafe(key);
            return this;
        }

        public SimpleBuilder addKeys(List<String> keys) {
            for (String key : keys) addKeyUnsafe(key);
            return this;
        }

        public SimpleBuilder addKey(int nationId, String apiKey, String botKey) {
            ApiKey key = new ApiKey(nationId, apiKey, botKey);
            apiKey = apiKey.toLowerCase(Locale.ROOT);
            ApiKey existing = this.keys.get(apiKey);
            if (existing != null && existing.botKey != null) return this;

            this.keys.put(apiKey, key);
            return this;
        }

        public ApiKeyPool build() {
            if (keys.isEmpty()) throw new IllegalArgumentException("No api keys were provided.");
            return new ApiKeyPool(keys.values());
        }

        public SimpleBuilder addKey(ApiKey key) {
            return addKey(key.nationId, key.key, key.botKey);
        }
    }
}
