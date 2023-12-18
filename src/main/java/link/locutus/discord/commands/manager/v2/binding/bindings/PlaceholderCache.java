package link.locutus.discord.commands.manager.v2.binding.bindings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class PlaceholderCache<T> {
    private final List<T> list;
    private boolean cached = false;
    private final Map<String, Map<T, Object>> cacheInstance = new Object2ObjectOpenHashMap<>();
    private final Map<String, Object> cacheGlobal = new Object2ObjectOpenHashMap<>();

    public PlaceholderCache(Collection<T> set) {
        this.list = new ObjectArrayList<>(new ObjectOpenHashSet<>(set));
    }

    public List<T> getList() {
        return list;
    }

    public static <V, T> V getGlobal(ValueStore store, Class<T> clazz, String key, Supplier<V> create) {
        PlaceholderCache<T> cache = (PlaceholderCache<T>) store.getProvided(Key.of(PlaceholderCache.class, clazz), false);
        return getGlobal(cache, key, create);
    }

    public static <V, T> V getGlobal(PlaceholderCache<T> cache, String key, Supplier<V> create) {
        if (cache == null) {
            return create.get();
        }
        return (V) cache.cacheGlobal.computeIfAbsent(key, k -> create.get());
    }

    public static <V, T> V get(ValueStore store, Class<T> clazz, T obj, String key, Function<List<T>, List<V>> getAll) {
        PlaceholderCache<T> cache = (PlaceholderCache<T>) store.getProvided(Key.of(PlaceholderCache.class, clazz), false);
        return get(cache, obj, key, getAll);
    }

    public static <V, T> V get(PlaceholderCache<T> cache, T obj, String key, Function<List<T>, List<V>> getAll) {
        return get(cache, obj, key, getAll, f -> getAll.apply(Collections.singletonList(f)).get(0));
    }

    public static <V, T> V get(PlaceholderCache<T> cache, T obj, String key, Function<List<T>, List<V>> getAll, Function<T, V> getSingle) {
        if (cache == null) {
            return getSingle.apply(obj);
        }
        Map<T, Object> map = cache.cacheInstance.computeIfAbsent(key, o -> new Object2ObjectOpenHashMap<>());
        if (map.containsKey(obj)) {
            return (V) map.get(obj);
        }
        if (!cache.cached && cache.list != null && !cache.list.isEmpty()) {
            cache.cached = true;
            if (cache.list.size() == 1) {
                T first = cache.list.get(0);
                map.put(first, getSingle.apply(first));
            } else {
                List<V> list = getAll.apply(cache.list);
                for (int i = 0; i < cache.list.size(); i++) {
                    map.put(cache.list.get(i), list.get(i));
                }
            }
        }
        return (V) map.computeIfAbsent(obj, getSingle);
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
