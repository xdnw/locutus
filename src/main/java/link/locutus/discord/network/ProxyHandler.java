package link.locutus.discord.network;

public class ProxyHandler {
    private String proxyHost;
    private int proxyPort;
    private String proxyUser;
    private String proxyPass;

    public ProxyHandler(String proxyHost, int proxyPort, String proxyUser, String proxyPass) {
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
}
