package link.locutus.discord.network;

import link.locutus.discord.config.Settings;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.*;

public class SocksProxy implements IProxy {
    private final Proxy proxy;
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPass;

    public SocksProxy(String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUser = proxyUser;
        this.proxyPass = proxyPass;
        this.proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.SERVER &&
                        getRequestingHost().equals(proxyHost) &&
                        getRequestingPort() == proxyPort) {
                    return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
                }
                return null;
            }
        });
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
        Connection connection = Jsoup.connect(url).proxy(this.proxy);
        return connection;
    }
}