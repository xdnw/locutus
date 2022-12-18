package link.locutus.discord.util.search;

import link.locutus.discord.apiv1.enums.city.JavaCity;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class BFSUtil {
    public static <T> T search(Function<T, Boolean> goal, Function<T, Double> valueFunction, Function<Double, Function<T, Double>> valueCompletionFunction, BiConsumer<T, PriorityQueue<T>> branch, Consumer<T> garbageLastMax, T origin, long timeout) {
        ObjectArrayFIFOQueue<T> queue = new ObjectArrayFIFOQueue<>(500000) {
            @Override
            public void enqueue(T x) {
                super.enqueue(x);
            }
        };
        return search(goal, valueFunction, valueCompletionFunction, branch, garbageLastMax, origin, queue, timeout);
    }

    public static <T, G> T search(Function<T, Boolean> goal, Function<T, Double> valueFunction, Function<Double, Function<T, Double>> valueCompletionFunction, BiConsumer<T, PriorityQueue<T>> branch, Consumer<T> garbageLastMax, T origin, PriorityQueue<T> queue, long timeout) {
        queue.enqueue(origin);

        T max = null;
        double maxValue = Double.NEGATIVE_INFINITY;

        long start = System.currentTimeMillis();
        long maxTimeout = TimeUnit.MINUTES.toMillis(1);

        double completeFactor = 0;

        Function<T, Double>[] completeValueFuncFinal = new Function[1];

        long delay = 1000;

        int i = 0;
        while (!queue.isEmpty()) {
            T next = queue.dequeue();
            if (next == null) {
                break;
            }
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

                        {
                            Map.Entry<JavaCity, Integer> nextCity = (Map.Entry<JavaCity, Integer>) next;
                            JavaCity city = nextCity.getKey();
                        }

                        start = System.currentTimeMillis() - timeout + delay;
                    } else if (System.currentTimeMillis() - start > maxTimeout) {
                        break;
                    } else {
                        Map.Entry<JavaCity, Integer> nextCity = (Map.Entry<JavaCity, Integer>) next;
                        JavaCity city = nextCity.getKey();
                    }
                }
            }

            Boolean result = goal.apply(next);
            if (result == Boolean.TRUE) {
                Double value = valueFunction.apply(next);
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
            } else if (result == null) {
                garbageLastMax.accept(next);
                continue;
            }

            branch.accept(next, queue);
        }

        return max;
    }
}
