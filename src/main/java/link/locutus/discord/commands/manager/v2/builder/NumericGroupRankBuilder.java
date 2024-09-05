package link.locutus.discord.commands.manager.v2.builder;

public class NumericGroupRankBuilder<T, G extends Number> extends GroupedRankBuilder<T, G> {

    public SummedMapRankBuilder<T, Number> average() {
        return sum((i, v) -> v.stream().mapToDouble(a -> a.doubleValue()).average().getAsDouble());
    }

    public SummedMapRankBuilder<T, G> sum() {
        return sumValues(g -> g);
    }
}
