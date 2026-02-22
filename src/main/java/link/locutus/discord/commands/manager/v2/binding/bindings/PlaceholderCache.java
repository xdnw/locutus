package link.locutus.discord.commands.manager.v2.binding.bindings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlaceholderCache<T> {
    private static final Key<ScopeIndex> SCOPE_INDEX_KEY = Key.nested(PlaceholderCache.class, ScopeIndex.class);

    public static class ScopeIndex {
        private final Map<Class<?>, PlaceholderCache<?>> byType = new LinkedHashMap<>();

        public Map<Class<?>, PlaceholderCache<?>> getByType() {
            return byType;
        }
    }

    protected final List<T> list;
    protected final Map<MethodIdentity, Map<T, Object>> cacheInstance = new Object2ObjectOpenHashMap<>();
    protected final Map<MethodIdentity, Object> cacheGlobal = new Object2ObjectOpenHashMap<>();

    public static <T> ValueStore<T> createCache(Collection<T> selection, Class<T> type) {
        ValueStore store = new SimpleValueStore();
        return createCache(store, selection, type);
    }

    public static <T> ValueStore<T> createCache(ValueStore store, Collection<T> selection, Class<T> type) {
        if (selection == null || selection.isEmpty())
            return store;
        PlaceholderCache<T> cache = new PlaceholderCache<>(selection);
        if (type == null)
            return store;
        addScope(store, type, cache);
        return store;
    }

    private static ScopeIndex getScopeIndex(ValueStore store, boolean create) {
        if (store == null)
            return null;
        ScopeIndex index = (ScopeIndex) store.getProvided(SCOPE_INDEX_KEY, false);
        if (index == null && create) {
            index = new ScopeIndex();
            store.addProvider(SCOPE_INDEX_KEY, index);
        }
        return index;
    }

    public static <T> void addScope(ValueStore store, Class<T> type, PlaceholderCache<T> cache) {
        if (store == null || type == null || cache == null)
            return;
        store.addProvider(Key.nested(PlaceholderCache.class, type), cache);
        ScopeIndex index = getScopeIndex(store, true);
        index.getByType().put(type, cache);
    }

    public static <T> PlaceholderCache<T> getScope(ValueStore store, Class<T> clazz) {
        return store == null ? null
                : (PlaceholderCache<T>) store.getProvided(Key.nested(PlaceholderCache.class, clazz), false);
    }

    public static <T, B> PlaceholderCache<T> getScopeAssignableTo(ValueStore store, Class<T> fallbackScope,
            Class<B> requiredScope, Class<?> preferredScope) {
        ScopeIndex index = getScopeIndex(store, false);
        if (index != null) {
            if (preferredScope != null) {
                PlaceholderCache<?> exact = index.getByType().get(preferredScope);
                if (exact != null && requiredScope.isAssignableFrom(preferredScope)) {
                    return (PlaceholderCache<T>) exact;
                }
            }

            if (preferredScope != null) {
                for (Map.Entry<Class<?>, PlaceholderCache<?>> entry : index.getByType().entrySet()) {
                    Class<?> scopeType = entry.getKey();
                    if (requiredScope.isAssignableFrom(scopeType) && scopeType.isAssignableFrom(preferredScope)) {
                        return (PlaceholderCache<T>) entry.getValue();
                    }
                }
            }

            for (Map.Entry<Class<?>, PlaceholderCache<?>> entry : index.getByType().entrySet()) {
                if (requiredScope.isAssignableFrom(entry.getKey())) {
                    return (PlaceholderCache<T>) entry.getValue();
                }
            }
        }

        return getScope(store, fallbackScope);
    }

    public static <T, B> ScopedPlaceholderCache<T> getScopedAssignableTo(ValueStore store, Class<T> fallbackScope,
            Class<B> requiredScope, Class<?> preferredScope, MethodIdentity method) {
        PlaceholderCache<T> cache = getScopeAssignableTo(store, fallbackScope, requiredScope, preferredScope);
        return getScoped(cache, method);
    }

    public PlaceholderCache(Collection<T> set) {
        this.list = new ObjectArrayList<>(new ObjectOpenHashSet<>(set));
    }

    public List<T> getList() {
        return list;
    }

    public static <T> ScopedPlaceholderCache<T> getScoped(ValueStore store, Class<T> clazz, MethodIdentity method) {
        PlaceholderCache<T> cache = getScope(store, clazz);
        return getScoped(cache, method);
    }

    public static <T> ScopedPlaceholderCache<T> getScoped(PlaceholderCache<T> cache, MethodIdentity method) {
        return new ScopedPlaceholderCache<T>(cache, method);
    }

    public Object get(T object, MethodIdentity id) {
        Map<T, Object> map = cacheInstance.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
        return map.get(object);
    }

    public boolean has(T object, MethodIdentity id) {
        Map<T, Object> map = cacheInstance.get(id);
        return map != null && map.containsKey(object);
    }

    public void put(T object, MethodIdentity id, Object value) {
        Map<T, Object> map = cacheInstance.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
        map.put(object, value);
    }

    public Object getGlobal(MethodIdentity id) {
        return cacheGlobal.get(id);
    }

    public void putGlobal(MethodIdentity id, Object value) {
        cacheGlobal.put(id, value);
    }
}
