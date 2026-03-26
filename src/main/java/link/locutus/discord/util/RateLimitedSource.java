package link.locutus.discord.util;

/**
 * Source-local rate-limit intent. Each caller area should expose its own enum implementing
 * this contract instead of forwarding raw booleans into {@link RateLimitUtil}.
 */
public interface RateLimitedSource {
    SendPolicy sendPolicy();

    DeferredPriority deferredPriority();
}

