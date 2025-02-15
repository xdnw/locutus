package link.locutus.discord.network;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.*;
import java.util.Base64;

import static link.locutus.discord.network.PassthroughProxy.TIMEOUT_MILLIS;

public class HTTPProxy implements IProxy {
    private final Proxy proxy;
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPass;

    public HTTPProxy(String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUser = proxyUser;
        this.proxyPass = proxyPass;
        this.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public String getProxyPass() {
        return proxyPass;
    }

    @Override
    public Connection connect(String url) throws IOException {
        Connection connection = Jsoup.connect(url.toString()).proxy(proxy).timeout(TIMEOUT_MILLIS);
        if (proxyUser != null && proxyPass != null) {
            String encoded = Base64.getEncoder().encodeToString((proxyUser + ":" + proxyPass).getBytes());
            connection.header("Proxy-Authorization", "Basic " + encoded);
        }
        return connection;
    }
}
