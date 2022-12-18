package link.locutus.discord.network;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public interface IProxy {
    public HttpURLConnection connect(URL url) throws IOException;

    default boolean isPassthrough() {
        return false;
    }
}
