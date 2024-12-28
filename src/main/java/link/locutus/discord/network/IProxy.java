package link.locutus.discord.network;

import org.jsoup.Connection;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public interface IProxy {
    public Connection connect(String url) throws IOException;

    default boolean isPassthrough() {
        return false;
    }
}
