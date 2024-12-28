package link.locutus.discord.network;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class PassthroughProxy implements IProxy {
    public static final IProxy INSTANCE = new PassthroughProxy();

    @Override
    public Connection connect(String url) throws IOException {
        return Jsoup.connect(url.toString());
    }

    @Override
    public boolean isPassthrough() {
        return true;
    }
}
