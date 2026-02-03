package link.locutus.discord._main;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.CaughtTask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules repeating tasks and records bounded "health telemetry" suitable for a dashboard:
 * - last run start/end/duration + outcome
 * - running/stuck indication (current run start time)
 * - counters: runs/success/errors/interrupts + consecutive failures
 * - per-run history ring buffer (timestamps + durations + outcomes), bounded by:
 *      - retention window (e.g. last 24h)
 *      - maxSamples cap
 * - last N distinct stack traces (default 3), with counts + last seen timestamps
 */
public final class RepeatingTasks {

    // --------------------------------------------
    // Public model types (for a dashboard)
    // --------------------------------------------

    public static class Outcome {
        public static final byte EMPTY = ((byte) 0);
        public static final byte SUCCESS = ((byte) 1);
        public static final byte ERROR = ((byte) 2);
        public static final byte INTERRUPTED = ((byte) 3);
    }

    // --------------------------------------------
    // Config
    // --------------------------------------------

    private final ScheduledThreadPoolExecutor service;
    private final AtomicInteger nextId = new AtomicInteger(0);

    /** Keep per-run samples for at most this time window (e.g. last 24h). */
    private final long retentionMs;

    /** Hard cap on number of per-run samples stored per task (ring buffer). */
    private final int maxRunSamples;

    /** Keep last N distinct stack traces per task. */
    private final int maxDistinctErrors;

    /** Truncate stack traces to cap memory. */
    private final int maxStackTraceChars;

    // --------------------------------------------
    // Storage
    // --------------------------------------------

    private final Object mapLock = new Object();
    private final Int2ObjectOpenHashMap<TaskRecord> taskMap = new Int2ObjectOpenHashMap<>();

    private static final class TaskRecord {
        final int id;
        final String name;
        final long createdAtMs;
        final long intervalMs;

        final Object lock = new Object();
        final RunHistory runHistory;
        final ErrorHistory errorHistory;

        volatile ScheduledFuture<?> future; // assigned right after scheduling

        volatile boolean running;
        volatile long currentRunStartMs;

        volatile long lastRunStartMs;
        volatile long lastRunEndMs;
        volatile int lastRunDurationMs = -1;
        volatile byte lastOutcome = 0;

        volatile long totalRuns;
        volatile long totalSuccess;
        volatile long totalErrors;
        volatile long totalInterrupts;
        volatile int consecutiveFailures;

        volatile long lastSuccessAtMs;
        volatile long lastFailureAtMs;

        volatile String lastErrorClass;
        volatile String lastErrorMessage;

        TaskRecord(int id,
                   String name,
                   long createdAtMs,
                   long intervalMs,
                   long retentionMs,
                   int maxRunSamples,
                   int maxDistinctErrors) {
            this.id = id;
            this.name = name;
            this.createdAtMs = createdAtMs;
            this.intervalMs = intervalMs;
            this.runHistory = new RunHistory(retentionMs, maxRunSamples);
            this.errorHistory = new ErrorHistory(maxDistinctErrors);
        }
    }

    /** Primitive ring buffer of (startTimeMs, durationMs, outcomeCode). */
    private static final class RunHistory {
        private final long retentionMs;
        private final int capacity;

        private final long[] startTimes;
        private final int[] durations;
        private final byte[] outcomes;

        private int head = 0; // index of oldest
        private int size = 0; // number of elements

        RunHistory(long retentionMs, int capacity) {
            this.retentionMs = Math.max(0, retentionMs);
            this.capacity = Math.max(1, capacity);
            this.startTimes = new long[this.capacity];
            this.durations = new int[this.capacity];
            this.outcomes = new byte[this.capacity];
        }

        void add(long startMs, int durationMs, byte outcomeCode, long nowMs) {
            int idx;
            if (size < capacity) {
                idx = (head + size) % capacity;
                size++;
            } else {
                idx = head;
                head = (head + 1) % capacity;
            }

            startTimes[idx] = startMs;
            durations[idx] = durationMs;
            outcomes[idx] = outcomeCode;

            prune(nowMs);
        }

        void prune(long nowMs) {
            if (retentionMs <= 0 || size == 0) return;

            long cutoff = nowMs - retentionMs;
            while (size > 0) {
                long oldestStart = startTimes[head];
                if (oldestStart >= cutoff) break;
                head = (head + 1) % capacity;
                size--;
            }
        }

