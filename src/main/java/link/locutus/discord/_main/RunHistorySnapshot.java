package link.locutus.discord._main;

/**
 * Snapshot of per-run history (parallel primitive arrays).
 * outcomeCodes use Outcome.code (0=SUCCESS, 1=ERROR, 2=INTERRUPTED).
 */
public final class RunHistorySnapshot {
    public final long[] startTimesMs;
    public final int[] durationsMs;
    public final byte[] outcomeCodes;

    RunHistorySnapshot(long[] startTimesMs, int[] durationsMs, byte[] outcomeCodes) {
        this.startTimesMs = startTimesMs;
        this.durationsMs = durationsMs;
        this.outcomeCodes = outcomeCodes;
    }
}
