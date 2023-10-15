package link.locutus.discord.util.io;

import link.locutus.discord.RequestTracker;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class PageRequestQueue {
    private RequestTracker tracker;
    private final ScheduledExecutorService service;
    // field of priority queue
    private final PriorityQueue<PageRequestTask<?>> queue;
    private final Object lock = new Object();

    private final AtomicInteger delayIncrement = new AtomicInteger(0);

    public PageRequestQueue(int threads) {
        // ScheduledExecutorService service
        this.queue = new PriorityQueue<>(Comparator.comparingLong(PageRequestTask::getPriority));
        this.service = Executors.newScheduledThreadPool(threads);
        tracker = new RequestTracker();

        for (int i = 0; i < threads; i++) {
            service.submit(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        boolean isEmpty ;
                        synchronized (queue) {
                            isEmpty = queue.isEmpty();
                        }
                        if (isEmpty) {
                            synchronized (lock) {
                                synchronized (queue) {
                                    if (!queue.isEmpty()) {
                                        continue;
                                    }
                                }
                                try {
                                    lock.wait();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                            }
                        }

                        AtomicLong waitTime = new AtomicLong();
                        PageRequestTask<?> task = null;
                        synchronized (queue) {
                            task = findAndRemoveTask(waitTime);
                        }
                        if (task == null) {
                            long wait = waitTime.get();
                            if (wait <= 0) {
                                wait = (delayIncrement.addAndGet(100) % 900) + 100;
                            }
                            synchronized (queue) {
                                if (queue.isEmpty()) {
                                    continue;
                                }
                            }
                            try {
                                Thread.sleep(wait);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            continue;
                        }
                        PageRequestQueue.this.run(task);
                    }
                }
            });
        }
    }

    private PageRequestTask findAndRemoveTask(AtomicLong waitTime) {
        synchronized (queue) {
            if (queue.isEmpty()) return null;
            PageRequestTask task = findTask(waitTime);
            if (task != null) {
                queue.remove(task);
            }
            return task;
        }
    }

    private PageRequestTask findTask(AtomicLong waitTime) {
        long minWait = Long.MAX_VALUE;

        boolean hasNonRateLimitedTask = false;

        long now = System.currentTimeMillis();
        long oneMinute = now - TimeUnit.MINUTES.toMillis(1);
        long fiveMinutes = now - TimeUnit.MINUTES.toMillis(5);

        PageRequestTask firstDelayTask = null;
        PageRequestTask firstBufferTask = null;
        PageRequestTask firstTask = null;

        PageRequestTask[] elems = queue.toArray(new PageRequestTask[0]);
        if (elems.length > 1) {
            Arrays.sort(elems, Comparator.comparingLong(PageRequestTask::getPriority));
        }

        for (PageRequestTask task : elems) {
            if (!tracker.hasRateLimiting(task.getUrl())) {
                return task;
            }
            int domainId = tracker.getDomainId(task.getUrl());
            long retry = tracker.getRetryAfter(domainId);

            if (retry > now) {
                minWait = Math.min(minWait, retry);
                continue;
            }
            hasNonRateLimitedTask = true;

            long timeStart = System.currentTimeMillis();
            int minuteCount = tracker.getDomainRequestsSince(task.getUrl(), oneMinute);
            long timeEnd = System.currentTimeMillis() - timeStart;
            if (timeEnd > 0) {
                System.out.println("Took " + timeEnd + "ms to get minute count for " + task.getUrl());
            }
//            double fiveCount = tracker.getDomainRequestsSince(task.getUrl(), fiveMinutes) / 5d;
            double maxCount = minuteCount;//Math.max(minuteCount, fiveCount);

            System.out.println("Current minute count is " + minuteCount);
            long submitDate = task.getCreationDate();
            long bufferMs = task.getAllowBuffering();
            long delayMs = task.getAllowDelay();

            if (bufferMs == 0 && delayMs == 0) {
                return task;
            }

            if (maxCount < 30) {
                return task;
            }

            long currentDiff = now - submitDate;

            if (currentDiff > delayMs) {
                if (firstDelayTask == null) {
                    firstDelayTask = task;
                }
            }

            if (currentDiff > bufferMs) {
                if (firstBufferTask == null) {
                    firstBufferTask = task;
                }
            }

            if (maxCount <= 59) {
                firstTask = task;
            } else {
                int over = minuteCount - 58;
                minWait = Math.min(TimeUnit.SECONDS.toMillis(over), minWait);
            }
        }

        if (hasNonRateLimitedTask || true) {
            if (firstDelayTask != null) {
                return firstDelayTask;
            }
            if (firstBufferTask != null) {
                return firstBufferTask;
            }
            if (firstTask != null) {
                return firstTask;
            }
        }
        if (minWait != Long.MAX_VALUE) {
            long wait = Math.min(60000, minWait - System.currentTimeMillis());
            waitTime.set(wait);
            return null;
        }
        return null;
    }

    public RequestTracker getTracker() {
        return tracker;
    }

    public List<PageRequestTask<?>> getQueue() {
        synchronized (queue) {
            return new ArrayList<>(queue);
        }
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

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority, int allowBuffering, int allowDelay, String urlStr) {
        URI url;
        try {
            url = new URI(urlStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return submit(task, priority, allowBuffering, allowDelay, url);
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, long priority, int allowBuffering, int allowDelay, URI url) {
        return submit(new PageRequestTask<T>(task, priority, allowBuffering, allowDelay, url));
    }

    public <T> PageRequestTask<T> submit(PageRequestTask<T> request) {
        synchronized (lock) {
            synchronized (queue) {
                queue.add(request);
            }
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
        private final int allowBuffering;
        private final int allowDelay;
        private final long creationDate;

        public PageRequestTask(Supplier<T> task, long priority, int allowBuffering, int allowDelay, URI uri) {
            this.creationDate = System.currentTimeMillis();
            this.allowBuffering = allowBuffering;
            this.allowDelay = allowDelay;
            this.task = task;
            this.priority = priority;
            this.url = uri;
            checkNotNull(this.url.getHost(), "Invalid URL Host: " + uri);
        }

        public long getCreationDate() {
            return creationDate;
        }

        public int getAllowBuffering() {
            return allowBuffering;
        }

        public int getAllowDelay() {
            return allowDelay;
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
