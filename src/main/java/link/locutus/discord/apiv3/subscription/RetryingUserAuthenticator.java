package link.locutus.discord.apiv3.subscription;

import com.pusher.client.AuthenticationFailureException;
import com.pusher.client.AuthorizationFailureException;
import com.pusher.client.UserAuthenticator;

import java.io.IOException;
import java.util.Objects;

public class RetryingUserAuthenticator implements UserAuthenticator {
    private final UserAuthenticator delegate;
    private final int maxAttempts;
    private final long baseWaitMillis;
    private final boolean exponentialBackoff;

    public RetryingUserAuthenticator(UserAuthenticator delegate, int maxAttempts, long baseWaitMillis, boolean exponentialBackoff) {
        this.delegate = Objects.requireNonNull(delegate);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseWaitMillis = Math.max(0L, baseWaitMillis);
        this.exponentialBackoff = exponentialBackoff;
    }

    @Override
    public String authenticate(final String socketId) throws AuthenticationFailureException {
        AuthorizationFailureException lastEx = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.authenticate(socketId);
            } catch (AuthorizationFailureException e) {
                lastEx = e;
                if (attempt >= maxAttempts || !RetryingChannelAuthorizer.looksLike429(e)) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        // should not reach here normally, but throw the last caught exception if present
        if (lastEx != null) throw lastEx;
        throw new AuthorizationFailureException("Authorization failed after max attempts");
    }

    private void sleepBeforeRetry(int attempt) {
        long wait = exponentialBackoff ? baseWaitMillis * (1L << (attempt - 1)) : baseWaitMillis;
        if (wait <= 0) return;
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
