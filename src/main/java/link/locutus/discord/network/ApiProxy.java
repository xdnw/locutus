package link.locutus.discord.network;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ApiProxy implements IProxy {
    private final String urlWithPlaceholder;
    private final String key;

    /**
     *
     * @param urlWithPlaceholder Uses String.format for the url and key (as ordered)
     * @param key the api key
     */
    public ApiProxy(String urlWithPlaceholder, String key) {
        this.urlWithPlaceholder = urlWithPlaceholder;
        this.key = key;
    }

    public String getEncodedUrl(String urlStr) {
        String urlEncoded = URLEncoder.encode(urlStr, StandardCharsets.UTF_8);
        String proxyEncoded = String.format(urlWithPlaceholder, urlEncoded, key);
        return proxyEncoded;
    }

    @Override
    public HttpURLConnection connect(URL urlStr) throws IOException {
        String proxyUrl = getEncodedUrl(urlStr.toString());
        URL url = new URL(proxyUrl);
        return (HttpURLConnection) url.openConnection();
    }
}
