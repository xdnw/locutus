package link.locutus.discord.util.io;

import link.locutus.discord.RequestTracker;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class PageRequestQueue {
    private RequestTracker tracker;
    private final ScheduledExecutorService service;
    // field of priority queue
    private final PriorityQueue<PageRequestTask<?>> queue;
    private final Object lock = new Object();

    public PageRequestQueue(int threads) {
        // ScheduledExecutorService service
        this.queue = new PriorityQueue<>(Comparator.comparingLong(PageRequestTask::getPriority));
        this.service = Executors.newScheduledThreadPool(threads);
        tracker = new RequestTracker();

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

    public RequestTracker getTracker() {
        return tracker;
    }

    public PriorityQueue<PageRequestTask<?>> getQueue() {
        return queue;
    }

    public void run(PageRequestTask task) {
        if (task != null) {
            try {
                tracker.runWithRetryAfter(task);
            } catch (Throwable e) {
                e.printStackTrace();
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

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority, String urlStr) {
        URI url;
        try {
            url = new URI(urlStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return submit(task, priority, url);
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority, URI url) {
        PageRequestTask<T> request = new PageRequestTask<T>(task, priority, url);
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
        private final URI url;
        public PageRequestTask(Supplier<T> task, long priority, URI uri) {
            this.task = task;
            this.priority = priority;
            this.url = uri;
            checkNotNull(this.url.getHost(), "Invalid URL Host: " + uri);
        }

        public URI getUrl() {
            return url;
        }

        public Supplier<T> getTask() {
            return task;
        }

        public long getPriority() {
            return priority;
        }
    }
}
