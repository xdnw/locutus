package link.locutus.discord.util;

/**
 * Source-local rate-limit intent. Each caller area should expose its own enum implementing
 * this contract instead of forwarding raw booleans into {@link RateLimitUtil}. The returned
 * {@link DeferredPriority} should identify that concrete source, not a shared LOW/NORMAL/HIGH
 * style bucket.
 */
public interface RateLimitedSource {
    SendPolicy sendPolicy();

    DeferredPriority deferredPriority();
}

