package link.locutus.discord.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestRateLimiter;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.SequentialRestRateLimiter;
import okhttp3.Response;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps JDA's SequentialRestRateLimiter to expose per-bucket and global rate-limit
 * state for use by RateLimitUtil.
 *
 * JDA still drives all rate-limit compliance — this class only piggybacks on responses
 * to maintain a queryable snapshot of remaining capacity per route, and tracks how many
 * requests are currently in-flight (submitted to JDA but not yet responded to).
 *
 * Register during JDA setup:
 *   JDABuilder.create(token, intents)
 *       .setRateLimiterFactory(InstrumentedRateLimiter::new)
 *       .build();
 */
public class InstrumentedRateLimiter implements RestRateLimiter {

    private final SequentialRestRateLimiter delegate;
    private final GlobalRateLimit globalRateLimit;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // Keyed by "METHOD/compiled/route/with/major/params", e.g. "POST/channels/123/messages"
    private static final ConcurrentHashMap<String, BucketSnapshot> bucketsByRoute = new ConcurrentHashMap<>();
    private static final Set<GlobalRateLimit> globalRateLimits = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger activeLimiterCount = new AtomicInteger(0);

    // Requests submitted to JDA's queue but not yet responded to.
    private static final AtomicInteger inflightCount = new AtomicInteger(0);

    // -------------------------------------------------------------------------
    // BucketSnapshot
    // -------------------------------------------------------------------------

    /**
     * Immutable snapshot of a rate-limit bucket from the last observed response.
     * 'remaining' may be stale by the number of in-flight requests to this route.
     */
    public record BucketSnapshot(int remaining, int limit, long resetAtMs) {

        public boolean isExhausted() {
            return remaining <= 0 && System.currentTimeMillis() < resetAtMs;
        }

        public long msUntilReset() {
            return Math.max(0L, resetAtMs - System.currentTimeMillis());
        }

        /**
         * True if remaining > 1 or the bucket has already reset.
         * Leaves a 1-request safety margin for in-flight requests not yet reflected
         * in the observed 'remaining' value.
         */
        public boolean hasComfortableCapacity() {
            return remaining > 1 || System.currentTimeMillis() >= resetAtMs;
        }
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public InstrumentedRateLimiter(RateLimitConfig config) {
        if (activeLimiterCount.getAndIncrement() == 0) {
            bucketsByRoute.clear();
            inflightCount.set(0);
        }
        this.globalRateLimit = config.getGlobalRateLimit();
        this.delegate = new SequentialRestRateLimiter(config);
        globalRateLimits.add(globalRateLimit);
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * True if JDA has received a global rate-limit signal (classic token-level or
     * Cloudflare IP-level) that hasn't expired yet. Reads from the same GlobalRateLimit
     * objects JDA uses internally across all shards — always accurate, never an approximation.
     */
    public static boolean isGloballyLimited() {
        long now = System.currentTimeMillis();
        for (GlobalRateLimit globalRateLimit : globalRateLimits) {
            if (globalRateLimit.getClassic() > now || globalRateLimit.getCloudflare() > now) {
                return true;
            }
        }
        return false;
    }

    /** Unix ms timestamp at which the active global limit expires. 0 if none active. */
    public static long globalResetAtMs() {
        long resetAtMs = 0L;
        for (GlobalRateLimit globalRateLimit : globalRateLimits) {
            resetAtMs = Math.max(resetAtMs,
                    Math.max(globalRateLimit.getClassic(), globalRateLimit.getCloudflare()));
        }
        return resetAtMs;
    }

    /** Last observed bucket snapshot for POST /channels/{channelId}/messages. */
    public static BucketSnapshot getChannelSendBucket(long channelId) {
        return bucketsByRoute.get("POST/channels/" + channelId + "/messages");
    }

    /**
     * True if the channel send bucket has comfortable remaining capacity, or is unknown
     * (no message sent to this channel yet — conservatively assume capacity available).
     */
    public static boolean channelHasCapacity(long channelId) {
        BucketSnapshot b = getChannelSendBucket(channelId);
        return b == null || b.hasComfortableCapacity();
    }

    /** Number of requests submitted to JDA that have not yet received a response. */
    public static int getInflightCount() {
        return inflightCount.get();
    }

    // -------------------------------------------------------------------------
    // RestRateLimiter implementation
    // -------------------------------------------------------------------------

    @Override
    public void enqueue(Work work) {
        inflightCount.incrementAndGet();
        try {
            delegate.enqueue(new ObservingWork(work));
        } catch (Throwable t) {
            inflightCount.decrementAndGet();
            throw t;
        }
    }

    @Override
    public void stop(boolean shutdown, Runnable callback) {
        delegate.stop(shutdown, () -> {
            releaseSharedState();
            if (callback != null) {
                callback.run();
            }
        });
    }

    @Override
    public boolean isStopped() {
        return delegate.isStopped();
    }

    @Override
    public int cancelRequests() {
        return delegate.cancelRequests();
    }

    private void releaseSharedState() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        globalRateLimits.remove(globalRateLimit);
        if (activeLimiterCount.decrementAndGet() == 0) {
            bucketsByRoute.clear();
            inflightCount.set(0);
            globalRateLimits.clear();
        }
    }

    // -------------------------------------------------------------------------
    // ObservingWork — intercepts execute() to read response headers
    // -------------------------------------------------------------------------

    private static final class ObservingWork implements Work {
        private final Work delegate;
        private final String routeKey;
        private final AtomicBoolean counted = new AtomicBoolean(true);

        ObservingWork(Work delegate) {
            this.delegate = delegate;
            Route.CompiledRoute route = delegate.getRoute();
            this.routeKey = route.getMethod().name() + "/" + route.getCompiledRoute();
        }

        @Override
        public Response execute() {
            try {
                Response response = delegate.execute();
                if (response != null) {
                    observeHeaders(response);
                }
                return response;
            } finally {
                releaseInflight();
            }
        }

        @Override
        public void cancel() {
            try {
                delegate.cancel();
            } finally {
                releaseInflight();
            }
        }

        private void releaseInflight() {
            if (counted.compareAndSet(true, false)) {
                inflightCount.decrementAndGet();
            }
        }

        private void observeHeaders(Response response) {
            String remainingStr  = response.header(REMAINING_HEADER);
            String limitStr      = response.header(LIMIT_HEADER);
            String resetAfterStr = response.header(RESET_AFTER_HEADER);
            if (remainingStr == null || limitStr == null || resetAfterStr == null) return;
            try {
                int remaining  = Integer.parseInt(remainingStr);
                int limit      = Integer.parseInt(limitStr);
                long resetAtMs = System.currentTimeMillis()
                        + (long) (Double.parseDouble(resetAfterStr) * 1000.0);
                bucketsByRoute.put(routeKey, new BucketSnapshot(remaining, limit, resetAtMs));
            } catch (NumberFormatException ignored) {}
        }

        @Override public Route.CompiledRoute getRoute() { return delegate.getRoute(); }
        @Override public JDA getJDA() { return delegate.getJDA(); }
        @Override public boolean isSkipped() { return delegate.isSkipped(); }
        @Override public boolean isDone() { return delegate.isDone(); }
        @Override public boolean isPriority() { return delegate.isPriority(); }
        @Override public boolean isCancelled() { return delegate.isCancelled(); }
    }
}