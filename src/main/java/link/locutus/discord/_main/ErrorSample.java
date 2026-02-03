package link.locutus.discord._main;

import link.locutus.discord.util.StringMan;

/**
 * One distinct error sample for a task (bounded list, suitable for UI).
 */
public final class ErrorSample {
    public final int fingerprint;
    public final String throwableClass;
    public final String message;     // may be null
    public final String stackTrace;  // truncated
    public final long firstSeenAtMs;
    public final long lastSeenAtMs;
    public final long count;

    ErrorSample(int fingerprint,
                String throwableClass,
                String message,
                String stackTrace,
                long firstSeenAtMs,
                long lastSeenAtMs,
                long count) {
        this.fingerprint = fingerprint;
        this.throwableClass = throwableClass;
        this.message = StringMan.stripApiKey(message);
        this.stackTrace = stackTrace;
        this.firstSeenAtMs = firstSeenAtMs;
        this.lastSeenAtMs = lastSeenAtMs;
        this.count = count;
    }
}
