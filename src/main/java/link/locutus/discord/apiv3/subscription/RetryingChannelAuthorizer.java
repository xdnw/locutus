package link.locutus.discord.apiv3.subscription;

import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.ChannelAuthorizer;

import java.io.IOException;
import java.util.Objects;

/**
 * Delegating ChannelAuthorizer that retries when the underlying authorizer fails
 * with an error that looks like HTTP 429 (rate limited).
 *
 * Usage:
 *   ChannelAuthorizer base = new com.pusher.client.util.HttpChannelAuthorizer("https://.../auth");
 *   ChannelAuthorizer retrying = new RetryingChannelAuthorizer(base, 5, 1000L, true);
 */
public class RetryingChannelAuthorizer implements ChannelAuthorizer {
    private final ChannelAuthorizer delegate;
    private final int maxAttempts;
    private final long baseWaitMillis;
    private final boolean exponentialBackoff;

    public RetryingChannelAuthorizer(ChannelAuthorizer delegate, int maxAttempts, long baseWaitMillis, boolean exponentialBackoff) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseWaitMillis = Math.max(0L, baseWaitMillis);
        this.exponentialBackoff = exponentialBackoff;
    }

    @Override
    public String authorize(final String channelName, final String socketId) throws AuthorizationFailureException {
        AuthorizationFailureException lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.authorize(channelName, socketId);
            } catch (AuthorizationFailureException e) {
                lastEx = e;
                if (attempt >= maxAttempts || !looksLike429(e)) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        // should not reach here normally, but throw the last caught exception if present
        if (lastEx != null) throw lastEx;
        throw new AuthorizationFailureException("Authorization failed after max attempts");
    }

    public static boolean looksLike429(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof IOException) {
                String msg = cur.getMessage();
                if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("too many requests") || msg.contains("HTTP response code: 429"))) {
                    return true;
                }
            } else {
                String msg = cur.getMessage();
                if (msg != null && (msg.contains("429") || msg.toLowerCase().contains("too many requests") || msg.contains("HTTP response code: 429"))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(int attempt) {
        final long wait = exponentialBackoff ? baseWaitMillis * (1L << (attempt - 1)) : baseWaitMillis;
        if (wait <= 0) return;
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
