package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
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

    public static DebugSnapshot getDebugSnapshot() {
        return getDebugSnapshot(true);
    }

    public static DebugSnapshot getDebugSnapshot(boolean update) {
        long now = System.currentTimeMillis();
        long cutoff = getWindowCutoff(now);
        if (update) {
            pruneExpiredRequests(cutoff);
        }

        List<RateLimitClassStat> requestsByClass = rateLimitByClass.entrySet().stream()
                .map(entry -> new RateLimitClassStat(getActionClassName(entry.getKey()),
                        (int) entry.getValue().keySet().stream().filter(f -> f >= cutoff).count()))
                .filter(stat -> stat.requestsThisMinute() > 0)
                .sorted(Comparator.comparingInt(RateLimitClassStat::requestsThisMinute).reversed()
                        .thenComparing(RateLimitClassStat::className))
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
        StringBuilder response = new StringBuilder("\n\n----------- RATE LIMIT: " + requestsThisMinute.size() + " -------------");
        // sort the map
        Map<Class<?>, Map<Long, Exception>> sorted = rateLimitByClass.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.comparingInt(value -> -value.keySet().stream().filter(f -> f > cutoff).mapToInt(f -> 1).sum())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        for (Map.Entry<Class<?>, Map<Long, Exception>> entry : sorted.entrySet()) {
            Map<Long, Exception> category = entry.getValue();
            if (category.size() > 1) category.entrySet().removeIf(f -> f.getKey() < cutoff);
            if (category.size() > 1) {
                response.append("\n\n" + getActionClassName(entry.getKey()) + " = " + category.size());
                Map<String, Integer> exceptionStrings = new HashMap<>();
                for (Exception value : category.values()) {
                    String key = StringMan.stacktraceToString(value.getStackTrace());
                    int amt = exceptionStrings.getOrDefault(key, 0) + 1;
                    exceptionStrings.put(key, amt);
                }
                // sort exceptionStrings
                exceptionStrings = exceptionStrings.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
                for (Map.Entry<String, Integer> entry2 : exceptionStrings.entrySet()) {
                    response.append("\n- " + entry2.getValue() + ": " + entry2.getKey());
                }
            }
        }
        return response.toString();
    }

    private static <T> Map.Entry<Class<?>, Exception> getClass(RestAction<T> action) {

    }

    private static <T> RestAction<T> addRequest(RestAction<T> action) {
        long now = System.currentTimeMillis();
        long cutoff = getWindowCutoff(now);
        requestsThisMinute.add(now);
        rateLimitByClass.computeIfAbsent(action.getClass(), f -> new ConcurrentHashMap<>())
                .put(now, new Exception());
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
