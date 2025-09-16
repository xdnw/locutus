package link.locutus.discord.apiv3;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PageRequestQueue;
import link.locutus.discord.util.math.ArrayUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RequestTracker {
    // A map of the domain to the id, the id is from the domain counter, must be atomic
    private final Map<String, Integer> DOMAIN_MAP = new ConcurrentHashMap<>();
    private final Map<Integer, Object> DOMAIN_LOCKS = new ConcurrentHashMap<>();
    // The domain counter, increment for each new domain
    private final AtomicInteger DOMAIN_COUNTER = new AtomicInteger(0);
    // The map of the domain id, to the url and then the list of times being requested
    private final Map<Integer, Map<String, List<Long>>> DOMAIN_REQUESTS = new Int2ObjectOpenHashMap<>();

    private final Map<Integer, Boolean> DOMAIN_HAS_RATE_LIMITING = new ConcurrentHashMap<>();
    private final Map<Integer, Long> DOMAIN_RETRY_AFTER = new ConcurrentHashMap<>();

    public boolean hasRateLimiting(int domainId) {
        return DOMAIN_HAS_RATE_LIMITING.getOrDefault(domainId, false);
    }

    public void setRateLimited(int domainId, boolean rateLimited) {
        DOMAIN_HAS_RATE_LIMITING.put(domainId, rateLimited);
    }

    public long getRetryAfter(URI url) {
        int id = getDomainId(url);
        return getRetryAfter(id);
    }

    public long getRetryAfter(int domainId) {
        return DOMAIN_RETRY_AFTER.getOrDefault(domainId, 0L);
    }

    private void setRetryAfter(URI url, int seconds) {
        int domainId = getDomainId(url);
        long currentTime = System.currentTimeMillis();
        long expiresAt = currentTime + TimeUnit.SECONDS.toMillis(seconds);
        DOMAIN_RETRY_AFTER.put(domainId, expiresAt);
    }

    public <T> void runWithRetryAfter(PageRequestQueue.PageRequestTask<T> task) {
        runWithRetryAfter(task, 0);
    }

    private Throwable strip(Throwable e) {
        if (e.getMessage() != null) {
            String stripped = StringMan.stripApiKey(e.getMessage());
            if (!Objects.equals(e.getMessage(), stripped)) {
                return new RuntimeException(stripped);
            }
        }
        return e;
    }

    private <T> void runWithRetryAfter(final PageRequestQueue.PageRequestTask<T> task, int depth) {
        int domainId = getDomainId(task.getUrl());

        long now = System.currentTimeMillis();
        long retryMs = getRetryAfter(task.getUrl());
        boolean isRateLimited = false;
        Integer retryAfter = null;
        try {
            if (retryMs > now) {
                try {
                    long diff = retryMs - now;
                    if (diff > 60000) {
                        diff = 60000;
                    }
                    Logg.text("Rate Limited On (1):\n" +
                            "- Domain: " + task.getUrl().getHost() + "\n" +
                            "- Request: " + task.getUrl() + "\n" +
                            "- Retry After: " + (retryMs - now) + "ms");
                    Thread.sleep(diff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            addRequest(task.getUrl());
            Supplier<T> supplier = task.getTask();
            if (!task.complete(supplier.get())) {
                new Exception().printStackTrace();
                task.completeExceptionally(new RuntimeException("Task failed"));
            }
            DOMAIN_HAS_RATE_LIMITING.remove(domainId);
            return;
        } catch (FileUtil.TooManyRequests e) {
            if (depth > 3) {
                task.completeExceptionally(strip(e));
                return;
            }
            isRateLimited = true;
            retryAfter = e.getRetryAfter();
            Logg.error("API requests are being rate limited. Will Retry After (1): " + retryAfter + " | " + e.getMessage());
        } catch (HttpClientErrorException.TooManyRequests e) {
            if (depth > 3) {
                task.completeExceptionally(strip(e));
                return;
            }
            isRateLimited = true;
            HttpHeaders headers = e.getResponseHeaders();
            if (headers != null) {
                String retryStr = headers.getFirst("Retry-After");
                if (MathMan.isInteger(retryStr)) {
                    retryAfter = Integer.parseInt(retryStr);
                }
                if (retryAfter == null) {
                    String resetStr = headers.getFirst("X-RateLimit-Reset-After");
                    if (MathMan.isInteger(resetStr)) {
                        long reset = Long.parseLong(resetStr) * 1000L;
                        if (reset < 1000 && reset > 0) {
                            retryAfter = (int) reset;
                        } else if (reset < 60000) {
                            retryAfter = (int) ((reset + 999L) / 1000L);
                        } else {
                            long diff = reset - now;
                            if (diff > 60000) {
                                diff = 60000;
                            } else if (diff < 0) {
                                Logg.error("API is being rate limited, however no recognized `Retry-After` was returned. debug info: " + diff + " | " + reset + " for " + task.getUrl() + " | " + resetStr + " | " + now);
                                diff = 4000;
                            }
                            retryAfter = (int) ((diff + 999L) / 1000L);
                        }
                    }
                }
            }
            Logg.error("API requests are being rate limited. Will Retry After (2): " + retryAfter + " | " + headers);
        } catch (Throwable e) {
            Logg.error("API request: " + e.getMessage() + " on " + task.getUrl());
            task.completeExceptionally(strip(e));
            throw e;
        }
        if (isRateLimited) {
            try {
                // print rate limit when it hits (retry after, + how many requests on that domain + the domain)
                {
                    int requestsPast2m = getDomainRequestsSince(domainId, now - TimeUnit.MINUTES.toMillis(2));
                    Logg.text("Rate Limited On (2):\n" +
                            "- URL: " + task.getUrl() + "\n" +
                            "- Domain: " + task.getUrl().getHost() + "\n" +
                            "- Retry After: " + retryAfter + "\n" +
                            "- Requests Past 2m: " + requestsPast2m + "\n" +
                            "- URL: " + task.getUrl());
                }

                now = System.currentTimeMillis();
                int delayS = depth > 0 ? depth > 1 ? 60 : 30 : 10;
                if (retryAfter == null) retryAfter = delayS;
                long minimumRetry = now + TimeUnit.SECONDS.toMillis(delayS);

                setRetryAfter(task.getUrl(), retryAfter);
                DOMAIN_HAS_RATE_LIMITING.put(domainId, true);

                Object lock = DOMAIN_LOCKS.computeIfAbsent(domainId, k -> new Object());
                synchronized (lock) {
                    now = System.currentTimeMillis();
                    long timestamp = Math.max(minimumRetry, getRetryAfter(task.getUrl()));
                    if (timestamp > now) {
                        // sleep remaining ms
                        long sleepMs = timestamp - now;
                        try {
                            if (sleepMs > 60000) {
                                sleepMs = 60000;
                            }
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    runWithRetryAfter(task, depth + 1);
                }
            } catch (Throwable e) {
                task.completeExceptionally(strip(e));
                throw e;
            }
        }
    }

    public int getDomainId(URI url) {
        String urlStr = url.toString();
        String host;
        if (urlStr.contains((Settings.INSTANCE.TEST ? "api-test" : "api") + ".politicsandwar.com/subscriptions/")) {
            host = (Settings.INSTANCE.TEST ? "api-test" : "api") + ".politicsandwar.com/subscriptions";
        } else {
            host = url.getHost();
        }
        return DOMAIN_MAP.computeIfAbsent(host, k -> DOMAIN_COUNTER.incrementAndGet());
    }

    public void addRequest(String queryStr, URI url) {
        int domainId = getDomainId(url);
        addRequest(domainId, queryStr);
    }

    public void addRequest(int domainId, String url) {
        long currentTime = System.currentTimeMillis();

        synchronized (DOMAIN_REQUESTS) {
            DOMAIN_REQUESTS.computeIfAbsent(domainId, k -> new Object2ObjectOpenHashMap<>())
                    .computeIfAbsent(url, k -> new ArrayList<>())
                    .add(currentTime);
        }
    }

    public void addRequest(URI url) {
        addRequest(url.toString(), url);
    }

    public void purgeRequests(long timestamp) {
        synchronized (DOMAIN_REQUESTS) {
            for (Map<String, List<Long>> domainRequests : DOMAIN_REQUESTS.values()) {
                Iterator<Map.Entry<String, List<Long>>> iterator = domainRequests.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, List<Long>> entry = iterator.next();
                    List<Long> requestTimes = entry.getValue();
                    requestTimes.removeIf(time -> time < timestamp);
                    if (requestTimes.isEmpty()) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    public Map<String, Integer> getCountByUrl(long timestamp) {
        Map<String, Integer> counts = new Object2IntOpenHashMap<>();
        synchronized (DOMAIN_REQUESTS) {
            for (Map.Entry<Integer, Map<String, List<Long>>> domEntry : DOMAIN_REQUESTS.entrySet()) {
                for (Map.Entry<String, List<Long>> urlEntry : domEntry.getValue().entrySet()) {
                    String url = urlEntry.getKey();
                    List<Long> requestTimes = urlEntry.getValue();
                    int index = ArrayUtil.binarySearchGreater(requestTimes, f -> f >= timestamp);
                    if (index == -1 || index >= requestTimes.size()) continue;
                    counts.put(url, requestTimes.size() - index);
                }
            }
        }

        return counts.entrySet()
        .stream()
        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public Map<String, Integer> getCountByDomain(long timestamp) {
        Map<String, Integer> countByDomain = new Object2ObjectOpenHashMap<>();
        for (Map.Entry<String, Integer> entry : DOMAIN_MAP.entrySet()) {
            int id = entry.getValue();
            int count = getDomainRequestsSince(id, timestamp);
            countByDomain.put(entry.getKey(), count);
        }
        // sorted linked hash map
        return countByDomain.entrySet()
                .stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public int getDomainRequestsSince(URI url, long timestamp) {
        int id = getDomainId(url);
        return getDomainRequestsSince(id, timestamp);
    }
    private int getDomainRequestsSince(int id, long timestamp) {
        int count = 0;
        synchronized (DOMAIN_REQUESTS) {
            Map<String, List<Long>> domainRequests = DOMAIN_REQUESTS.get(id);
            if (domainRequests == null || domainRequests.isEmpty()) return 0;
            for (List<Long> requestTimes : domainRequests.values()) {
                if (requestTimes.isEmpty()) continue;
                int index = ArrayUtil.binarySearchGreater(requestTimes, f -> f >= timestamp);
                if (index == -1) continue;
                count += Math.max(0, requestTimes.size() - index);
            }
        }
        return count;
    }
}
