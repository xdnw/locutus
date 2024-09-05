package link.locutus.discord.commands.manager.v2.builder;

import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

public class NumericMappedRankBuilder<T, G, N extends Number> {
    private final Map<T, Map<G, N>> mapped;

    public NumericMappedRankBuilder() {
        this.mapped = new LinkedHashMap<>();
    }

    public NumericMappedRankBuilder(Map<T, Map<G, N>> map) {
        this.mapped = map;
    }

    public void put(T key, Map<G, N> value) {
        Map<G, N> existing = mapped.get(key);
        if (existing == null) {
            existing = value;
        } else {
            existing = ResourceType.add(value, existing);
        }
        mapped.put(key, existing);
    }

    public SummedMapRankBuilder<T, Double> sumResources() {
        return sum(g -> ResourceType.convertedTotal((Map<ResourceType, ? extends Number>) g));
    }

    public <H extends Number> SummedMapRankBuilder<T, H> sum(Function<Map<G, N>, H> toNumber) {
        LinkedHashMap<T, H> copy = new LinkedHashMap<>();
        for (Map.Entry<T, Map<G, N>> entry : mapped.entrySet()) {
            H total = toNumber.apply(entry.getValue());
            copy.put(entry.getKey(), total);
        }
        return new SummedMapRankBuilder<>(copy);
    }

    public <H extends Number> SummedMapRankBuilder<T, H> sumEntires(BiFunction<T, Map<G, N>, H> toNumber) {
        LinkedHashMap<T, H> copy = new LinkedHashMap<>();
        for (Map.Entry<T, Map<G, N>> entry : mapped.entrySet()) {
            H total = toNumber.apply(entry.getKey(), entry.getValue());
            copy.put(entry.getKey(), total);
        }
        return new SummedMapRankBuilder<>(copy);
    }

    public SummedMapRankBuilder<T, Number> average() {
        LinkedHashMap<T, Number> copy = new LinkedHashMap<>();
        for (Map.Entry<T, Map<G, N>> entry : mapped.entrySet()) {
            Map<G, N> map = entry.getValue();
            if (!map.isEmpty()) {
                double total = 0d;
                for (Map.Entry<G, N> gnEntry : map.entrySet()) {
                    total += gnEntry.getValue().doubleValue();
                }
                total /= map.size();
                copy.put(entry.getKey(), total);
            }
        }
        return new SummedMapRankBuilder<>(copy);
    }

    public SummedMapRankBuilder<T, Number> sum() {
        LinkedHashMap<T, Number> copy = new LinkedHashMap<>();
        for (Map.Entry<T, Map<G, N>> entry : mapped.entrySet()) {
            Map<G, N> map = entry.getValue();
            if (!map.isEmpty()) {
                double total = 0d;
                for (Map.Entry<G, N> gnEntry : map.entrySet()) {
                    total += gnEntry.getValue().doubleValue();
                }
                copy.put(entry.getKey(), total);
            }
        }
        return new SummedMapRankBuilder<>(copy);
    }

    public Map<T, Map<G, N>> get() {
        return mapped;
    }
}
