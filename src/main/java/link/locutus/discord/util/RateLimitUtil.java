package link.locutus.discord.util;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.jooby.adapter.JoobyChannel;
import link.locutus.discord.web.jooby.adapter.JoobyMessageAction;
import link.locutus.discord.web.jooby.adapter.JoobyRestAction;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RateLimitUtil {
    private static final Collection<Long> requestsThisMinute = new ConcurrentLinkedQueue<>();
    private static final Map<Class, Map<Long, Exception>> rateLimitByClass = new ConcurrentHashMap<>();

    private static long lastLimitTime = 0;
    private static int lastLimitTotal = 0;

    public static int getCurrentUsed() {
        return getCurrentUsed(false);
    }

    public static int getCurrentUsed(boolean update) {
        if (update) {
            long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
            requestsThisMinute.removeIf(f -> f < cutoff);
        }
        return requestsThisMinute.size();
    }

    public static int getLimitPerMinute() {
        return 50;
    }

    private static RestAction addRequest(RestAction action) {
        if (action instanceof JoobyMessageAction || action instanceof JoobyRestAction) {
            return action;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.MINUTES.toMillis(1);
        requestsThisMinute.add(now);
        Map<Long, Exception> category = rateLimitByClass.computeIfAbsent(action.getClass(), f -> new ConcurrentHashMap<>());
        category.put(now, new Exception());

        if (category.size() > 1) category.entrySet().removeIf(f -> f.getKey() < cutoff);
        if (requestsThisMinute.size() > 1) requestsThisMinute.removeIf(f -> f < cutoff);

        if (requestsThisMinute.size() > 20) {
            if (lastLimitTime < cutoff || requestsThisMinute.size() > lastLimitTotal + 10) {
                lastLimitTime = now;
                lastLimitTotal = requestsThisMinute.size();

                new Exception().printStackTrace();

                StringBuilder response = new StringBuilder("\n\n----------- RATE LIMIT: " + requestsThisMinute.size() + " -------------");
                for (Map.Entry<Class, Map<Long, Exception>> entry : rateLimitByClass.entrySet()) {
                    category = entry.getValue();
                    if (category.size() > 1) category.entrySet().removeIf(f -> f.getKey() < cutoff);
                    if (category.size() > 1) {
                        response.append("\n\n" + entry.getKey().getSimpleName() + " = " + category.size());
                        if (category.size() > 5) {
                            Map<String, Integer> exceptionStrings = new HashMap<>();
                            for (Exception value : category.values()) {
                                String key = StringMan.stacktraceToString(value.getStackTrace());
                                int amt = exceptionStrings.getOrDefault(key, 0) + 1;
                                exceptionStrings.put(key, amt);
                            }
                            for (Map.Entry<String, Integer> entry2 : exceptionStrings.entrySet()) {
                                response.append("\n - " + entry2.getValue() + ": " + entry2.getKey());
                            }

                        }
                    }
                }
            }
        } else {
            lastLimitTotal = 0;
        }

        return action;
    }

    private static final Map<Long, List<Map.Entry<UUID, String>>> messageQueue = new ConcurrentHashMap<>();
    private static final Map<Long, Long> messageQueueLastSent = new ConcurrentHashMap<>();

    public static void queueMessage(MessageChannel channel, String message, boolean condense) {
        if (channel instanceof JoobyChannel) {
            condense = false;
        }
        if (!condense || requestsThisMinute.size() < 10) {
            queue(channel.sendMessage(message));
            return;
        }

        int bufferSeconds;
        int requests = requestsThisMinute.size();
        if (requests < 20) bufferSeconds = 10;
        else if (requests < 30) bufferSeconds = 30;
        else if (requests < 50) bufferSeconds = 45;
        else bufferSeconds = 60;

        UUID id = UUID.randomUUID();
        long channelId = channel.getIdLong();
        synchronized (messageQueueLastSent) {
            messageQueue.computeIfAbsent(channelId, f -> new LinkedList<>()).add(new AbstractMap.SimpleEntry<>(id, message));
        }

        Locutus.imp().getCommandManager().getExecutor().schedule(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                MessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(channelId);
                if (channel == null) return;

                long now = System.currentTimeMillis();
                long last = messageQueueLastSent.getOrDefault(channelId, 0L);

                List<Map.Entry<UUID, String>> toSend = null;

                synchronized (messageQueueLastSent) {

                    List<Map.Entry<UUID, String>> messages = messageQueue.get(channelId);
                    if (messages == null || messages.isEmpty()) return;

                    boolean isMyMessageLatest = messages.get(messages.size() - 1).getKey() == id;

                    if (now - last < bufferSeconds * 1000L || isMyMessageLatest) {
                        toSend = messageQueue.remove(channelId);
                        messageQueueLastSent.put(channelId, now);
                    }
                }
                if (toSend != null) {
                    List<String> messages = toSend.stream().map(Map.Entry::getValue).collect(Collectors.toList());
                    String combined = StringMan.join(messages, "\n");
                    DiscordUtil.sendMessage(channel, combined);
                }
            }
        }, bufferSeconds, TimeUnit.SECONDS);

    }

    public static void queue(MessageAction action) {
        addRequest(action).queue();
    }

    public static Message complete(MessageAction action) {
        return (Message) addRequest(action).complete();
    }

    public static <T> T complete(RestAction<T> action) {
        return (T) addRequest(action).complete();
    }

    public static <T> void queue(RestAction<T> action) {
        if (action == null) return;
        addRequest(action).queue();
    }
}
