package link.locutus.discord.commands.rankings.builder;

import link.locutus.discord.util.MathMan;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GroupedRankBuilder<T, G> {
    private final Map<T, List<G>> mapped;

    public GroupedRankBuilder() {
        this.mapped = new LinkedHashMap<>();
    }

    public GroupedRankBuilder(Map<T, List<G>> map) {
        this.mapped = map;
    }

    public Map<T, List<G>> get() {
        return mapped;
    }

    public <H> GroupedRankBuilder<T, H> adaptValues(BiFunction<T, G, H> adapter) {
        for (Map.Entry<T, List<G>> entry : mapped.entrySet()) {
            List<G> value = entry.getValue();
            value.replaceAll(u -> (G) adapter.apply(entry.getKey(), u));
        }
        return (GroupedRankBuilder<T, H>) this;
    }

    public <K, V extends Number> NumericMappedRankBuilder<T, K, V> map(BiFunction<T, G, K> groupBy, BiFunction<T, G, V> toNumber) {
        NumericMappedRankBuilder<T, K, V> result = new NumericMappedRankBuilder<>();
        for (Map.Entry<T, List<G>> entry : mapped.entrySet()) {
            T t = entry.getKey();
            List<G> value = entry.getValue();
            LinkedHashMap<K, V> map = new LinkedHashMap<>();
            for (G item : value) {
                K key = groupBy.apply(t, item);
                V add = toNumber.apply(t, item);
                map.put(key, (V) MathMan.add(add, map.get(key)));
            }
            result.put(t, map);
        }
        return result;
    }

    public void put(T key, G value) {
        List<G> existing = mapped.get(key);
        if (existing == null) {
            existing = new ArrayList<>(1);
            existing.add(value);
            mapped.put(key, existing);
        } else {
            existing.add(value);
        }
    }

    public <H extends Number> SummedMapRankBuilder<T, H> sumValues(Function<G, H> toNumber) {
        LinkedHashMap<T, H> copy = new LinkedHashMap<>();
        for (Map.Entry<T, List<G>> entry : mapped.entrySet()) {
            List<G> value = entry.getValue();
            H total = null;
            for (G item : value) {
                H other = toNumber.apply(item);
                total = total == null ? other : (H) MathMan.add(total, other);
            }
            copy.put(entry.getKey(), total);
        }
        return new SummedMapRankBuilder<>(copy);
    }

    public <H extends Number> SummedMapRankBuilder<T, H> sum(BiFunction<T, List<G>, H> toNumber) {
        LinkedHashMap<T, H> copy = new LinkedHashMap<>();
        for (Map.Entry<T, List<G>> entry : mapped.entrySet()) {
            List<G> value = entry.getValue();
            H total = toNumber.apply(entry.getKey(), value);
            copy.put(entry.getKey(), total);
        }
        return new SummedMapRankBuilder<>(copy);
    }
}
