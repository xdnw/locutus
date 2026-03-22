package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.web.commands.WebIO;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class RateLimitUtil {

    // -------------------------------------------------------------------------
    // Priority thresholds — inflight counts at which each tier pauses submission.
    //
    // "Priority" requests (user interactions) are allowed to eat into the margin
    // that non-priority sends are blocked at. This reserves headroom for interaction
    // responses even when the bot is busy with alerts/bulk sends.
    //
    // Discord's global limit is 50 req/s. These thresholds are conservative enough
    // that we stay well clear of it without stalling user-facing responses.
    // -------------------------------------------------------------------------
    private static final int INFLIGHT_NONPRIORITY_PAUSE = 15;
    private static final int INFLIGHT_PRIORITY_PAUSE    = 25;

    // -------------------------------------------------------------------------
    // Internal state check
    // -------------------------------------------------------------------------

    private static InstrumentedRateLimiter limiter() {
        return InstrumentedRateLimiter.getInstance();
    }

    /**
     * True if we should hold further sends rather than submitting to JDA.
     * Priority requests have a higher threshold, reserving headroom for user interactions.
     */
    private static boolean shouldPause(boolean priority) {
        InstrumentedRateLimiter inst = limiter();
        if (inst != null && inst.isGloballyLimited()) return true;
        int inflight = InstrumentedRateLimiter.getInflightCount();
        return inflight >= (priority ? INFLIGHT_PRIORITY_PAUSE : INFLIGHT_NONPRIORITY_PAUSE);
    }

    /**
     * True if we are close enough to the rate limit that non-priority sends should
     * be deferred or dropped.
     */
    private static boolean isCloseToLimit() {
        return shouldPause(false);
    }

    private static long globalResetDelayMs() {
        InstrumentedRateLimiter inst = limiter();
        if (inst == null || !inst.isGloballyLimited()) return 0L;
        return Math.max(0L, inst.globalResetAtMs() - System.currentTimeMillis());
    }

    private static long channelResetDelayMs(long channelId) {
        InstrumentedRateLimiter.BucketSnapshot b = InstrumentedRateLimiter.getChannelSendBucket(channelId);
        if (b == null || b.hasComfortableCapacity()) return 0L;
        return b.msUntilReset();
    }

    // -------------------------------------------------------------------------
    // Thread assertions
    // -------------------------------------------------------------------------

    private static void assertNotJdaThread() {
        String name = Thread.currentThread().getName();
        if (name.startsWith("JDA")) {
            throw new IllegalStateException(
                    "Blocking call on JDA thread '" + name + "' would deadlock");
        }
    }

    // -------------------------------------------------------------------------
    // queue / complete
    //
    // Both default to priority=true (user-facing). Pass priority=false for
    // background/alert sends that should respect the non-priority threshold.
    // -------------------------------------------------------------------------

    private static <T> CompletableFuture<T> submitNow(RestAction<T> action) {
        return handleNews(action.submit());
    }

    /** Priority queue — submits immediately unless the priority threshold is breached. */
    public static <T> CompletableFuture<T> queue(RestAction<T> action) {
        return queue(action, true);
    }

    /**
     * @param priority If true, only pauses when inflight >= INFLIGHT_PRIORITY_PAUSE.
     *                 If false, pauses at the lower INFLIGHT_NONPRIORITY_PAUSE threshold
     *                 and is routed through the deferred queue automatically.
     */
    public static <T> CompletableFuture<T> queue(RestAction<T> action, boolean priority) {
        if (action == null) return CompletableFuture.completedFuture(null);

        if (!shouldPause(priority)) {
            return submitNow(action);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        queueWhenFree(priority, () -> {
            submitNow(action).whenComplete((v, e) -> {
                if (e != null) future.completeExceptionally(e);
                else future.complete(v);
            });
        });
        return future;
    }

    /** Priority complete — blocks the calling thread. Must not be called from a JDA thread. */
    public static <T> T complete(RestAction<T> action) {
        return complete(action, true);
    }

    public static <T> T complete(RestAction<T> action, boolean priority) {
        if (action == null) return null;
        assertNotJdaThread();

        if (!shouldPause(priority)) {
            return handleNews(action.complete());
        }

        try {
            return completeWhenFree(action, priority);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

// -------------------------------------------------------------------------
// queueWhenFree / completeWhenFree
//
// queueWhenFree: holds the action in our local queue until the selected
// threshold is clear, then submits to JDA. Returns a future that completes
// when the action has been submitted (not when JDA responds).
//
// completeWhenFree: like queueWhenFree but blocks until JDA responds.
// Must not be called from JDA event threads or the main thread.
// -------------------------------------------------------------------------

    public static CompletableFuture<Void> queueWhenFree(RestAction<?> action) {
        if (action == null) return CompletableFuture.completedFuture(null);
        return queueWhenFree(false, () -> handleNews(action.submit()));
    }

    public static CompletableFuture<Void> queueWhenFree(Runnable action) {
        return queueWhenFree(false, action);
    }

    private static CompletableFuture<Void> queueWhenFree(boolean priority, Runnable action) {
        if (!shouldPause(priority)) {
            try {
                action.run();
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        QueuedAction queued = new QueuedAction(priority, () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });

        if (priority) {
            queuedPriorityActions.add(queued);
        } else {
            queuedActions.add(queued);
        }

        startQueueWorkerIfNeeded();
        return future;
    }

    /**
     * Blocks until the action can be submitted and JDA has responded.
     * Must not be called from a JDA thread or the main thread.
     */
    public static <T> T completeWhenFree(RestAction<T> action) throws InterruptedException {
        return completeWhenFree(action, false);
    }

    private static <T> T completeWhenFree(RestAction<T> action, boolean priority) throws InterruptedException {
        assertNotJdaThread();

        CompletableFuture<T> result = new CompletableFuture<>();
        queueWhenFree(priority, () -> {
            try {
                T value = handleNews(action.complete());
                result.complete(value);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });

        try {
            return result.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException r) throw r;
            throw new RuntimeException(cause);
        }
    }

// -------------------------------------------------------------------------
// Deferred action queue
// -------------------------------------------------------------------------

    private record QueuedAction(boolean priority, Runnable runnable) {}

    private static final ConcurrentLinkedQueue<QueuedAction> queuedPriorityActions = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<QueuedAction> queuedActions = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean runningTask = new AtomicBoolean(false);

    private static void startQueueWorkerIfNeeded() {
        if (!runningTask.compareAndSet(false, true)) return;

        Locutus.imp().getExecutor().submit(new CaughtRunnable() {
            @Override
            public void runUnsafe() throws InterruptedException {
                try {
                    while (true) {
                        QueuedAction current = pollNextAction();
                        if (current == null) return;

                        try {
                            current.runnable().run();
                        } catch (Throwable e) {
                            AlertUtil.error("Error with queued action", e);
                        }
                    }
                } finally {
                    runningTask.set(false);
                    if ((!queuedPriorityActions.isEmpty() || !queuedActions.isEmpty())
                            && runningTask.compareAndSet(false, true)) {
                        runUnsafe();
                    }
                }
            }

            private QueuedAction pollNextAction() throws InterruptedException {
                while (true) {
                    if (queuedPriorityActions.isEmpty() && queuedActions.isEmpty()) {
                        return null;
                    }

                    if (!shouldPause(true)) {
                        QueuedAction priority = queuedPriorityActions.poll();
                        if (priority != null) return priority;
                    }

                    if (!shouldPause(false)) {
                        QueuedAction normal = queuedActions.poll();
                        if (normal != null) return normal;
                    }

                    long sleep = Math.min(globalResetDelayMs() + 50, 5000);
                    if (sleep <= 0) sleep = 250;
                    Thread.sleep(sleep);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // queueMessage
    //
    // API surface simplified: condense boolean + bufferSeconds replaced by SendPolicy.
    // Returns a CompletableFuture<Void> that completes once the message has been sent
    // (or dropped). The future does not carry a Message reference since batched sends
    // produce a single message from multiple applies.
    // -------------------------------------------------------------------------

    public static CompletableFuture<Void> queueMessage(MessageChannel channel, String message, SendPolicy policy) {
        if (message.isBlank()) return CompletableFuture.completedFuture(null);
        DiscordChannelIO io = new DiscordChannelIO(channel);
        return queueMessage(io, msg -> { msg.append(message).append("\n"); return true; }, policy);
    }

    public static CompletableFuture<Void> queueMessage(IMessageIO io,
                                                       Function<IMessageBuilder, Boolean> apply,
                                                       SendPolicy policy) {
        // WebIO is synchronous and has no Discord limits.
        if (io instanceof WebIO) {
            IMessageBuilder msg = io.create();
            if (apply.apply(msg)) msg.send();
            return CompletableFuture.completedFuture(null);
        }

        // Interaction responses (DiscordHookIO) go through the /interactions endpoint,
        // which is not subject to the per-channel send bucket. Always treat as IMMEDIATE
        // regardless of the requested policy to avoid an unnecessary delay on user replies.
        if (io instanceof DiscordHookIO) {
            return sendNow(io, apply);
        }

        return switch (policy) {
            case IMMEDIATE -> sendNow(io, apply);
            case DROP -> {
                if (isCloseToLimit()) yield CompletableFuture.completedFuture(null);
                yield sendNow(io, apply);
            }
            case DEFER -> deferSend(io, apply);
            case CONDENSE -> condenseAndSend(io, apply);
        };
    }

    private static CompletableFuture<Void> deferSend(IMessageIO io, Function<IMessageBuilder, Boolean> apply) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        queueWhenFree(() ->
                sendNow(io, apply).whenComplete((v, e) -> {
                    if (e != null) future.completeExceptionally(e);
                    else future.complete(null);
                })
        ).whenComplete((v, e) -> {
            if (e != null) future.completeExceptionally(e);
        });

        return future;
    }

    private static CompletableFuture<Void> sendNow(IMessageIO io, Function<IMessageBuilder, Boolean> apply) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            IMessageBuilder msg = io.create();
            if (apply.apply(msg)) msg.send();
            future.complete(null);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    // -------------------------------------------------------------------------
// CONDENSE implementation — batches per-channel, flushes when bucket resets
// -------------------------------------------------------------------------

    private record PendingMessage(Function<IMessageBuilder, Boolean> apply, CompletableFuture<Void> future) {}

    private static final Map<Long, List<PendingMessage>> messageQueue = new ConcurrentHashMap<>();
    private static final Map<Long, Long> messageQueueLastSent = new ConcurrentHashMap<>();
    private static final Object messageQueueLock = new Object();

    private static CompletableFuture<Void> condenseAndSend(IMessageIO io,
                                                           Function<IMessageBuilder, Boolean> apply) {
        long channelId = io.getIdLong();

        // If the channel has capacity (or is unknown), no need to batch — send now.
        if (channelId <= 0 || InstrumentedRateLimiter.channelHasCapacity(channelId)) {
            return sendNow(io, apply);
        }

        // Derive flush delay from the channel bucket's actual reset time + a small margin.
        long resetDelay = channelResetDelayMs(channelId);
        int bufferSeconds = (int) Math.min(65, (resetDelay + 200) / 1000L + 1);

        CompletableFuture<Void> future = new CompletableFuture<>();
        PendingMessage pending = new PendingMessage(apply, future);

        synchronized (messageQueueLock) {
            messageQueue.computeIfAbsent(channelId, f -> new ArrayList<>()).add(pending);
        }

        Locutus.imp().getCommandManager().getExecutor().schedule(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                List<PendingMessage> toSend = null;
                long now = System.currentTimeMillis();

                synchronized (messageQueueLock) {
                    List<PendingMessage> messages = messageQueue.get(channelId);
                    if (messages == null || messages.isEmpty()) {
                        return;
                    }

                    long last = messageQueueLastSent.getOrDefault(channelId, 0L);
                    boolean isLatest = messages.get(messages.size() - 1) == pending;
                    boolean windowElapsed = now - last > (long) bufferSeconds * 1000L;

                    if (isLatest || windowElapsed) {
                        toSend = messageQueue.remove(channelId);
                        messageQueueLastSent.put(channelId, now);
                    }
                }

                if (toSend == null) {
                    return;
                }

                List<PendingMessage> batch = toSend;
                sendNow(io, msg -> {
                    boolean modified = false;
                    for (int i = 0; i < batch.size(); i++) {
                        if (batch.get(i).apply().apply(msg)) {
                            modified = true;
                            if (i < batch.size() - 1) msg.append("\n");
                        }
                    }
                    return modified;
                }).whenComplete((v, e) -> {
                    for (PendingMessage item : batch) {
                        if (e != null) item.future().completeExceptionally(e);
                        else item.future().complete(null);
                    }
                });
            }
        }, bufferSeconds, TimeUnit.SECONDS);

        return future;
    }

    // -------------------------------------------------------------------------
    // News channel crosspost handling
    // -------------------------------------------------------------------------

    private static <T> T handleNews(T t) {
        if (t instanceof Message msg) {
            MessageChannelUnion channel = msg.getChannel();
            if (channel instanceof NewsChannel news && msg.getGuild().getSelfMember().hasAccess(news)) {
                queue(news.crosspostMessageById(msg.getIdLong()));
            }
        }
        return t;
    }

    private static <T> CompletableFuture<T> handleNews(CompletableFuture<T> future) {
        future.thenAccept(RateLimitUtil::handleNews);
        return future;
    }

    // -------------------------------------------------------------------------
    // Debug snapshot
    // -------------------------------------------------------------------------

    public record DebugSnapshot(
            boolean globallyLimited,
            long globalResetAtMs,
            int inflightCount,
            int queuedActionCount,
            boolean queuedActionWorkerRunning,
            int queuedMessageChannelCount,
            int queuedMessageCount
    ) {}

    public static DebugSnapshot getDebugSnapshot() {
        InstrumentedRateLimiter inst = limiter();
        int msgCount;
        int channelCount;
        synchronized (messageQueueLock) {
            channelCount = messageQueue.size();
            msgCount = messageQueue.values().stream().mapToInt(List::size).sum();
        }
        return new DebugSnapshot(
                inst != null && inst.isGloballyLimited(),
                inst != null ? inst.globalResetAtMs() : 0L,
                InstrumentedRateLimiter.getInflightCount(),
                queuedPriorityActions.size() + queuedActions.size(),
                runningTask.get(),
                channelCount,
                msgCount
        );
    }

    public static String formatDebugSummary(DebugSnapshot snap) {
        StringBuilder sb = new StringBuilder("**Discord Rate Limit State:**\n");
        if (snap.globallyLimited()) {
            sb.append("- **GLOBALLY LIMITED** — resets at ")
                    .append(new Date(snap.globalResetAtMs())).append("\n");
        } else {
            sb.append("- Global limit: OK\n");
        }
        sb.append("- In-flight (submitted to JDA, awaiting response): ")
                .append(snap.inflightCount()).append("\n");
        sb.append("- Non-priority pauses at: ").append(INFLIGHT_NONPRIORITY_PAUSE)
                .append(", priority pauses at: ").append(INFLIGHT_PRIORITY_PAUSE).append("\n");
        sb.append("- Our deferred queue: ")
                .append(snap.queuedActionCount())
                .append(" actions (worker running: ").append(snap.queuedActionWorkerRunning()).append(")\n");
        sb.append("- Condense queue: ")
                .append(snap.queuedMessageCount()).append(" messages across ")
                .append(snap.queuedMessageChannelCount()).append(" channels");
        return sb.toString();
    }
}