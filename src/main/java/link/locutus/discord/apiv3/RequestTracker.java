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
    private static final int MAX_RATE_LIMIT_RETRIES = 4;

    private final Map<String, Integer> DOMAIN_MAP = new ConcurrentHashMap<>();
    private final AtomicInteger DOMAIN_COUNTER = new AtomicInteger(0);
    private final Map<Integer, Map<String, List<Long>>> DOMAIN_REQUESTS = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Boolean> DOMAIN_HAS_RATE_LIMITING = new ConcurrentHashMap<>();
    private final Map<Integer, Long> DOMAIN_RETRY_AFTER = new ConcurrentHashMap<>();

    public boolean hasRateLimiting(int domainId) {
        return DOMAIN_HAS_RATE_LIMITING.getOrDefault(domainId, false);
    }

    public void setRateLimited(int domainId, boolean rateLimited) {
        if (rateLimited) {
            DOMAIN_HAS_RATE_LIMITING.put(domainId, true);
            return;
        }
        DOMAIN_HAS_RATE_LIMITING.remove(domainId);
        DOMAIN_RETRY_AFTER.remove(domainId);
    }

    public long getRetryAfter(URI url) {
        return getRetryAfter(getDomainId(url));
    }

    public long getRetryAfter(int domainId) {
        return DOMAIN_RETRY_AFTER.getOrDefault(domainId, 0L);
    }

    private Throwable strip(Throwable e) {
        if (e.getMessage() != null) {
            String stripped = StringMan.stripApiKey(e.getMessage());
            if (!Objects.equals(e.getMessage(), stripped)) {
                return new RuntimeException(stripped, e);
            }
        }
        return e;
    }

    private RuntimeException asRuntime(Throwable e) {
        if (e instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(e);
    }

    public <T> T runWithRetryAfter(final PageRequestQueue.PageRequestTask<T> task) {
        int domainId = getDomainId(task.getUrl());
        long now = System.currentTimeMillis();
        long retryAt = getRetryAfter(domainId);
        if (retryAt > now) {
            throw new RetryableRequestException(retryAt, null);
        }

        try {
            addRequest(task.getUrl());
            Supplier<T> supplier = task.getTask();
            T result = supplier.get();
            DOMAIN_HAS_RATE_LIMITING.remove(domainId);
            DOMAIN_RETRY_AFTER.remove(domainId);
            task.resetRateLimitAttempts();
            task.clearDeferral();
            return result;
        } catch (FileUtil.TooManyRequests e) {
            Logg.error("API requests are being rate limited. Will Retry After (1): " + e.getRetryAfter() + " | " + e.getMessage());
            throw createRetryException(task, domainId, now, e.getRetryAfter(), strip(e));
        } catch (HttpClientErrorException.TooManyRequests e) {
            Integer retryAfterSeconds = extractRetryAfterSeconds(e, now, task.getUrl());
            Logg.error("API requests are being rate limited. Will Retry After (2): " + retryAfterSeconds + " | " + e.getResponseHeaders());
            throw createRetryException(task, domainId, now, retryAfterSeconds, strip(e));
        } catch (Throwable e) {
            Throwable stripped = strip(e);
            Logg.error("API request: " + stripped.getMessage() + " on " + task.getUrl());
            throw asRuntime(stripped);
        }
    }

    private Integer extractRetryAfterSeconds(HttpClientErrorException.TooManyRequests e, long now, URI url) {
        Integer retryAfter = null;
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
                            Logg.error("API is being rate limited, however no recognized `Retry-After` was returned. debug info: " + diff + " | " + reset + " for " + url + " | " + resetStr + " | " + now);
                            diff = 4000;
                        }
                        retryAfter = (int) ((diff + 999L) / 1000L);
                    }
                }
            }
        }
        return retryAfter;
    }

    private RetryableRequestException createRetryException(PageRequestQueue.PageRequestTask<?> task, int domainId, long now, Integer retryAfterSeconds, Throwable cause) {
        int attempt = task.incrementRateLimitAttempts();
        if (attempt > MAX_RATE_LIMIT_RETRIES) {
            throw asRuntime(cause);
        }

        int requestsPast2m = getDomainRequestsSince(domainId, now - TimeUnit.MINUTES.toMillis(2));
        Logg.text("Rate Limited On (2):\n" +
                "- URL: " + task.getUrl() + "\n" +
                "- Domain: " + task.getUrl().getHost() + "\n" +
                "- Retry After: " + retryAfterSeconds + "\n" +
                "- Requests Past 2m: " + requestsPast2m + "\n" +
                "- URL: " + task.getUrl());

        int minimumDelaySeconds = attempt == 1 ? 10 : attempt == 2 ? 30 : 60;
        int requestedDelaySeconds = retryAfterSeconds == null ? 0 : Math.max(retryAfterSeconds, 0);
        long retryAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(Math.max(minimumDelaySeconds, requestedDelaySeconds));

        DOMAIN_HAS_RATE_LIMITING.put(domainId, true);
        DOMAIN_RETRY_AFTER.put(domainId, retryAt);
        return new RetryableRequestException(retryAt, cause);
    }

    public static class RetryableRequestException extends RuntimeException {
        private final long retryAtMillis;

        public RetryableRequestException(long retryAtMillis, Throwable cause) {
            super(cause);
            this.retryAtMillis = retryAtMillis;
        }

        public long getRetryAtMillis() {
            return retryAtMillis;
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
        addRequest(getDomainId(url), queryStr);
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
                    if (index == -1 || index >= requestTimes.size()) {
                        continue;
                    }
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
        return countByDomain.entrySet()
                .stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    public int getDomainRequestsSince(URI url, long timestamp) {
        return getDomainRequestsSince(getDomainId(url), timestamp);
    }

    public int getDomainRequestsSince(int id, long timestamp) {
        int count = 0;
        synchronized (DOMAIN_REQUESTS) {
            Map<String, List<Long>> domainRequests = DOMAIN_REQUESTS.get(id);
            if (domainRequests == null || domainRequests.isEmpty()) {
                return 0;
            }
            for (List<Long> requestTimes : domainRequests.values()) {
                if (requestTimes.isEmpty()) {
                    continue;
                }
                int index = ArrayUtil.binarySearchGreater(requestTimes, f -> f >= timestamp);
                if (index == -1) {
                    continue;
                }
                count += Math.max(0, requestTimes.size() - index);
            }
        }
        return count;
    }
}
