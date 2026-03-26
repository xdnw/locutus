package link.locutus.discord.util;

/**
 * Caller-owned rate-limit intent.
 *
 * <p>The source boundary that decides to submit Discord work must provide one concrete source to
 * {@link RateLimitUtil}. Internal callers should usually reuse the canonical instances in
 * {@link RateLimitedSources}; only keep a dedicated public type when that domain-specific surface
 * is part of the API and materially clearer than a shared constant.</p>
 */
public interface RateLimitedSource {
    SendPolicy sendPolicy();

    DeferredPriority deferredPriority();
}

