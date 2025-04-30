package link.locutus.discord.util.search;

import link.locutus.discord.Logg;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import link.locutus.discord.util.MathMan;

import java.util.concurrent.TimeUnit;
import java.util.function.*;

public class BFSUtil<T> {

    private PriorityQueue<T> originQueue;
    private final Predicate<T> goal;
    private final ToDoubleFunction<T> valueFunction;
    private final Function<Double, Function<T, Double>> valueCompletionFunction;
    private final BiConsumer<T, PriorityQueue<T>> branch;
    private final Consumer<T> garbageLastMax;
    private final T origin;
    private final long timeout;

    private static final long MAX_TIMEOUT = TimeUnit.MINUTES.toMillis(1);

    public BFSUtil(Predicate<T> goal, ToDoubleFunction<T> valueFunction,
                   Function<Double, Function<T, Double>> valueCompletionFunction,
                   BiConsumer<T, PriorityQueue<T>> branch, Consumer<T> garbageLastMax,
                   T origin, final PriorityQueue<T> queue, long timeout) {
        this.originQueue = queue;
        this.goal = goal;
        this.valueFunction = valueFunction;
        this.valueCompletionFunction = valueCompletionFunction;
        this.branch = branch;
        this.garbageLastMax = garbageLastMax;
        this.origin = origin;
        this.timeout = timeout;
    }

    public T search() {
        PriorityQueue<T> queue = originQueue;
        queue.enqueue(origin);

        T max = null;
        double maxValue = Double.NEGATIVE_INFINITY;

        long originalStart = System.currentTimeMillis();
        long start = originalStart;

        double completeFactor = 0;

        Function<T, Double>[] completeValueFuncFinal = new Function[1];

        long delay = 5000;

        int i = 0;
        while (!queue.isEmpty()) {
            T next = queue.dequeue();
            if ((i++ & 0xFFFF) == 0) {
                long diff = System.currentTimeMillis() - start;
                if (diff > timeout) {
                    if (max != null && diff > timeout + 5000) {
                        break;
                    } else if (completeFactor + 0.05 < 2 && valueCompletionFunction != null && max == null) {
                        boolean isNew = completeFactor == 0;
                        completeFactor += 0.01;
                        if (completeFactor >= 0.1) {
                            completeFactor += 0.1;
                        }
                        if (completeFactor >= 0.5) {
                            completeFactor += 0.2;
                        }
                        completeValueFuncFinal[0] = valueCompletionFunction.apply(completeFactor);
                        if (isNew || completeFactor >= 0.1) {
                            PriorityQueue<T> oldQueue = queue;
                            queue = new ObjectHeapPriorityQueue<T>(500000,
                                    (o1, o2) -> {
                                        Function<T, Double> valueFunc = completeValueFuncFinal[0];
                                        return Double.compare(valueFunc.apply(o2), valueFunc.apply(o1));
                                    });
                            while (!oldQueue.isEmpty()) {
                                queue.enqueue(oldQueue.dequeue());
                            }
                            oldQueue.clear();
                            oldQueue = null;
                        }

                        start = System.currentTimeMillis() - timeout + delay;
                    } else if (System.currentTimeMillis() - start > MAX_TIMEOUT) {
                        break;
                    }
                }
            }

            boolean result = goal.test(next);
            if (result) {
                double value = valueFunction.applyAsDouble(next);
                if (value > maxValue) {
                    T lastMax = max;
                    max = next;
                    maxValue = value;

                    if (lastMax != null && lastMax != next) {
                        garbageLastMax.accept(lastMax);
                    }
                } else {
                    garbageLastMax.accept(next);
                }
                continue;
            }

            branch.accept(next, queue);
        }

        long diff = System.currentTimeMillis() - originalStart;
        Logg.text("BFS searched " + i + " options in " + diff + "ms for a rate of " + MathMan.format(i * 1000d / diff) + " per second");
        return max;
    }
}