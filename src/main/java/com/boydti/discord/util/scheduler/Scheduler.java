package com.boydti.discord.util.scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface Scheduler {
    <V> Future<V> asyncShort(Runnable task);

    <V> Future<V> asyncShort(Callable<V> task);

    <V> Future<V> async(Runnable task);

    <V> Future<V> async(Callable<V> task);

    void sync(Runnable task);

    <V> V sync(Callable<V> task);

    void tick(long time);
}
