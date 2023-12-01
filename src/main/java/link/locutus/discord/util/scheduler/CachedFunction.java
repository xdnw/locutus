package link.locutus.discord.util.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class CachedFunction<I, O> implements Function<I, O> {
    private final Map<I, O> cache = new ConcurrentHashMap<>();
    private final Function<I, O> function;

    public CachedFunction(Function<I, O> function) {
        this.function = function;
    }

    @Override
    public O apply(I input) {
        return cache.computeIfAbsent(input, function);
    }
}