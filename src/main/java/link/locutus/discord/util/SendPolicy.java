package link.locutus.discord.util;

/**
 * Controls how a message or action is handled when the bot is near its rate limit.
 * Choose based on the nature of the content, not current load — the rate limit logic adapts.
 */
public enum SendPolicy {

    /**
     * Send immediately, bypassing all rate-limit gating.
     * Use for: interaction/slash command responses, direct replies to user actions.
     * Note: JDA still backs off internally if globally limited — IMMEDIATE just means
     * we don't add our own delay on top.
     */
    IMMEDIATE,

    /**
     * Send immediately if the channel bucket has capacity; otherwise buffer locally and
     * flush together with any other pending messages for the same channel once the bucket
     * resets. Each flush produces one combined Discord message.
     * Use for: alerts and notifications that can tolerate a short delay (~seconds).
     */
    CONDENSE,

    /**
     * Hold in our local queue until global pressure clears (global limit not active and
     * inflight count below the non-priority threshold), then send as its own message.
     * Use for: background bulk sends, scheduled reports, non-urgent notifications.
     */
    DEFER,

    /**
     * Send immediately if not close to the rate limit; silently discard otherwise.
     * Use for: low-value informational messages where dropping is preferable to any delay.
     */
    DROP,
}
