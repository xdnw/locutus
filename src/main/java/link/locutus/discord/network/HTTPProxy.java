package link.locutus.discord.network;

import java.net.HttpURLConnection;
import java.net.URL;

public class HTTPProxy implements IProxy {
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPass;

    public HTTPProxy(String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.proxyUser = proxyUser;
        this.proxyPass = proxyPass;
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
    public HttpURLConnection connect(URL url) {
        return null;
    }
}
