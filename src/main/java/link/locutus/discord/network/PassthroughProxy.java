package link.locutus.discord.network;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;

public class PassthroughProxy implements IProxy {
    public static final IProxy INSTANCE = new PassthroughProxy();
    public static final int TIMEOUT_MILLIS = 60000; // Set timeout to 30 seconds

    @Override
    public Connection connect(String url) throws IOException {
        return Jsoup.connect(url).timeout(TIMEOUT_MILLIS);
    }

    @Override
    public boolean isPassthrough() {
        return true;
    }
}
