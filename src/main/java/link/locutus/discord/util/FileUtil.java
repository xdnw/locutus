package link.locutus.discord.util;

import link.locutus.discord.network.IProxy;
import link.locutus.discord.network.PassthroughProxy;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.io.PageRequestQueue;
import org.eclipse.jetty.util.UrlEncoded;
import org.jsoup.Connection;

import java.nio.channels.Channels;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FileUtil {
    private static final String COOKIES_HEADER = "Set-Cookie";

    public static String readFile(String name) {
        try (InputStream resource = FileUtil.class.getResourceAsStream(name)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = resource.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            buffer.flush();
            byte[] bytes = buffer.toByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static List<String> getResourceFiles(String path) throws IOException {
        List<String> filenames = new ArrayList<>();

        try (
                InputStream in = getResourceAsStream(path);
                BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                filenames.add(resource);
            }
        }

        return filenames;
    }

    public static InputStream getResourceAsStream(String resource) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream(resource);

        return in == null ? FileUtil.class.getResourceAsStream(resource) : in;
    }

    public static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    public static <T> T submit(int priority, int maxBuffer, int maxDelay, Supplier<T> task, String url) {
        return get(pageRequestQueue.submit(task, getPriority(priority), maxBuffer, maxDelay, url));
    }

    public static <T> T submit(int priority, int maxBuffer, int maxDelay, Supplier<T> task, URI url) {
        return get(pageRequestQueue.submit(task, getPriority(priority), maxBuffer, maxDelay, url));
    }

    public static PageRequestQueue getPageRequestQueue() {
        return pageRequestQueue;
    }

    public static byte[] readBytesFromUrl(PagePriority priority, String urlStr) {
        return submit(priority.ordinal(), priority.getAllowedBufferingMs(), priority.getAllowableDelayMs(), () -> {
            try (InputStream is = new URL(urlStr).openStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] byteChunk = new byte[4096]; // Or whatever size you want to read in at a time.
                int n;

                while ( (n = is.read(byteChunk)) > 0 ) {
                    baos.write(byteChunk, 0, n);
                }
                is.close();
                return baos.toByteArray();
            }
            catch (IOException e) {
                e.printStackTrace ();
                return null;
            }
        }, urlStr);
    }

    private static void check409Error(URLConnection connection) throws IOException {
        if (connection instanceof HttpURLConnection http) {
            if (http.getResponseCode() == 429) {
                Integer retryAfter = null;
                String retry = http.getHeaderField("Retry-After");
                if (retry != null && MathMan.isInteger(retry)) {
                    retryAfter = Integer.parseInt(retry);
                }
                if (retryAfter == null) {
                    String resetStr = http.getHeaderField("X-RateLimit-Reset");
                    if (MathMan.isInteger(resetStr)) {
                        long reset = Long.parseLong(resetStr) * 1000L;
                        long diff = reset - System.currentTimeMillis();
                        if (diff > 60000) {
                            diff = 60000;
                        } else if (diff < 0) {
                            diff = 4000;
                        }
                        retryAfter = (int) ((diff + 999L) / 1000L);
                    }
                }
                if (retryAfter == null) System.out.println("Headers for retry-after " + http.getHeaderFields());
                throw new TooManyRequests("Too many requests", retryAfter);
            }
        }
    }

    public static class TooManyRequests extends RuntimeException {
        private final Integer retryAfter;

        public TooManyRequests(String message, Integer retryAfter) {
            super(message);
            this.retryAfter = retryAfter;
        }

        public Integer getRetryAfter() {
            return retryAfter;
        }
    }

    public static String readStringFromURL(PagePriority priority, String requestURL) throws IOException {
        return readStringFromURL(priority.ordinal(), priority.getAllowedBufferingMs(), priority.getAllowableDelayMs(), requestURL);
    }

    public static String readStringFromURL(int priority, int maxBuffer, int maxDelay, String requestURL) throws IOException {
        return submit(priority, maxBuffer, maxDelay, () -> {
                try {
                    URL website = new URL(requestURL);
                    URLConnection connection = website.openConnection();
                    try {
                        try (ReadableByteChannel channel = Channels.newChannel(connection.getInputStream())) {
                            ByteBuffer buffer = ByteBuffer.allocate(1024);
                            StringBuilder response = new StringBuilder();

                            while (channel.read(buffer) != -1) {
                                buffer.flip();
                                while (buffer.hasRemaining()) {
                                    response.append((char) buffer.get());
                                }
                                buffer.clear();
                            }
                            return response.toString();
                        }
                    } catch (IOException e) {
                        check409Error(connection);
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            },
            requestURL
        );
    }

    public static String encode(String url) throws UnsupportedEncodingException {
        String[] split = url.split("\\?", 2);
        if (split.length == 1) return url;
        return split[0] + "?" + UrlEncoded.encodeString(split[1], StandardCharsets.UTF_8);
    }

    public static CompletableFuture<String> readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, null);
    }

    public static CompletableFuture<String> readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments, CookieManager msCookieManager) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, true, msCookieManager, i -> {});
    }

    public static CompletableFuture<String> readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments, boolean post, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, post, msCookieManager, PassthroughProxy.INSTANCE, apply);
    }

    public static CompletableFuture<String> readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments, boolean post, CookieManager msCookieManager, IProxy proxy, Consumer<HttpURLConnection> apply) throws IOException {
        IProxy finalProxy = proxy == null ? PassthroughProxy.INSTANCE : proxy;
        Supplier<String> jsoupTask = () -> {
            try {
                Connection connection = finalProxy.connect(urlStr);
                connection = connection.method(post ? Connection.Method.POST : Connection.Method.GET)
                        .ignoreContentType(true);

                // Add arguments to the request
                if (arguments != null) {
                    connection.data(arguments);
                }

                // Add cookies to the request
                if (msCookieManager != null) {
                    for (HttpCookie cookie : msCookieManager.getCookieStore().getCookies()) {
                        connection.cookie(cookie.getName(), cookie.getValue());
                    }
                }

                // Apply additional settings to the connection
                if (apply != null) {
                    apply.accept((HttpURLConnection) connection.request().url().openConnection());
                }

                // Execute the request and get the response
                Connection.Response response = connection.execute();

                // Store cookies from the response
                if (msCookieManager != null) {
                    for (Map.Entry<String, String> cookie : response.cookies().entrySet()) {
                        msCookieManager.getCookieStore().add(null, new HttpCookie(cookie.getKey(), cookie.getValue()));
                    }
                }

                return response.body();
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        };

        PageRequestQueue.PageRequestTask<String> task = pageRequestQueue.submit(jsoupTask,
                getPriority(priority.ordinal()), priority.getAllowedBufferingMs(), priority.getAllowableDelayMs(), urlStr);
        return task;
    }

    public static <T> T get(Future<T> myFuture) {
        try {
            return myFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause != null && cause instanceof RuntimeException run) throw run;
            throw new RuntimeException(e);
        }
    }

    public enum RequestType {
        GET,
        POST,
    }

    private static PageRequestQueue pageRequestQueue = new PageRequestQueue(8);
    private static AtomicInteger requestOrder = new AtomicInteger();

    private static long getPriority(int priority) {
        return requestOrder.incrementAndGet() + Integer.MAX_VALUE * (long) priority;
    }

    public static void waitRateLimit(URI domain, long maxWaitMs, long waitIntervalMs) {
        int domainId = pageRequestQueue.getTracker().getDomainId(domain);
        if (hasRateLimiting(domainId)) {
            long endWait = System.currentTimeMillis() + maxWaitMs;
            do {
                try {
                    Thread.sleep(waitIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while (System.currentTimeMillis() < endWait && hasRateLimiting(domainId));
        }
    }

    public static class RateLimitSkipper {
        private final int domainId;
        private final long maxWaitMs;
        private long waitUntil = -1;

        public RateLimitSkipper(URI url, long maxWaitMs) {
            this.domainId = pageRequestQueue.getTracker().getDomainId(url);
            this.maxWaitMs = maxWaitMs;
        }

        public boolean shouldSkip() {
            if (waitUntil != -1) {
                if (System.currentTimeMillis() > waitUntil) {
                    waitUntil = -1;
                    return false;
                }
                if (hasRateLimiting(domainId)) {
                    return true;
                }
                waitUntil = -1;
                return false;
            } else if (hasRateLimiting(domainId)) {
                waitUntil = System.currentTimeMillis() + maxWaitMs;
                return true;
            } else {
                return false;
            }
        }
    }

    public static boolean hasRateLimiting(URI url) {
        int domain = pageRequestQueue.getTracker().getDomainId(url);
        return hasRateLimiting(domain);
    }

    private static boolean hasRateLimiting(int domain) {
        return pageRequestQueue.getTracker().hasRateLimiting(domain);
    }

    public static void setRateLimited(URI uri, boolean limited) {
        int domain = pageRequestQueue.getTracker().getDomainId(uri);
        pageRequestQueue.getTracker().setRateLimited(domain, limited);
    }
}
