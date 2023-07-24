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
    private PriorityQueue<PageRequestTask<?>> queue;
    private final Object lock = new Object();

    public PageRequestQueue(int threads) {
        // ScheduledExecutorService service
        this.queue = new PriorityQueue<>(Comparator.comparingLong(PageRequestTask::getPriority));
        this.service = Executors.newScheduledThreadPool(threads);



        for (int i = 0; i < threads; i++) {
            service.submit(() -> {
                while (true) {
                    PageRequestTask<?> task;
                    synchronized (lock) {
                        while (queue.isEmpty()) {
                            try {
                                lock.wait();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        task = queue.poll();
                    }
                    run(task);
                }
            });
        }
    }

    public PriorityQueue<PageRequestTask<?>> getQueue() {
        return queue;
    }

    public void run(PageRequestTask task) {
        if (task != null) {
            try {
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

    public void run() {
        PageRequestTask task;
        synchronized (queue) {
            task = queue.poll();
        }
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority) {
        PageRequestTask<T> request = new PageRequestTask<T>(task, priority);
        synchronized (lock) {
            queue.add(request);
            lock.notifyAll();
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
