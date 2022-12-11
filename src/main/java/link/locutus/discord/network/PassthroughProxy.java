package link.locutus.discord.network;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class PassthroughProxy implements IProxy {
    @Override
    public HttpURLConnection connect(URL url) throws IOException {
        return (HttpURLConnection) url.openConnection();
    }

    @Override
    public boolean isPassthrough() {
        return true;
    }
}
