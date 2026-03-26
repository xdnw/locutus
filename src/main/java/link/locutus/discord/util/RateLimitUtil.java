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
import java.util.function.Supplier;

public class RateLimitUtil {
    // -------------------------------------------------------------------------
// Priority thresholds — inflight counts at which each source pauses submission.
//
// Sources marked reserveHeadroom=true are allowed to eat into the margin that
// background sources are blocked at. This reserves headroom for interaction-like
// work even when the bot is busy with alerts/bulk sends.
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

    private static boolean shouldPause(DeferredPriority priority) {
        return shouldPause(priority.reserveHeadroom());
    }

    public static boolean isCloseToLimit(RateLimitedSource source) {
        return shouldPause(source.deferredPriority());
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
// Public entry points require a concrete caller-owned RateLimitedSource.
// RateLimitUtil schedules work but must not infer who owns it.
// -------------------------------------------------------------------------

    public static <T> CompletableFuture<T> queue(RestAction<T> action, RateLimitedSource source) {
        Objects.requireNonNull(source, "source");
        return queue(action, source.deferredPriority(), source);
    }

    private static <T> CompletableFuture<T> queue(RestAction<T> action,
                                                  DeferredPriority priority,
                                                  RateLimitedSource source) {
        if (action == null) return CompletableFuture.completedFuture(null);

        if (!shouldPause(priority)) {
            return submitNow(action, source);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        queueWhenFree(priority, () ->
                submitNow(action, source).whenComplete((v, e) -> {
                    if (e != null) future.completeExceptionally(e);
                    else future.complete(v);
                })
        ).whenComplete((v, e) -> {
            if (e != null) future.completeExceptionally(e);
        });
        return future;
    }

    public static <T> T complete(RestAction<T> action, RateLimitedSource source) {
        Objects.requireNonNull(source, "source");
        return complete(action, source.deferredPriority(), source);
    }

    private static <T> T complete(RestAction<T> action,
                                  DeferredPriority priority,
                                  RateLimitedSource source) {
        if (action == null) return null;
        assertNotJdaThread();

        if (!shouldPause(priority)) {
            return handleNews(source, action.complete());
        }

        try {
            return completeWhenFree(action, priority, source);
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

    public static CompletableFuture<Void> queueWhenFree(RestAction<?> action, RateLimitedSource source) {
        if (action == null) return CompletableFuture.completedFuture(null);
        Objects.requireNonNull(source, "source");
        return queueWhenFree(source.deferredPriority(), () -> submitNow(action, source));
    }

    public static CompletableFuture<Void> queueWhenFree(RateLimitedSource source, Runnable action) {
        Objects.requireNonNull(source, "source");
        return queueWhenFree(source.deferredPriority(), action);
    }

    private static CompletableFuture<Void> queueWhenFree(DeferredPriority priority, Runnable action) {
        if (!shouldPause(priority)) {
            try {
                action.run();
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        queuedActions.get(priority).add(new QueuedAction(action, future));

        startQueueWorkerIfNeeded();
        return future;
    }

    /**
     * Blocks until the action can be submitted and JDA has responded.
     * Must not be called from a JDA thread or the main thread.
     */
    public static <T> T completeWhenFree(RestAction<T> action, RateLimitedSource source) throws InterruptedException {
        Objects.requireNonNull(source, "source");
        return completeWhenFree(action, source.deferredPriority(), source);
    }

    private static <T> T completeWhenFree(RestAction<T> action,
                                          DeferredPriority priority,
                                          RateLimitedSource source) throws InterruptedException {
        assertNotJdaThread();

        CompletableFuture<T> result = new CompletableFuture<>();
        queueWhenFree(priority, () -> {
            try {
                T value = handleNews(source, action.complete());
                result.complete(value);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }).whenComplete((v, e) -> {
            if (e != null) result.completeExceptionally(e);
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

    private record QueuedAction(Runnable runnable, CompletableFuture<Void> future) {}

    private static final DeferredPriority[] DEFERRED_PRIORITY_ORDER = DeferredPriority.values();
    private static final EnumMap<DeferredPriority, ConcurrentLinkedQueue<QueuedAction>> queuedActions = new EnumMap<>(DeferredPriority.class);
    private static final EnumMap<DeferredPriority, ConcurrentLinkedQueue<String>> queuedReplaceableKeys = new EnumMap<>(DeferredPriority.class);
    private static final ConcurrentHashMap<String, ReplaceableAction> replaceableActions = new ConcurrentHashMap<>();
    private static final AtomicBoolean runningTask = new AtomicBoolean(false);

    static {
        for (DeferredPriority priority : DEFERRED_PRIORITY_ORDER) {
            queuedActions.put(priority, new ConcurrentLinkedQueue<>());
            queuedReplaceableKeys.put(priority, new ConcurrentLinkedQueue<>());
        }
    }

    private static final class ReplaceableAction {
        private volatile DeferredPriority priority;
        private volatile Supplier<CompletableFuture<?>> supplier;
        private final List<CompletableFuture<Object>> futures = new CopyOnWriteArrayList<>();

        private <T> void replace(DeferredPriority priority,
                                 Supplier<CompletableFuture<T>> supplier,
                                 CompletableFuture<T> future) {
            this.priority = priority;
            this.supplier = (Supplier<CompletableFuture<?>>) (Supplier<?>) supplier;
            this.futures.add((CompletableFuture<Object>) (CompletableFuture<?>) future);
        }

        private DeferredPriority priority() {
            return priority;
        }

        private Supplier<CompletableFuture<?>> supplier() {
            return supplier;
        }

        private List<CompletableFuture<Object>> futures() {
            return futures;
        }
    }

    public static CompletableFuture<Void> queueLatest(String key, RateLimitedSource source, Runnable action) {
        return queueLatest(key, source.deferredPriority(), () -> {
            action.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    public static <T> CompletableFuture<T> queueLatest(String key, RateLimitedSource source, RestAction<T> action) {
        return queueLatest(key, source.deferredPriority(), () -> submitNow(action, source));
    }

    public static <T> CompletableFuture<T> queueLatest(String key,
                                                       RateLimitedSource source,
                                                       Supplier<CompletableFuture<T>> supplier) {
        return queueLatest(key, source.deferredPriority(), supplier);
    }

    private static <T> CompletableFuture<T> queueLatest(String key,
                                                        DeferredPriority priority,
                                                        Supplier<CompletableFuture<T>> supplier) {
        if (key == null || key.isBlank()) {
            try {
                return supplier.get();
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        if (!shouldPause(priority) && !replaceableActions.containsKey(key)) {
            try {
                return supplier.get();
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        replaceableActions.compute(key, (ignored, existing) -> {
            ReplaceableAction next = existing != null ? existing : new ReplaceableAction();
            DeferredPriority previousPriority = existing != null ? existing.priority() : null;
            next.replace(priority, supplier, future);
            if (existing == null || priority.ordinal() < previousPriority.ordinal()) {
                queuedReplaceableKeys.get(priority).add(key);
            }
            return next;
        });

        startQueueWorkerIfNeeded();
        return future;
    }

    private static final CaughtRunnable QUEUE_WORKER = new CaughtRunnable() {
        @Override
        public void runUnsafe() throws InterruptedException {
            try {
                while (true) {
                    boolean hadWork = hasAnyQueuedWork();
                    QueuedAction current = pollNextQueuedAction();
                    if (current != null) {
                        try {
                            current.runnable().run();
                            current.future().complete(null);
                        } catch (Throwable e) {
                            current.future().completeExceptionally(e);
                            AlertUtil.error("Error with queued action", e);
                        }
                        continue;
                    }

                    Map.Entry<String, ReplaceableAction> replaceable = pollNextReplaceableAction();
                    if (replaceable != null) {
                        runReplaceableAction(replaceable.getKey(), replaceable.getValue());
                        continue;
                    }

                    if (!hadWork) {
                        break;
                    }

                    long sleep = Math.min(globalResetDelayMs() + 50, 5000);
                    if (sleep <= 0) sleep = 250;
                    Thread.sleep(sleep);
                }
            } finally {
                runningTask.set(false);
                if (hasAnyQueuedWork()
                        && runningTask.compareAndSet(false, true)) {
                    try {
                        Locutus.imp().getExecutor().submit(this);
                    } catch (Throwable e) {
                        runningTask.set(false);
                        failQueuedActions(e);
                    }
                }
            }
        }
    };

    private static boolean hasAnyQueuedWork() {
        return hasQueuedActions() || hasQueuedReplaceableActions();
    }

    private static boolean hasQueuedActions() {
        for (ConcurrentLinkedQueue<QueuedAction> queue : queuedActions.values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasQueuedReplaceableActions() {
        if (!replaceableActions.isEmpty()) {
            return true;
        }
        for (ConcurrentLinkedQueue<String> queue : queuedReplaceableKeys.values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int totalQueuedActionCount() {
        int count = 0;
        for (ConcurrentLinkedQueue<QueuedAction> queue : queuedActions.values()) {
            count += queue.size();
        }
        return count + replaceableActions.size();
    }

    private static QueuedAction pollNextQueuedAction() {
        for (DeferredPriority priority : DEFERRED_PRIORITY_ORDER) {
            if (shouldPause(priority)) {
                continue;
            }
            QueuedAction queued = queuedActions.get(priority).poll();
            if (queued != null) {
                return queued;
            }
        }
        return null;
    }

    private static Map.Entry<String, ReplaceableAction> pollNextReplaceableAction() {
        for (DeferredPriority priority : DEFERRED_PRIORITY_ORDER) {
            if (shouldPause(priority)) {
                continue;
            }
            ConcurrentLinkedQueue<String> queue = queuedReplaceableKeys.get(priority);
            while (true) {
                String key = queue.poll();
                if (key == null) {
                    break;
                }
                ReplaceableAction action = replaceableActions.remove(key);
                if (action != null) {
                    return Map.entry(key, action);
                }
            }
        }
        return null;
    }

    private static void runReplaceableAction(String key, ReplaceableAction action) {
        CompletableFuture<?> submitted;
        try {
            submitted = action.supplier().get();
        } catch (Throwable e) {
            completeReplaceableFutures(action, null, e);
            AlertUtil.error("Error with replaceable queued action: " + key, e);
            return;
        }

        submitted.whenComplete((value, error) -> {
            completeReplaceableFutures(action, value, error);
            if (error != null) {
                AlertUtil.error("Error with replaceable queued action: " + key, error);
            }
        });
    }

    private static void completeReplaceableFutures(ReplaceableAction action, Object value, Throwable error) {
        for (CompletableFuture<Object> future : action.futures()) {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(value);
            }
        }
    }

    private static void startQueueWorkerIfNeeded() {
        if (!runningTask.compareAndSet(false, true)) return;

        try {
            Locutus.imp().getExecutor().submit(QUEUE_WORKER);
        } catch (Throwable e) {
            runningTask.set(false);
            failQueuedActions(e);
        }
    }

    private static void failQueuedActions(Throwable e) {
        for (ConcurrentLinkedQueue<QueuedAction> queue : queuedActions.values()) {
            QueuedAction q;
            while ((q = queue.poll()) != null) {
                q.future().completeExceptionally(e);
            }
        }
        for (ReplaceableAction action : replaceableActions.values()) {
            completeReplaceableFutures(action, null, e);
        }
        replaceableActions.clear();
        for (ConcurrentLinkedQueue<String> queue : queuedReplaceableKeys.values()) {
            queue.clear();
        }
        AlertUtil.error("Failed to run rate-limit queue worker", e);
    }

// -------------------------------------------------------------------------
// queueMessage
//
// Callers must provide a source-owned RateLimitedSource. RateLimitUtil schedules work but
// does not infer where that work came from.
// Returns a CompletableFuture<Void> that completes once the message has been sent
// (or dropped). The future does not carry a Message reference since batched sends
// produce a single message from multiple applies.
// -------------------------------------------------------------------------

    public static CompletableFuture<Void> queueMessage(MessageChannel channel, String message, RateLimitedSource source) {
        if (message.isBlank()) return CompletableFuture.completedFuture(null);
        DiscordChannelIO io = new DiscordChannelIO(channel);
        return queueMessage(io, msg -> {
            msg.append(message).append("\n");
            return true;
        }, source);
    }

    public static CompletableFuture<Void> queueMessage(IMessageIO io,
                                                       Function<IMessageBuilder, Boolean> apply,
                                                       RateLimitedSource source) {
        Objects.requireNonNull(source, "source");
        SendPolicy policy = source.sendPolicy();
// WebIO is synchronous and has no Discord limits.
        if (io instanceof WebIO) {
            try {
                IMessageBuilder msg = io.create();
                if (apply.apply(msg)) {
                    return io.send(msg, source).thenApply(v -> null);
                }
                return CompletableFuture.completedFuture(null);
            } catch (Throwable e) {
                return CompletableFuture.failedFuture(e);
            }
        }

// Interaction responses (DiscordHookIO) go through the /interactions endpoint,
// which is not subject to the per-channel send bucket. Always treat as IMMEDIATE
// regardless of the requested policy to avoid an unnecessary delay on user replies.
        if (io instanceof DiscordHookIO) {
            return sendNow(io, apply, source);
        }

        return switch (policy) {
            case IMMEDIATE -> sendNow(io, apply, source);
            case DROP -> {
                if (isCloseToLimit(source)) yield CompletableFuture.completedFuture(null);
                yield sendNow(io, apply, source);
            }
            case DEFER -> deferSend(io, apply, source);
            case CONDENSE -> condenseAndSend(io, apply, source);
        };
    }

    private static CompletableFuture<Void> deferSend(IMessageIO io,
                                                     Function<IMessageBuilder, Boolean> apply,
                                                     RateLimitedSource source) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        queueWhenFree(source, () ->
                sendNow(io, apply, source).whenComplete((v, e) -> {
                    if (e != null) future.completeExceptionally(e);
                    else future.complete(null);
                })
        ).whenComplete((v, e) -> {
            if (e != null) future.completeExceptionally(e);
        });

        return future;
    }

    private static CompletableFuture<Void> sendNow(IMessageIO io,
                                                   Function<IMessageBuilder, Boolean> apply,
                                                   RateLimitedSource source) {
        try {
            IMessageBuilder msg = io.create();
            if (!apply.apply(msg)) {
                return CompletableFuture.completedFuture(null);
            }
            return msg.send(source).thenApply(v -> null);
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

// -------------------------------------------------------------------------
// CONDENSE implementation — batches per-channel, flushes when bucket resets
// -------------------------------------------------------------------------

    private record PendingMessage(Function<IMessageBuilder, Boolean> apply,
                                  CompletableFuture<Void> future,
                                  RateLimitedSource source) {}

    private record PendingMessageKey(long channelId,
                                     RateLimitedSource source) {}

    private static final Map<PendingMessageKey, List<PendingMessage>> messageQueue = new ConcurrentHashMap<>();
    private static final Map<PendingMessageKey, Long> messageQueueLastSent = new ConcurrentHashMap<>();
    private static final Object messageQueueLock = new Object();

    private static CompletableFuture<Void> condenseAndSend(IMessageIO io,
                                                           Function<IMessageBuilder, Boolean> apply,
                                                           RateLimitedSource source) {
        long channelId = io.getIdLong();
        DeferredPriority priority = source.deferredPriority();

// If the channel has capacity (or is unknown), no need to batch — send now.
        if (channelId <= 0) {
            return deferSend(io, apply, source);
        }
        if (InstrumentedRateLimiter.channelHasCapacity(channelId) && !shouldPause(priority)) {
            return sendNow(io, apply, source);
        }

// Derive flush delay from the channel bucket's actual reset time + a small margin.
        long resetDelay = channelResetDelayMs(channelId);
        int bufferSeconds = (int) Math.min(65, (resetDelay + 200) / 1000L + 1);

        CompletableFuture<Void> future = new CompletableFuture<>();
        PendingMessage pending = new PendingMessage(apply, future, source);
        PendingMessageKey key = new PendingMessageKey(channelId, source);

        synchronized (messageQueueLock) {
            messageQueue.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pending);
        }

        try {
            Locutus.imp().getCommandManager().getExecutor().schedule(new CaughtRunnable() {
                @Override
                public void runUnsafe() {
                    List<PendingMessage> toSend = null;
                    long now = System.currentTimeMillis();

                    synchronized (messageQueueLock) {
                        List<PendingMessage> messages = messageQueue.get(key);
                        if (messages == null || messages.isEmpty()) {
                            return;
                        }

                        long last = messageQueueLastSent.getOrDefault(key, 0L);
                        boolean isLatest = messages.get(messages.size() - 1) == pending;
                        boolean windowElapsed = now - last > (long) bufferSeconds * 1000L;

                        if (isLatest || windowElapsed) {
                            toSend = messageQueue.remove(key);
                            messageQueueLastSent.put(key, now);
                        }
                    }

                    if (toSend == null) {
                        return;
                    }

                    List<PendingMessage> batch = toSend;
                    queueWhenFree(source, () ->
                            sendNow(io, msg -> {
                                boolean modified = false;
                                for (int i = 0; i < batch.size(); i++) {
                                    if (batch.get(i).apply().apply(msg)) {
                                        modified = true;
                                        if (i < batch.size() - 1) {
                                            IMessageBuilder ignored = msg.append("\n");
                                        }
                                    }
                                }
                                return modified;
                            }, source).whenComplete((v, e) -> {
                                for (PendingMessage item : batch) {
                                    if (e != null) item.future().completeExceptionally(e);
                                    else item.future().complete(null);
                                }
                            })
                    );
                }
            }, bufferSeconds, TimeUnit.SECONDS);
        } catch (Throwable e) {
            synchronized (messageQueueLock) {
                List<PendingMessage> messages = messageQueue.get(key);
                if (messages != null) {
                    messages.remove(pending);
                    if (messages.isEmpty()) {
                        messageQueue.remove(key);
                    }
                }
            }
            future.completeExceptionally(e);
        }

        return future;
    }

// -------------------------------------------------------------------------
// News channel crosspost handling
// -------------------------------------------------------------------------

    private static <T> T handleNews(RateLimitedSource source, T t) {
        if (t instanceof Message msg) {
            MessageChannelUnion channel = msg.getChannel();
            if (channel instanceof NewsChannel news && msg.getGuild().getSelfMember().hasAccess(news)) {
                queue(news.crosspostMessageById(msg.getIdLong()), source);
            }
        }
        return t;
    }

    private static <T> CompletableFuture<T> submitNow(RestAction<T> action, RateLimitedSource source) {
        try {
            return handleNews(source, action.submit());
        } catch (Throwable e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static <T> CompletableFuture<T> handleNews(RateLimitedSource source, CompletableFuture<T> future) {
        future.thenAccept(value -> handleNews(source, value));
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
                totalQueuedActionCount(),
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