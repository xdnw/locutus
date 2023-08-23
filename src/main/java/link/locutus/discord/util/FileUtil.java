package link.locutus.discord.util;

import link.locutus.discord.config.Settings;
import link.locutus.discord.util.io.PageRequestQueue;
import link.locutus.discord.util.offshore.Auth;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.util.UrlEncoded;
import org.springframework.web.client.HttpClientErrorException;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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

    public static <T> T submit(int priority, Supplier<T> task, String url) {
        return get(pageRequestQueue.submit(task, getPriority(priority), url));
    }

    public static <T> T submit(int priority, Supplier<T> task, URI url) {
        return get(pageRequestQueue.submit(task, getPriority(priority), url));
    }

    public static PageRequestQueue getPageRequestQueue() {
        return pageRequestQueue;
    }

    public static byte[] readBytesFromUrl(int priority, String urlStr) {
        return submit(priority, () -> {
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

    public static String readStringFromURL(int priority, String requestURL) throws IOException {
        return submit(priority, () -> {
                try {
                    URL website = new URL(requestURL);
                    URLConnection connection = website.openConnection();
                    check409Error(connection);
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(connection.getInputStream()))) {

                        StringBuilder response = new StringBuilder();
                        String inputLine;

                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        return response.toString();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
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

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, null);
    }

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments, CookieManager msCookieManager) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, true, msCookieManager, i -> {});
    }

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, Map<String, String> arguments, boolean post, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) throws IOException {
        StringJoiner sj = new StringJoiner("&");
        for (Map.Entry<String, String> entry : arguments.entrySet())
            sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                    + URLEncoder.encode(entry.getValue(), "UTF-8"));
        System.out.println("SJ " + sj);
        byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
        return readStringFromURL(priority, urlStr, out, post ? RequestType.POST : RequestType.GET, msCookieManager, apply);
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

    public static CompletableFuture<String> readStringFromURL(int priority, String urlStr, byte[] dataBinary, RequestType type, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) {
        Supplier<String> fetch = new Supplier<String>() {
            @Override
            public String get() {
//                System.out.println("Requesting " + urlStr + " at " + now + " with priority " + priority + " ( last: " + (now - lastRead) + " ). Queue size: " + pageRequestQueue.size());
                try {
                    URL url = new URL(urlStr);
                    URLConnection con = url.openConnection();
                    check409Error(con);
                    HttpURLConnection http = (HttpURLConnection) con;

                    if (msCookieManager != null && msCookieManager.getCookieStore().getCookies().size() > 0) {
                        for (HttpCookie cookie : msCookieManager.getCookieStore().getCookies()) {
                            http.addRequestProperty("Cookie", cookie.toString());
                        }
                        // While joining the Cookies, use ',' or ';' as needed. Most of the servers are using ';'
//            http.setRequestProperty("Cookie",
//                    StringMan.join(msCookieManager.getCookieStore().getCookies(), ";"));
                    }

                    http.setUseCaches(false);
                    http.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
                    http.setRequestProperty("dnt", "1");
                    http.setRequestProperty("Connection", "keep-alive");
                    http.setRequestProperty("Referer", urlStr);
                    http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    http.setRequestProperty("User-Agent", Settings.USER_AGENT);
                    if (dataBinary != null && dataBinary.length != 0 && type == RequestType.POST) {
                        http.setRequestMethod("POST");
                    } else if (type != RequestType.POST && dataBinary == null) {
                        http.setRequestMethod("GET");
                    }
                    http.setDoOutput(true);
                    http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    int length = dataBinary != null ? dataBinary.length : 0;
                    http.setFixedLengthStreamingMode(length);
                    http.setInstanceFollowRedirects(false);

                    if (apply != null) apply.accept(http);

                    http.connect();
                    if (dataBinary != null) {
                        try (OutputStream os = http.getOutputStream()) {
                            os.write(dataBinary);
                        }
                    }

                    try (InputStream is = http.getInputStream()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        int nRead;
                        byte[] data = new byte[8192];
                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }

                        buffer.flush();
                        byte[] bytes = buffer.toByteArray();

                        Map<String, List<String>> headerFields = http.getHeaderFields();
                        List<String> cookiesHeader = headerFields.get(COOKIES_HEADER);
                        if (cookiesHeader != null && msCookieManager != null) {
                            for (String cookie : cookiesHeader) {
                                msCookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
                            }
                        }


                        return new String(bytes, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        try (InputStream is = http.getErrorStream()) {
                            if (is != null) {
                                throw new IOException(e.getMessage() + ":\n" + IOUtils.toString(is, StandardCharsets.UTF_8));
                            }
                        }
                        System.out.println("URL " + urlStr);
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        PageRequestQueue.PageRequestTask<String> task = pageRequestQueue.submit(new Supplier<String>() {
            @Override
            public String get() {
                int backoff = 4000;
                while (true) {
                    try {
                        String result = fetch.get();
                        return result;
                    } catch (RuntimeException e) {
                        Throwable cause = e;
                        while (cause.getCause() != null && cause != cause.getCause()) {
                            cause = cause.getCause();
                        }
                        if (e.getMessage() != null && e.getMessage().contains("Server returned HTTP response code: 429")) {
                            System.out.println("Error 429");
                            try {
                                Thread.sleep(backoff);
                                backoff += 4000;
                            } catch (InterruptedException ex) {
                                throw new RuntimeException(ex);
                            }
                            continue;
                        }
                        throw e;
                    }
                }
            }
        }, getPriority(priority), urlStr);
        return task;
    }
}
