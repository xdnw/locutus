package link.locutus.discord.util.io;

import link.locutus.discord.Logg;
import link.locutus.discord.apiv3.RequestTracker;
import link.locutus.discord.config.Settings;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class PageRequestQueue implements AutoCloseable {
    private final RequestTracker tracker;
    private final ScheduledExecutorService service;
    private final PriorityQueue<PageRequestTask<?>> queue;
    private final Object lock = new Object();
    private final Set<Integer> inFlightDomains = new HashSet<>();

    private final AtomicInteger delayIncrement = new AtomicInteger(0);

    public PageRequestQueue(int threads) {
        this.queue = new PriorityQueue<>(Comparator.comparingLong(PageRequestTask::getPriority));
        this.service = Executors.newScheduledThreadPool(threads);
        tracker = new RequestTracker();

        for (int i = 0; i < threads; i++) {
            service.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (!service.isShutdown()) {
            PageRequestTask<?> task;
            try {
                task = awaitTask();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (task == null) {
                continue;
            }
            run(task);
        }
    }

    private PageRequestTask<?> awaitTask() throws InterruptedException {
        synchronized (lock) {
            while (!service.isShutdown()) {
                AtomicLong waitTime = new AtomicLong();
                PageRequestTask<?> task = findAndRemoveTask(waitTime);
                if (task != null) {
                    return task;
                }
                if (queue.isEmpty()) {
                    lock.wait();
                    continue;
                }
                long wait = waitTime.get();
                if (wait <= 0) {
                    wait = (delayIncrement.addAndGet(100) % 900) + 100;
                }
                lock.wait(wait);
            }
        }
        return null;
    }

    private PageRequestTask<?> findAndRemoveTask(AtomicLong waitTime) {
        if (queue.isEmpty()) {
            return null;
        }
        PageRequestTask<?> task = findTask(waitTime);
        if (task != null) {
            queue.remove(task);
            inFlightDomains.add(tracker.getDomainId(task.getUrl()));
        }
        return task;
    }

    private PageRequestTask<?> findTask(AtomicLong waitTime) {
        long minWait = Long.MAX_VALUE;

        long now = System.currentTimeMillis();
        long oneMinute = now - TimeUnit.MINUTES.toMillis(1);

        PageRequestTask<?> firstDelayTask = null;
        PageRequestTask<?> firstBufferTask = null;
        PageRequestTask<?> firstTask = null;
        List<PageRequestTask<?>> completedTasks = null;

        PageRequestTask<?>[] elems = queue.toArray(new PageRequestTask<?>[0]);
        if (elems.length > 1) {
            Arrays.sort(elems, Comparator.comparingLong(PageRequestTask::getPriority));
        }

        for (PageRequestTask<?> task : elems) {
            if (task.isDone()) {
                if (completedTasks == null) {
                    completedTasks = new ArrayList<>();
                }
                completedTasks.add(task);
                continue;
            }
            int domainId = tracker.getDomainId(task.getUrl());
            if (inFlightDomains.contains(domainId)) {
                continue;
            }

            long nextEligibleAt = Math.max(task.getNextEligibleAt(), tracker.getRetryAfter(domainId));
            if (nextEligibleAt > now) {
                minWait = Math.min(minWait, nextEligibleAt - now);
                continue;
            }

            if (!tracker.hasRateLimiting(domainId)) {
                removeCompletedTasks(completedTasks);
                return task;
            }

            long timeStart = System.currentTimeMillis();
            int minuteCount = tracker.getDomainRequestsSince(task.getUrl(), oneMinute);
            long timeEnd = System.currentTimeMillis() - timeStart;
            if (timeEnd > 0) {
                Logg.info("Took " + timeEnd + "ms to get minute count for " + task.getUrl());
            }
            int maxCount = minuteCount;

            long submitDate = task.getCreationDate();
            long bufferMs = task.getAllowBuffering();
            long delayMs = task.getAllowDelay();

            if (bufferMs == 0 && delayMs == 0) {
                removeCompletedTasks(completedTasks);
                return task;
            }

            if (maxCount < 30) {
                removeCompletedTasks(completedTasks);
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

        removeCompletedTasks(completedTasks);
        if (firstDelayTask != null) {
            return firstDelayTask;
        }
        if (firstBufferTask != null) {
            return firstBufferTask;
        }
        if (firstTask != null) {
            return firstTask;
        }
        if (minWait != Long.MAX_VALUE) {
            waitTime.set(Math.min(60000, Math.max(1, minWait)));
        }
        return null;
    }

    private void removeCompletedTasks(List<PageRequestTask<?>> completedTasks) {
        if (completedTasks == null || completedTasks.isEmpty()) {
            return;
        }
        queue.removeAll(completedTasks);
    }

    public RequestTracker getTracker() {
        return tracker;
    }

    public List<PageRequestTask<?>> getQueue() {
        synchronized (lock) {
            return new ArrayList<>(queue);
        }
    }

    public <T> void run(PageRequestTask<T> task) {
        if (task != null) {
            int domainId = tracker.getDomainId(task.getUrl());
            boolean requeue = false;
            try {
                T result = tracker.runWithRetryAfter(task);
                task.complete(result);
            } catch (RequestTracker.RetryableRequestException e) {
                if (!task.isDone()) {
                    task.deferUntil(e.getRetryAtMillis());
                    requeue = true;
                }
            } catch (Throwable e) {
                e.printStackTrace();
                task.completeExceptionally(e);
            } finally {
                synchronized (lock) {
                    inFlightDomains.remove(domainId);
                    if (requeue) {
                        queue.add(task);
                    }
                    lock.notifyAll();
                }
            }
        }
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, PagePriority taskEnum, long priority, int allowBuffering, int allowDelay, String urlStr) {
        URI url;
        try {
            url = new URI(urlStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return submit(task, taskEnum, priority, allowBuffering, allowDelay, url);
    }

    public <T> PageRequestTask<T> submit(Supplier<T> task, PagePriority taskEnum, long priority, int allowBuffering, int allowDelay, URI url) {
        return submit(new PageRequestTask<>(task, taskEnum, priority, allowBuffering, allowDelay, url));
    }

    public <T> PageRequestTask<T> submit(PageRequestTask<T> request) {
        if (!Settings.INSTANCE.ENABLED_COMPONENTS.USE_API) {
            throw new IllegalArgumentException("Cannot use get() when USE_API is disabled.");
        }
        synchronized (lock) {
            queue.add(request);
            lock.notifyAll();
        }
        return request;
    }

    public int size() {
        synchronized (lock) {
            return queue.size();
        }
    }

    @Override
    public void close() {
        service.shutdownNow();
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public static class PageRequestTask<T> extends CompletableFuture<T> {
        private final Supplier<T> task;
        private final long priority;
        private final URI url;
        private final int allowBuffering;
        private final int allowDelay;
        private final long creationDate;
        private final PagePriority taskEnum;
        private volatile long nextEligibleAt;
        private int rateLimitAttempts;

        public PageRequestTask(Supplier<T> task, PagePriority taskEnum, long priority, int allowBuffering, int allowDelay, URI uri) {
            this.creationDate = System.currentTimeMillis();
            this.allowBuffering = allowBuffering;
            this.allowDelay = allowDelay;
            this.task = task;
            this.taskEnum = taskEnum;
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

        public PagePriority getTaskEnum() {
            return taskEnum;
        }

        public long getNextEligibleAt() {
            return nextEligibleAt;
        }

        public synchronized void deferUntil(long timestamp) {
            nextEligibleAt = Math.max(nextEligibleAt, timestamp);
        }

        public synchronized void clearDeferral() {
            nextEligibleAt = 0;
        }

        public synchronized int incrementRateLimitAttempts() {
            return ++rateLimitAttempts;
        }

        public synchronized void resetRateLimitAttempts() {
            rateLimitAttempts = 0;
        }
    }
}
