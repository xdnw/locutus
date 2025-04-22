package link.locutus.discord._main;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.util.scheduler.CaughtTask;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class RepeatingTasks {
    public final Map<Integer, ExecutionInfo> taskMap;
    private final ScheduledThreadPoolExecutor service;
    private final AtomicInteger nextId = new AtomicInteger(0);

    public static class ExecutionInfo {
        public final String name;
        public final int id;
        public long lastExecution;
        public long executionTime;
        public long times;
        public String lastError;

        public ExecutionInfo(String name, int id, long lastExecution, long executionTime) {
            this.name = name;
            this.id = id;
            this.lastExecution = lastExecution;
            this.executionTime = executionTime;
        }
    }

    public RepeatingTasks(ScheduledThreadPoolExecutor service) {
        this.service = service;
        taskMap = new Int2ObjectOpenHashMap<>();
    }

    private int getNextId() {
        return nextId.getAndIncrement();
    }

    public ScheduledFuture<?> addRunnable(String name, Runnable task, long interval, TimeUnit unit) {
        return addTask(name, (CaughtTask) task::run, interval, unit);
    }

    public ScheduledFuture<?> addTask(String name, CaughtTask task, long interval, TimeUnit unit) {
        if (interval <= 0) return null;
        int id = getNextId();

        ExecutionInfo info = new ExecutionInfo(name, id, 0, 0);
        taskMap.put(id, info);
        Runnable delegate = new Runnable() {
            @Override
            public void run() {
                try {
                    long start = System.currentTimeMillis();
                    info.lastExecution = start;
                    info.times++;
                    task.runUnsafe();
                    long end = System.currentTimeMillis();
                    info.executionTime = end - start;
                } catch (Throwable e) {
                    e.printStackTrace();
                    info.executionTime = -1;
                    info.lastError = e.getMessage();
                }
            }
        };
        return service.scheduleWithFixedDelay(delegate, interval, interval, unit);
    }
}
