package link.locutus.discord.commands.manager.v2.binding.bindings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.db.entities.DBNation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static link.locutus.discord.util.math.ReflectionUtil.getGenericType;

public class PlaceholderCache<T> {
    protected final List<T> list;
    protected boolean cached = false;
    protected final Map<String, Map<T, Object>> cacheInstance = new Object2ObjectOpenHashMap<>();
    protected final Map<String, Object> cacheGlobal = new Object2ObjectOpenHashMap<>();

    public static <T> ValueStore<T> createCache(Collection<T> selection) {
        ValueStore store = new SimpleValueStore();
        return createCache(store, selection);
    }

    public static <T> ValueStore<T> createCache(ValueStore store, Collection<T> selection) {
        if (selection == null || selection.isEmpty()) return store;
        PlaceholderCache<T> cache = new PlaceholderCache<>(selection);
        Class<?> type = getGenericType(selection);
        if (type == null) return store;
        store.addProvider(Key.of(PlaceholderCache.class, type), cache);
        return store;
    }

    public PlaceholderCache(Collection<T> set) {
        this.list = new ObjectArrayList<>(new ObjectOpenHashSet<>(set));
    }

    public List<T> getList() {
        return list;
    }

    public static <T> ScopedPlaceholderCache<T> getScoped(ValueStore store, Class<T> clazz, String method) {
        PlaceholderCache<T> cache = store == null ? null : (PlaceholderCache<T>) store.getProvided(Key.of(PlaceholderCache.class, clazz), false);
        return getScoped(cache, method);
    }

    public static <T> ScopedPlaceholderCache<T> getScoped(PlaceholderCache<T> cache, String method) {
        return new ScopedPlaceholderCache<T>(cache, method);
    }

    public Object get(T object, String id) {
        Map<T, Object> map = cacheInstance.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
        return map.get(object);
    }

    public boolean has(T object, String id) {
        Map<T, Object> map = cacheInstance.get(id);
        return map != null && map.containsKey(object);
    }

    public void put(T object, String id, Object value) {
        Map<T, Object> map = cacheInstance.computeIfAbsent(id, o -> new Object2ObjectOpenHashMap<>());
        map.put(object, value);
    }

    public Object getGlobal(String id) {
        return cacheGlobal.get(id);
    }

    public void putGlobal(String id, Object value) {
        cacheGlobal.put(id, value);
    }
}
