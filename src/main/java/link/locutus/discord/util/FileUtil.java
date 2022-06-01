package link.locutus.discord.util;

import org.eclipse.jetty.util.UrlEncoded;

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
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

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

    public static byte[] readBytesFromUrl(String urlStr) {
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
    }

    public static String readStringFromURL(String requestURL) throws IOException {
        URL website = new URL(requestURL);
        URLConnection connection = website.openConnection();
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(connection.getInputStream()))) {

            StringBuilder response = new StringBuilder();
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }

    public static String encode(String url) throws UnsupportedEncodingException {
        String[] split = url.split("\\?", 2);
        if (split.length == 1) return url;
        return split[0] + "?" + UrlEncoded.encodeString(split[1], StandardCharsets.UTF_8);
    }

    public static String readStringFromURL(String urlStr, Map<String, String> arguments) throws IOException {
        return readStringFromURL(urlStr, arguments, null);
    }

    public static String readStringFromURL(String urlStr, Map<String, String> arguments, CookieManager msCookieManager) throws IOException {
        return readStringFromURL(urlStr, arguments, true, msCookieManager, i -> {});
    }

    public static String readStringFromURL(String urlStr, Map<String, String> arguments, boolean post, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) throws IOException {
        StringJoiner sj = new StringJoiner("&");
        for (Map.Entry<String, String> entry : arguments.entrySet())
            sj.add(URLEncoder.encode(entry.getKey(), "UTF-8") + "="
                    + URLEncoder.encode(entry.getValue(), "UTF-8"));
        byte[] out = sj.toString().getBytes(StandardCharsets.UTF_8);
        return readStringFromURL(urlStr, out, post, msCookieManager, apply);
    }

    public static String readStringFromURL(String urlStr, byte[] dataBinary, boolean post, CookieManager msCookieManager, Consumer<HttpURLConnection> apply) throws IOException {
        URL url = new URL(urlStr);
        URLConnection con = url.openConnection();
        HttpURLConnection http = (HttpURLConnection) con;

        if (msCookieManager != null && msCookieManager.getCookieStore().getCookies().size() > 0) {
            // While joining the Cookies, use ',' or ';' as needed. Most of the servers are using ';'
            http.setRequestProperty("cookie",
                    StringMan.join(msCookieManager.getCookieStore().getCookies(), ";"));
        }

        if (dataBinary != null && dataBinary.length != 0 && post) {
            http.setRequestMethod("POST"); // PUT is another valid option
        }
        http.setDoOutput(true);
        http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");


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
        }
    }
}