        RunHistorySnapshot snapshotSince(long sinceMs) {
            long[] startsTmp = new long[size];
            int[] durTmp = new int[size];
            byte[] outTmp = new byte[size];

            int n = 0;
            for (int i = 0; i < size; i++) {
                int idx = (head + i) % capacity;
                long s = startTimes[idx];
                if (s < sinceMs) continue;

                startsTmp[n] = s;
                durTmp[n] = durations[idx];
                outTmp[n] = outcomes[idx];
                n++;
            }

            if (n == startsTmp.length) {
                return new RunHistorySnapshot(startsTmp, durTmp, outTmp);
            }

            long[] starts = new long[n];
            int[] durs = new int[n];
            byte[] outs = new byte[n];
            System.arraycopy(startsTmp, 0, starts, 0, n);
            System.arraycopy(durTmp, 0, durs, 0, n);
            System.arraycopy(outTmp, 0, outs, 0, n);
            return new RunHistorySnapshot(starts, durs, outs);
        }
    }

    /** Last N distinct errors (distinct by fingerprint + stackTrace string), with counts. */
    private static final class ErrorHistory {
        private final int maxDistinct;
        private final ObjectArrayList<MutableErrorSample> samples;

        ErrorHistory(int maxDistinct) {
            this.maxDistinct = Math.max(1, maxDistinct);
            this.samples = new ObjectArrayList<>(this.maxDistinct);
        }

        List<ErrorSample> snapshot() {
            ArrayList<ErrorSample> out = new ArrayList<>(samples.size());
            for (int i = 0; i < samples.size(); i++) {
                out.add(samples.get(i).toImmutable());
            }
            return out;
        }

        void record(int fingerprint, String throwableClass, String message, String stackTrace, long nowMs) {
            // N is tiny -> linear scan is fine.
            for (int i = 0; i < samples.size(); i++) {
                MutableErrorSample s = samples.get(i);
                if (s.fingerprint == fingerprint && Objects.equals(s.stackTrace, stackTrace)) {
                    s.lastSeenAtMs = nowMs;
                    s.count++;
                    // move-to-end (most recent) so eviction removes least-recent
                    samples.remove(i);
                    samples.add(s);
                    return;
                }
            }

            if (samples.size() == maxDistinct) {
                samples.remove(0);
            }
            samples.add(new MutableErrorSample(fingerprint, throwableClass, message, stackTrace, nowMs));
        }

        private static final class MutableErrorSample {
            final int fingerprint;
            final String throwableClass;
            final String message;
            final String stackTrace;
            final long firstSeenAtMs;

            long lastSeenAtMs;
            long count;

            MutableErrorSample(int fingerprint, String throwableClass, String message, String stackTrace, long nowMs) {
                this.fingerprint = fingerprint;
                this.throwableClass = throwableClass;
                this.message = message;
                this.stackTrace = stackTrace;
                this.firstSeenAtMs = nowMs;
                this.lastSeenAtMs = nowMs;
                this.count = 1;
            }

            ErrorSample toImmutable() {
                return new ErrorSample(fingerprint, throwableClass, message, stackTrace, firstSeenAtMs, lastSeenAtMs, count);
            }
        }
    }

    // --------------------------------------------
    // Constructors
    // --------------------------------------------

    public RepeatingTasks(ScheduledThreadPoolExecutor service,
                          long retention,
                          TimeUnit retentionUnit,
                          int maxRunSamples,
                          int maxDistinctErrors,
                          int maxStackTraceChars) {
        this.service = Objects.requireNonNull(service, "service");
        Objects.requireNonNull(retentionUnit, "retentionUnit");

        this.retentionMs = Math.max(0, retentionUnit.toMillis(retention));
        this.maxRunSamples = Math.max(1, maxRunSamples);
        this.maxDistinctErrors = Math.max(1, maxDistinctErrors);
        this.maxStackTraceChars = Math.max(256, maxStackTraceChars);
    }

    /** Defaults: last 24h, up to 50k run samples per task, last 3 distinct errors, stack trace max 16k chars. */
    public RepeatingTasks(ScheduledThreadPoolExecutor service) {
        this(service, 24, TimeUnit.HOURS, 50_000, 3, 16_384);
    }

