package com.boydti.discord.commands.rankings.builder;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public class NumericGroupRankBuilder<T, G extends Number> extends GroupedRankBuilder<T, G> {

    public SummedMapRankBuilder<T, Number> average() {
        return sum((i, v) -> v.stream().mapToDouble(a -> a.doubleValue()).average().getAsDouble());
    }

    public SummedMapRankBuilder<T, G> sum() {
        return sumValues(g -> g);
    }
}
