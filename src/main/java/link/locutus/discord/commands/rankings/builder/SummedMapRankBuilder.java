package link.locutus.discord.commands.rankings.builder;

import link.locutus.discord.util.MathMan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class SummedMapRankBuilder<T, G extends Number> {
    private Map<T, G> map;

    public SummedMapRankBuilder() {
        this.map = new LinkedHashMap<>();
    }

    public SummedMapRankBuilder(Map<T, G> map) {
        this.map = map;
    }

    public Map<T, G> get() {
        return map;
    }

    public SummedMapRankBuilder<T, G> removeIfKey(Predicate<T> remove) {
        map.entrySet().removeIf(tgEntry -> remove.test(tgEntry.getKey()));
        return this;
    }

    public SummedMapRankBuilder<T, G> adapt(BiFunction<T, G, G> adapter) {
        for (Map.Entry<T, G> entry : map.entrySet()) {
            entry.setValue(adapter.apply(entry.getKey(), entry.getValue()));
        }
        return this;
    }

    public <T2> SummedMapRankBuilder<T2, G> adaptKeys(BiFunction<T, G, T2> adapter) {
        Map<T2, G> newMap = new LinkedHashMap<>();
        for (Map.Entry<T, G> entry : map.entrySet()) {
            newMap.put(adapter.apply(entry.getKey(), entry.getValue()), entry.getValue());
        }
        return new SummedMapRankBuilder<>(newMap);
    }

    public <H> SummedMapRankBuilder<H, G> sum(BiConsumer<Map.Entry<T, G>, SummedMapRankBuilder<H, G>> groupBy) {
        SummedMapRankBuilder<H, G> builder = new SummedMapRankBuilder<>();
        for (Map.Entry<T, G> entry : map.entrySet()) {
            groupBy.accept(entry, builder);
        }
        return builder;
    }

    public <H> NumericGroupRankBuilder<H, G> group(BiConsumer<Map.Entry<T, G>, GroupedRankBuilder<H, G>> groupBy) {
        NumericGroupRankBuilder<H, G> builder = new NumericGroupRankBuilder<>();
        for (Map.Entry<T, G> entry : map.entrySet()) {
            groupBy.accept(entry, builder);
        }
        return builder;
    }

    public G sum() {
        Number total = null;
        for (Map.Entry<T, G> entry : map.entrySet()) {
            if (total == null) {
                total = entry.getValue();
            } else {
                total = MathMan.add(entry.getValue(), total);
            }
        }
        return (G) total;
    }

    public void put(T key, G value) {
        G existing = map.get(key);
        if (existing == null) {
            existing = value;
        } else {
            existing = (G) MathMan.add(existing, value);
        }
        map.put(key, existing);
    }

    public SummedMapRankBuilder<T, G> sort() {
        return sort(Comparator.comparingDouble(o -> -o.getValue().doubleValue()));
    }

    public SummedMapRankBuilder<T, G> sortAsc() {
        return sort(Comparator.comparingDouble(o -> o.getValue().doubleValue()));
    }

    public SummedMapRankBuilder<T, G> sort(Comparator<Map.Entry<T, G>> comparator) {
        ArrayList<Map.Entry<T, G>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, comparator);
        Map<T, G> newMap = new LinkedHashMap<>();
        for (Map.Entry<T, G> entry : list) {
            newMap.put(entry.getKey(), entry.getValue());
        }
        this.map = newMap;
        return this;
    }

    public SummedMapRankBuilder<T, G> sortByValue(Comparator<G> comparator) {
        return this.sort((o1, o2) -> comparator.compare(o1.getValue(), o2.getValue()));
    }

    public SummedMapRankBuilder<T, G> sortByKey(Comparator<T> comparator) {
        return this.sort((o1, o2) -> comparator.compare(o1.getKey(), o2.getKey()));
    }

    public RankBuilder<String> name() {
        return name(entry -> entry.getKey() + ": " + MathMan.format(entry.getValue()));
    }

    public RankBuilder<String> nameKeys(Function<T, String> nameFunc) {
        return name(entry -> nameFunc.apply(entry.getKey()) + ": " + MathMan.format(entry.getValue()));
    }

    public RankBuilder<String> name(Function<T, String> nameKeys, Function<G, String> nameValues) {
        return name(e -> nameKeys.apply(e.getKey()) + ": " + nameValues.apply(e.getValue()));
    }

    public RankBuilder<String> name(Function<Map.Entry<T, G>, String> nameFunc) {
        List<String> names = new ArrayList<>(map.size());
        for (Map.Entry<T, G> entry : map.entrySet()) {
            names.add(nameFunc.apply(entry));
        }
        return new RankBuilder<>(names);
    }
}
