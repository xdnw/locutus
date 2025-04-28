package link.locutus.discord.network;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static link.locutus.discord.network.PassthroughProxy.TIMEOUT_MILLIS;

public class ApiProxy implements IProxy {
    private final String urlWithPlaceholder;

    /**
     *
     * @param urlWithPlaceholder Uses String.format for the url and key (as ordered)
     */
    public ApiProxy(String urlWithPlaceholder) {
        this.urlWithPlaceholder = urlWithPlaceholder;
    }

    public String getEncodedUrl(String urlStr) {
        String urlEncoded = URLEncoder.encode(urlStr, StandardCharsets.UTF_8);
        String proxyEncoded = urlWithPlaceholder.replace("%url%", urlEncoded);
        return proxyEncoded;
    }

    @Override
    public Connection connect(String urlStr) throws IOException {
        String proxyUrl = getEncodedUrl(urlStr.toString());
        Connection connection = Jsoup.connect(proxyUrl).timeout(TIMEOUT_MILLIS);
        return connection;
    }
}
