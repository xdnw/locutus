package link.locutus.discord.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.requests.RestRateLimiter;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.requests.SequentialRestRateLimiter;
import okhttp3.Response;

import java.util.concurrent.ConcurrentHashMap;
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

    private static volatile InstrumentedRateLimiter INSTANCE;

    private final SequentialRestRateLimiter delegate;
    private final GlobalRateLimit globalRateLimit;

    // Keyed by "METHOD/compiled/route/with/major/params", e.g. "POST/channels/123/messages"
    private static final ConcurrentHashMap<String, BucketSnapshot> bucketsByRoute = new ConcurrentHashMap<>();

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
        bucketsByRoute.clear();
        inflightCount.set(0);
        this.globalRateLimit = config.getGlobalRateLimit();
        this.delegate = new SequentialRestRateLimiter(config);
        INSTANCE = this;
    }

    public static InstrumentedRateLimiter getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Query API
    // -------------------------------------------------------------------------

    /**
     * True if JDA has received a global rate-limit signal (classic token-level or
     * Cloudflare IP-level) that hasn't expired yet. Reads from the same GlobalRateLimit
     * object JDA uses internally — always accurate, never an approximation.
     */
    public boolean isGloballyLimited() {
        long now = System.currentTimeMillis();
        return globalRateLimit.getClassic() > now || globalRateLimit.getCloudflare() > now;
    }

    /** Unix ms timestamp at which the active global limit expires. 0 if none active. */
    public long globalResetAtMs() {
        return Math.max(globalRateLimit.getClassic(), globalRateLimit.getCloudflare());
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
        delegate.stop(shutdown, callback);
    }

    @Override
    public boolean isStopped() {
        return delegate.isStopped();
    }

    @Override
    public int cancelRequests() {
        return delegate.cancelRequests();
    }

    // -------------------------------------------------------------------------
    // ObservingWork — intercepts execute() to read response headers
    // -------------------------------------------------------------------------

    private static final class ObservingWork implements Work {
        private final Work delegate;
        private final String routeKey;

        ObservingWork(Work delegate) {
            this.delegate = delegate;
            Route.CompiledRoute route = delegate.getRoute();
            // e.g. "POST/channels/123456789/messages"
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
                inflightCount.decrementAndGet();
            }
        }

        private void observeHeaders(Response response) {
            String remainingStr  = response.header(REMAINING_HEADER);    // X-RateLimit-Remaining
            String limitStr      = response.header(LIMIT_HEADER);        // X-RateLimit-Limit
            String resetAfterStr = response.header(RESET_AFTER_HEADER);  // X-RateLimit-Reset-After
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
        @Override public void cancel() { delegate.cancel(); }
    }
}