package link.locutus.discord.commands.manager.v2.binding.bindings;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class ScopedPlaceholderCache<T> {
    private final PlaceholderCache<T> cache;
    private final String method;

    public ScopedPlaceholderCache(PlaceholderCache<T> cache, String method) {
        this.cache = cache;
        this.method = method;
    }

    public List<T> getList(T def) {
        return cache == null ? List.of(def) : cache.getList();
    }

    public <V> V getGlobal(Supplier<V> create) {
        if (cache == null) {
            return create.get();
        }
        return (V) cache.cacheGlobal.computeIfAbsent(method, k -> create.get());
    }

    public <V> V get(T obj, Function<List<T>, List<V>> getAll) {
        return get(obj, getAll, null);
    }

    public <V> V getMap(T obj, Function<List<T>, Map<T, V>> getAll) {
        return getMap(obj, getAll, null);
    }

    public <V> V getMap(T obj, Function<List<T>, Map<T, V>> getAll, Function<T, V> getSingle) {
        Function<List<T>, List<V>> delegate = ts -> {
            Map<T, V> map = getAll.apply(ts);
            List<V> result = new ArrayList<>(ts.size());
            for (T t : ts) {
                result.add(map.get(t));
            }
            return result;
        };
        return get(obj, delegate, getSingle);
    }

    public <V> V get(T obj, Function<List<T>, List<V>> getAll, Function<T, V> getSingle) {
        if (cache == null) {
            if (getSingle == null) {
                return getAll.apply(Collections.singletonList(obj)).get(0);
            }
            return getSingle.apply(obj);
        }
        Map<T, Object> map = cache.cacheInstance.computeIfAbsent(method, o -> {
            Map<T, Object> data = new Object2ObjectOpenHashMap<>();
            if (cache.list != null) {
                if (cache.list.size() == 1 && getSingle != null) {
                    T first = cache.list.get(0);
                    data.put(first, getSingle.apply(first));
                } else {
                    List<V> list = getAll.apply(cache.list);
                    for (int i = 0; i < cache.list.size(); i++) {
                        data.put(cache.list.get(i), list.get(i));
                    }
                }
            } else if (getSingle != null) {
                V value = getSingle.apply(obj);
                data.put(obj, value);
            }
            return data;
        });
        if (map.containsKey(obj)) {
            return (V) map.get(obj);
        }
        if (cache.list == null && getSingle != null) {
            V result = getSingle.apply(obj);
            map.put(obj, result);
            return result;
        }
        return null;
    }
}