    // --------------------------------------------
    // Scheduling
    // --------------------------------------------

    private int nextId() {
        return nextId.getAndIncrement();
    }

    /** Backwards-compatible signature (like your original): returns the ScheduledFuture. */
    public ScheduledFuture<?> addRunnable(String name, Runnable task, long interval, TimeUnit unit) {
        return addTask(name, (CaughtTask) task::run, interval, unit);
    }

    /** Backwards-compatible signature (like your original): returns the ScheduledFuture. */
    public ScheduledFuture<?> addTask(String name, CaughtTask task, long interval, TimeUnit unit) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(unit, "unit");
        if (interval <= 0) return null;

        final int id = nextId();
        final long intervalMs = unit.toMillis(interval);
        final long createdAtMs = System.currentTimeMillis();

        final TaskRecord record = new TaskRecord(id, name, createdAtMs, intervalMs, retentionMs, maxRunSamples, maxDistinctErrors);

        // Put the record in the map before scheduling (dashboard can see it immediately).
        synchronized (mapLock) {
            taskMap.put(id, record);
        }

        Runnable delegate = new Runnable() {
            @Override
            public void run() {
                final long startEpochMs = System.currentTimeMillis();
                final long startNano = System.nanoTime();

                synchronized (record.lock) {
                    record.running = true;
                    record.currentRunStartMs = startEpochMs;

                    record.lastRunStartMs = startEpochMs;
                    record.totalRuns++;
                }

                byte outcome = Outcome.SUCCESS;
                Throwable thrown = null;

                try {
                    task.runUnsafe();
                } catch (Throwable t) {
                    thrown = t;
                    if (t instanceof InterruptedException) {
                        outcome = Outcome.INTERRUPTED;
                        Thread.currentThread().interrupt(); // preserve interrupt status
                    } else {
                        outcome = Outcome.ERROR;
                    }
                } finally {
                    final long endEpochMs = System.currentTimeMillis();
                    final long durationLongMs = (System.nanoTime() - startNano) / 1_000_000L;
                    final int durationMs = durationLongMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, durationLongMs);

                    synchronized (record.lock) {
                        record.running = false;
                        record.currentRunStartMs = 0L;

                        record.lastRunEndMs = endEpochMs;
                        record.lastRunDurationMs = durationMs;
                        record.lastOutcome = outcome;

                        if (outcome == Outcome.SUCCESS) {
                            record.totalSuccess++;
                            record.consecutiveFailures = 0;
                            record.lastSuccessAtMs = endEpochMs;
                        } else if (outcome == Outcome.INTERRUPTED) {
                            record.totalInterrupts++;
                            record.consecutiveFailures++;
                            record.lastFailureAtMs = endEpochMs;
                        } else {
                            record.totalErrors++;
                            record.consecutiveFailures++;
                            record.lastFailureAtMs = endEpochMs;
                        }

                        record.runHistory.add(startEpochMs, durationMs, outcome, endEpochMs);

                        if (thrown != null) {
                            record.lastErrorClass = thrown.getClass().getName();
                            record.lastErrorMessage = thrown.getMessage();

                            String st = stackTraceToString(thrown, maxStackTraceChars);
                            int fp = fingerprint(thrown, st);
                            record.errorHistory.record(fp, record.lastErrorClass, record.lastErrorMessage, st, endEpochMs);
                        }
                    }

                    // Keep scheduler alive: never rethrow.
                    if (thrown != null) {
                        thrown.printStackTrace();
                    }
                }
            }
        };

        ScheduledFuture<?> future;
        try {
            future = service.scheduleWithFixedDelay(delegate, interval, interval, unit);
        } catch (RuntimeException e) {
            // If scheduling is rejected (shutdown), don't keep a dead record around.
            synchronized (mapLock) {
                taskMap.remove(id);
            }
            throw e;
        }

        record.future = future;
        return future;
    }

    public boolean cancel(int taskId, boolean mayInterruptIfRunning) {
        TaskRecord r;
        synchronized (mapLock) {
            r = taskMap.remove(taskId);
        }
        if (r == null) return false;

        ScheduledFuture<?> f = r.future;
        return f != null && f.cancel(mayInterruptIfRunning);
    }

    // --------------------------------------------
    // Dashboard / query API
    // --------------------------------------------

    public TaskSummary getSummary(int taskId) {
        TaskRecord r = getRecord(taskId);
        return r == null ? null : toSummary(r);
    }

    public List<TaskSummary> getAllSummaries() {
        List<TaskRecord> records = snapshotRecords();
        ArrayList<TaskSummary> out = new ArrayList<>(records.size());
        for (int i = 0; i < records.size(); i++) {
            out.add(toSummary(records.get(i)));
        }
        return out;
    }

    /** Run history for plotting. */
    public RunHistorySnapshot getRunHistory(int taskId) {
        return getRunHistorySince(taskId, Long.MIN_VALUE);
    }

    public RunHistorySnapshot getRunHistorySince(int taskId, long sinceEpochMs) {
        TaskRecord r = getRecord(taskId);
        if (r == null) return new RunHistorySnapshot(new long[0], new int[0], new byte[0]);

        synchronized (r.lock) {
            r.runHistory.prune(System.currentTimeMillis());
            return r.runHistory.snapshotSince(sinceEpochMs);
        }
    }

    /** Last N distinct stack traces (+ counts/last seen). */
    public List<ErrorSample> getRecentDistinctErrors(int taskId) {
        TaskRecord r = getRecord(taskId);
        if (r == null) return Collections.emptyList();

        synchronized (r.lock) {
            return r.errorHistory.snapshot();
        }
    }

    public boolean isRunning(int taskId) {
        TaskRecord r = getRecord(taskId);
        return r != null && r.running;
    }

    /** If running, how long (ms) since it started; else 0. */
    public long runningForMs(int taskId) {
        TaskRecord r = getRecord(taskId);
        if (r == null) return 0L;

        long start = r.currentRunStartMs;
        if (!r.running || start <= 0L) return 0L;

        long now = System.currentTimeMillis();
        return Math.max(0L, now - start);
    }

    /** For external code that wants to iterate known ids. */
    public int[] getTaskIds() {
        synchronized (mapLock) {
            int[] ids = new int[taskMap.size()];
            int i = 0;
            for (Integer id : taskMap.keySet()) {
                ids[i++] = id;
            }
            return ids;
        }
    }

    // --------------------------------------------
    // Internals
    // --------------------------------------------

    private TaskRecord getRecord(int taskId) {
        synchronized (mapLock) {
            return taskMap.get(taskId);
        }
    }

    private List<TaskRecord> snapshotRecords() {
        synchronized (mapLock) {
            ArrayList<TaskRecord> out = new ArrayList<>(taskMap.size());
            for (TaskRecord r : taskMap.values()) {
                out.add(r);
            }
            return out;
        }
    }

    private static TaskSummary toSummary(TaskRecord r) {
        synchronized (r.lock) {
            return new TaskSummary(
                    r.id,
                    r.name,
                    r.createdAtMs,
                    r.intervalMs,

                    r.running,
                    r.currentRunStartMs,

                    r.lastRunStartMs,
                    r.lastRunEndMs,
                    r.lastRunDurationMs,
                    r.lastOutcome,

                    r.totalRuns,
                    r.totalSuccess,
                    r.totalErrors,
                    r.totalInterrupts,
                    r.consecutiveFailures,

                    r.lastSuccessAtMs,
                    r.lastFailureAtMs,

                    r.lastErrorClass,
                    r.lastErrorMessage
            );
        }
    }

    private static String stackTraceToString(Throwable t, int maxChars) {
        StringWriter sw = new StringWriter(2048);
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();

        String s = sw.toString();
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n... (truncated)";
    }

    private static int fingerprint(Throwable t, String printedStackTrace) {
        int h = 1;
        h = 31 * h + t.getClass().getName().hashCode();

        StackTraceElement[] st = t.getStackTrace();
        int n = Math.min(st.length, 24);
        for (int i = 0; i < n; i++) {
            StackTraceElement e = st[i];
            h = 31 * h + e.getClassName().hashCode();
            h = 31 * h + e.getMethodName().hashCode();
            h = 31 * h + e.getLineNumber();
        }

        h = 31 * h + printedStackTrace.hashCode();
        return h;
    }
}