package link.locutus.discord._main;

import link.locutus.discord.util.StringMan;

/**
 * Cheap, dashboard-friendly summary.
 */
public final class TaskSummary {
    public final int id;
    public final String name;

    public final long createdAtMs;
    public final long intervalMs;

    public final boolean running;
    public final long currentRunStartMs; // only meaningful if running==true

    public final long lastRunStartMs;
    public final long lastRunEndMs;
    public final int lastRunDurationMs; // -1 if unknown
    public final byte lastOutcome;   // null if never ran

    public final long totalRuns;
    public final long totalSuccess;
    public final long totalErrors;
    public final long totalInterrupts;
    public final int consecutiveFailures;

    public final long lastSuccessAtMs;
    public final long lastFailureAtMs;

    public final String lastErrorClass;
    public final String lastErrorMessage;

    TaskSummary(
            int id,
            String name,
            long createdAtMs,
            long intervalMs,
            boolean running,
            long currentRunStartMs,
            long lastRunStartMs,
            long lastRunEndMs,
            int lastRunDurationMs,
            byte lastOutcome,
            long totalRuns,
            long totalSuccess,
            long totalErrors,
            long totalInterrupts,
            int consecutiveFailures,
            long lastSuccessAtMs,
            long lastFailureAtMs,
            String lastErrorClass,
            String lastErrorMessage
    ) {
        this.id = id;
        this.name = name;
        this.createdAtMs = createdAtMs;
        this.intervalMs = intervalMs;
        this.running = running;
        this.currentRunStartMs = currentRunStartMs;
        this.lastRunStartMs = lastRunStartMs;
        this.lastRunEndMs = lastRunEndMs;
        this.lastRunDurationMs = lastRunDurationMs;
        this.lastOutcome = lastOutcome;
        this.totalRuns = totalRuns;
        this.totalSuccess = totalSuccess;
        this.totalErrors = totalErrors;
        this.totalInterrupts = totalInterrupts;
        this.consecutiveFailures = consecutiveFailures;
        this.lastSuccessAtMs = lastSuccessAtMs;
        this.lastFailureAtMs = lastFailureAtMs;
        this.lastErrorClass = lastErrorClass;
        this.lastErrorMessage = StringMan.stripApiKey(lastErrorMessage);
    }
}
