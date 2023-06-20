package link.locutus.discord.util.io;

import org.springframework.web.client.HttpClientErrorException;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class PageRequestQueue {

    private final ScheduledExecutorService service;
    // field of priority queue
    private int millisecondsPerPage;
    private PriorityQueue<PageRequestTask<?>> queue;

    private long lastRun = 0;

    public PageRequestQueue(int millisecondsPerPage) {
        // ScheduledExecutorService service
        this.service = Executors.newScheduledThreadPool(1);
        this.millisecondsPerPage = millisecondsPerPage;
        this.queue = new PriorityQueue<>(Comparator.comparingLong(PageRequestTask::getPriority));
        service.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            if (now - lastRun < millisecondsPerPage) return;
            try {
                PageRequestQueue.this.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }, 10, 10, TimeUnit.MILLISECONDS);
    }

    public void run() {
        PageRequestTask task;
        synchronized (queue) {
            task = queue.poll();
        }
        if (task != null) {
            try {
                lastRun = System.currentTimeMillis();
                Supplier supplier = task.getTask();
                task.complete(supplier.get());
            } catch (Throwable e) {
                if (e instanceof HttpClientErrorException.TooManyRequests) {
                    try {
                        Thread.sleep(6000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                task.completeExceptionally(e);
            }
        }
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority) {
        PageRequestTask<T> request = new PageRequestTask<T>(task, priority);
        synchronized (queue) {
            queue.add(request);
        }
        return request;
    }

    public int size() {
        synchronized (queue) {
            return queue.size();
        }
    }

    public static class PageRequestTask<T> extends CompletableFuture<T> {
        private final Supplier<T> task;
        private final long priority;

        public PageRequestTask(Supplier<T> task, long priority) {
            this.task = task;
            this.priority = priority;
        }

        public Supplier<T> getTask() {
            return task;
        }

        public long getPriority() {
            return priority;
        }
    }
}
