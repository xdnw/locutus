package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.AMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordMessageBuilder;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.web.commands.WebIO;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.RestAction;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RateLimitUtil {
    private static final Collection<Long> requestsThisMinute = new ConcurrentLinkedQueue<>();
    private static final Map<Class<?>, Map<Long, Exception>> rateLimitByClass = new ConcurrentHashMap<>();

    private static volatile long lastLimitTime = 0;
    private static volatile int lastLimitTotal = 0;
    private static volatile String lastRateLimitReport;

    private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private static final Set<Class<?>> FORWARDING_CALLERS = Set.of(
            RateLimitUtil.class,
            DiscordChannelIO.class,
            DiscordHookIO.class,
            DiscordMessageBuilder.class,
            AMessageBuilder.class,
            IMessageBuilder.class,
            IMessageIO.class
    );

    private static final List<String> FORWARDING_CALLER_PREFIXES = List.of(
            "link.locutus.discord.commands.manager.v2.impl.discord.Discord",
            "link.locutus.discord.commands.manager.v2.command.",
            "java.lang.reflect.",
            "jdk.internal.reflect."
    );

    public record RateLimitClassStat(String className, int requestsThisMinute) {
    }

    public record DebugSnapshot(int limitPerMinute,
                                int requestsThisMinute,
                                int remainingThisMinute,
                                int queuedActionCount,
                                boolean queuedActionWorkerRunning,
                                int queuedMessageChannelCount,
                                int queuedMessageCount,
                                long lastLimitTime,
                                int lastLimitTotal,
                                String lastRateLimitReport,
                                List<RateLimitClassStat> requestsByClass) {
    }

    private static long getWindowCutoff(long now) {
        return now - TimeUnit.MINUTES.toMillis(1);
    }

    private static void pruneExpiredRequests(long cutoff) {
        requestsThisMinute.removeIf(f -> f < cutoff);
        for (Map<Long, Exception> category : rateLimitByClass.values()) {
            if (category.size() > 1) {
                category.entrySet().removeIf(f -> f.getKey() < cutoff);
            }
        }
    }

    private static String getActionClassName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return simpleName.isBlank() ? clazz.getName() : simpleName;
    }

    private static int getQueuedMessageCount() {
        return messageQueue.values().stream().mapToInt(List::size).sum();
    }

    private static boolean shouldSkipCallerFrame(Class<?> clazz) {
        if (clazz == null) {
            return true;
        }
        if (FORWARDING_CALLERS.contains(clazz)) {
            return true;
        }
        String name = clazz.getName();
        for (String prefix : FORWARDING_CALLER_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<Map.Entry<Class<?>, Map<Long, Exception>>> getSortedRateLimitEntries(long cutoff) {
        List<Map.Entry<Class<?>, Map<Long, Exception>>> entries = new ArrayList<>(rateLimitByClass.entrySet());
        for (Map.Entry<Class<?>, Map<Long, Exception>> entry : entries) {
            entry.getValue().entrySet().removeIf(f -> f.getKey() < cutoff);
        }

        return entries.stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .sorted(Comparator
                        .comparingInt((Map.Entry<Class<?>, Map<Long, Exception>> entry) -> entry.getValue().size())
                        .reversed()
                        .thenComparing(entry -> getActionClassName(entry.getKey())))
                .toList();
    }

    private static String getRateLimitStackKey(Exception trace) {
        return StringMan.stacktraceToString(trace.getStackTrace());
    }

    private static void appendClassStats(StringBuilder response, List<RateLimitClassStat> classStats, int maxResults) {
        if (classStats.isEmpty()) {
            response.append("none in the current minute");
            return;
        }

        int shown = Math.min(Math.max(maxResults, 0), classStats.size());
        for (int i = 0; i < shown; i++) {
            RateLimitClassStat stat = classStats.get(i);
            response.append("- ")
                    .append(stat.className())
                    .append(": ")
                    .append(stat.requestsThisMinute())
                    .append("\n");
        }

        int hidden = classStats.size() - shown;
        if (hidden > 0) {
            response.append("- ... and ")
                    .append(hidden)
                    .append(" more classes\n");
        }
    }

    public static String formatDebugSummary(DebugSnapshot snapshot, int maxResults) {
        StringBuilder response = new StringBuilder();

        response.append("**Discord Rate Limit:** ")
                .append(snapshot.requestsThisMinute())
                .append("/")
                .append(snapshot.limitPerMinute())
                .append(" requests this minute (remaining: ")
                .append(snapshot.remainingThisMinute())
                .append(")\n");

        response.append("- Queued actions: ")
                .append(snapshot.queuedActionCount())
                .append(" (worker running: ")
                .append(snapshot.queuedActionWorkerRunning())
                .append(")\n");

        response.append("- Condensed message queue: ")
                .append(snapshot.queuedMessageCount())
                .append(" messages across ")
                .append(snapshot.queuedMessageChannelCount())
                .append(" channels\n");

        if (snapshot.lastLimitTime() > 0) {
            response.append("- Last recorded limit hit: ")
                    .append(new Date(snapshot.lastLimitTime()))
                    .append(" (")
                    .append(snapshot.lastLimitTotal())
                    .append(" requests)\n");
        } else {
            response.append("- Last recorded limit hit: none\n");
        }

        response.append("\n**By Class:** ");
        if (snapshot.requestsByClass().isEmpty()) {
            response.append("none in the current minute");
        } else {
            response.append("\n");
            appendClassStats(response, snapshot.requestsByClass(), maxResults);
        }

        return response.toString().stripTrailing();
    }

    public static String formatDebugDetails(DebugSnapshot snapshot) {
        StringBuilder detail = new StringBuilder();

        detail.append("Discord rate limit snapshot\n");
        detail.append("Generated: ").append(new Date()).append("\n");
        detail.append("Requests this minute: ")
                .append(snapshot.requestsThisMinute())
                .append("/")
                .append(snapshot.limitPerMinute())
                .append("\n");
        detail.append("Remaining this minute: ")
                .append(snapshot.remainingThisMinute())
                .append("\n");
        detail.append("Queued actions: ")
                .append(snapshot.queuedActionCount())
                .append("\n");
        detail.append("Queue worker running: ")
                .append(snapshot.queuedActionWorkerRunning())
                .append("\n");
        detail.append("Queued message channels: ")
                .append(snapshot.queuedMessageChannelCount())
                .append("\n");
        detail.append("Queued messages: ")
                .append(snapshot.queuedMessageCount())
                .append("\n");
        detail.append("Last recorded limit hit: ");
        if (snapshot.lastLimitTime() > 0) {
            detail.append(new Date(snapshot.lastLimitTime()))
                    .append(" (")
                    .append(snapshot.lastLimitTotal())
                    .append(" requests)\n");
        } else {
            detail.append("none\n");
        }

        detail.append("\nBy class:\n");
        if (snapshot.requestsByClass().isEmpty()) {
            detail.append("none in the current minute\n");
        } else {
            appendClassStats(detail, snapshot.requestsByClass(), Integer.MAX_VALUE);
        }

        if (snapshot.lastRateLimitReport() != null && !snapshot.lastRateLimitReport().isBlank()) {
            detail.append("\nLast recorded over-limit report:\n");
            detail.append(snapshot.lastRateLimitReport()).append("\n");
        }

        return detail.toString().stripTrailing();
    }

    public static DebugSnapshot getDebugSnapshot(boolean update) {
        long now = System.currentTimeMillis();
        long cutoff = getWindowCutoff(now);
        if (update) {
            pruneExpiredRequests(cutoff);
        }

        List<RateLimitClassStat> requestsByClass = getSortedRateLimitEntries(cutoff).stream()
                .map(entry -> new RateLimitClassStat(getActionClassName(entry.getKey()), entry.getValue().size()))
                .toList();

        int requests = requestsThisMinute.size();
        int limit = getLimitPerMinute();
        return new DebugSnapshot(limit,
                requests,
                Math.max(0, limit - requests),
                queuedActions.size(),
                runningTask.get(),
                messageQueue.size(),
                getQueuedMessageCount(),
                lastLimitTime,
                lastLimitTotal,
                lastRateLimitReport,
                requestsByClass);
    }

    public static int getCurrentUsed() {
        return getCurrentUsed(false);
    }

    public static int getCurrentUsed(boolean update) {
        if (update) {
            pruneExpiredRequests(getWindowCutoff(System.currentTimeMillis()));
        }
        return requestsThisMinute.size();
    }

    public static int getLimitPerMinute() {
        return 50;
    }

    private static String getRateLimitMessage(long cutoff) {
        StringBuilder response = new StringBuilder()
                .append("\n\n----------- RATE LIMIT: ")
                .append(requestsThisMinute.size())
                .append(" -------------");

        for (Map.Entry<Class<?>, Map<Long, Exception>> entry : getSortedRateLimitEntries(cutoff)) {
            Map<Long, Exception> category = entry.getValue();
            if (category.size() <= 1) {
                continue;
            }

            response.append("\n\n")
                    .append(getActionClassName(entry.getKey()))
                    .append(" = ")
                    .append(category.size());

            category.values().stream()
                    .collect(Collectors.toMap(
                            RateLimitUtil::getRateLimitStackKey,
                            ignore -> 1,
                            Integer::sum
                    ))
                    .entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry::getKey))
                    .forEach(stackEntry -> response.append("\n- ")
                            .append(stackEntry.getValue())
                            .append(": ")
                            .append(stackEntry.getKey()));
        }

        return response.toString();
    }

    private static <T> Map.Entry<Class<?>, Exception> getClass(RestAction<T> action) {
        Class<?> callerClass = null;
        List<StackTraceElement> filteredFrames = new ArrayList<>();

        for (StackWalker.StackFrame frame : STACK_WALKER.walk(stream -> stream.toList())) {
            Class<?> declaringClass = frame.getDeclaringClass();
            if (shouldSkipCallerFrame(declaringClass)) {
                continue;
            }

            if (callerClass == null) {
                callerClass = declaringClass;
            }

            filteredFrames.add(frame.toStackTraceElement());
        }

        if (callerClass == null) {
            callerClass = action != null ? action.getClass() : RateLimitUtil.class;
        }

        if (filteredFrames.isEmpty()) {
            filteredFrames.add(new StackTraceElement(callerClass.getName(), "<unknown>", null, -1));
        }

        Exception trace = new Exception();
        trace.setStackTrace(filteredFrames.toArray(StackTraceElement[]::new));
        return new AbstractMap.SimpleEntry<>(callerClass, trace);
    }

    private static <T> RestAction<T> addRequest(RestAction<T> action) {
        long now = System.currentTimeMillis();
        long cutoff = getWindowCutoff(now);

        Map.Entry<Class<?>, Exception> caller = getClass(action);

        requestsThisMinute.add(now);
        rateLimitByClass.computeIfAbsent(caller.getKey(), f -> new ConcurrentHashMap<>())
                .put(now, caller.getValue());

        pruneExpiredRequests(cutoff);

        if (requestsThisMinute.size() > getLimitPerMinute()) {
            if (lastLimitTime < cutoff || requestsThisMinute.size() > lastLimitTotal + 10) {
                lastLimitTime = now;
                lastLimitTotal = requestsThisMinute.size();
                lastRateLimitReport = getRateLimitMessage(cutoff);
            }
        } else {
            lastLimitTotal = 0;
        }

        return action;
    }

    private static final Map<Long, List<Function<IMessageBuilder, Boolean>>> messageQueue = new ConcurrentHashMap<>();
    private static final Map<Long, Long> messageQueueLastSent = new ConcurrentHashMap<>();

    public static void queueMessage(MessageChannel channel, String message, boolean condense) {
        queueMessage(channel, message, condense, null);
    }
    public static void queueMessage(MessageChannel channel, String message, boolean condense, Integer bufferSeconds) {
        if (message.isBlank()) return;
        DiscordChannelIO io = new DiscordChannelIO(channel);
        queueMessage(io, new Function<IMessageBuilder, Boolean>() {
            @Override
            public Boolean apply(IMessageBuilder msg) {
                msg.append(message + "\n");
                return true;
            }
        }, condense, bufferSeconds);
    }

    public static void queueMessage(IMessageIO io, Function<IMessageBuilder, Boolean> apply, boolean condense, Integer bufferSeconds) {
        if (io instanceof WebIO) {
            IMessageBuilder msg = io.create();
            if (apply.apply(msg)) {
                msg.send();
            }
            return;
        }
        long channelId = io.getIdLong();
        int requests = getCurrentUsed(true);
        if (!condense || requests < 10 || channelId <= 0) {
            IMessageBuilder msg = io.create();
            if (apply.apply(msg)) {
                msg.send();
            }
            return;
        }

        if (bufferSeconds == null) {
            if (requests < 20) bufferSeconds = 10;
            else if (requests < 30) bufferSeconds = 30;
            else if (requests < 50) bufferSeconds = 45;
            else bufferSeconds = 60;
        }

        synchronized (messageQueueLastSent) {
            messageQueue.computeIfAbsent(channelId, f -> new ArrayList<>()).add(apply);
        }
        Integer finalBufferSeconds = bufferSeconds;
        Locutus.imp().getCommandManager().getExecutor().schedule(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                MessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(channelId);
                if (channel == null) return;

                long now = System.currentTimeMillis();

                List<Function<IMessageBuilder, Boolean>> toSend = null;

                synchronized (messageQueueLastSent) {
                    long last = messageQueueLastSent.getOrDefault(channelId, 0L);
                    List<Function<IMessageBuilder, Boolean>> messages = messageQueue.get(channelId);
                    if (messages == null || messages.isEmpty()) return;

                    boolean isMyMessageLatest = messages.get(messages.size() - 1) == apply;

                    if (now - last > finalBufferSeconds * 1000L || isMyMessageLatest) {
                        toSend = messageQueue.remove(channelId);
                        messageQueueLastSent.put(channelId, now);
                    }
                }
                if (toSend != null) {
                    boolean modified = false;
                    IMessageBuilder msg = io.create();
                    for (int i = 0; i < toSend.size(); i++) {
                        if (toSend.get(i).apply(msg)) {
                            modified = true;
                            if (i < toSend.size() - 1) {
                                msg.append("\n");
                            }
                        }
                    }
                    if (modified) {
                        msg.send();
                    }
                }
            }
        }, bufferSeconds, TimeUnit.SECONDS);

    }

    private static final ConcurrentLinkedQueue<Runnable> queuedActions = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean runningTask = new AtomicBoolean(false);

    private static void startQueueWorkerIfNeeded() {
        if (!runningTask.compareAndSet(false, true)) {
            return;
        }

        Locutus.imp().getExecutor().submit(new CaughtRunnable() {
            @Override
            public void runUnsafe() throws InterruptedException {
                try {
                    while (true) {
                        if (queuedActions.isEmpty()) {
                            return;
                        }
                        if (getCurrentUsed(true) >= getLimitPerMinute()) {
                            Thread.sleep(1000);
                            continue;
                        }

                        Runnable current = queuedActions.poll();
                        if (current == null) {
                            continue;
                        }

                        try {
                            current.run();
                        } catch (Throwable e) {
                            AlertUtil.error("Error with queued action", e);
                        }
                    }
                } finally {
                    runningTask.set(false);
                    if (!queuedActions.isEmpty()) {
                        startQueueWorkerIfNeeded();
                    }
                }
            }
        });
    }

    public static void queueWhenFree(RestAction<?> action) {
        queueWhenFree(() -> queue(action));
    }

    public static void queueWhenFree(Runnable action) {
        if (getCurrentUsed(true) < getLimitPerMinute()) {
            action.run();
            return;
        }
        queuedActions.add(action);
        startQueueWorkerIfNeeded();
    }

    public static <T> T complete(RestAction<T> action) {
        return (T) handleNews(addRequest(action).complete());
    }

    public static <T> CompletableFuture<T> queue(RestAction<T> action) {
        if (action == null) return null;
        return handleNews(addRequest(action).submit());
    }

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
}
