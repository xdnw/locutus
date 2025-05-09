package link.locutus.discord.network;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;

import java.util.*;

public class ProxyHandler {
    private final List<IProxy> proxies = new ObjectArrayList<>();
    private int index = 0;

    public ProxyHandler() {

    }

    public static ProxyHandler createFromSettings() {
        int port = Settings.INSTANCE.PROXY.PORT;
        String user = Settings.INSTANCE.PROXY.USER;
        String pass = Settings.INSTANCE.PROXY.PASSWORD;
        List<String> hosts = Settings.INSTANCE.PROXY.HOSTS;
        String type = Settings.INSTANCE.PROXY.TYPE;

        ProxyHandler handler = new ProxyHandler();
        for (String host : hosts) {
            Logg.info("Adding proxy: " + type + "://" + "<redacted>" + ":" + "<redacted>" + "@" + host + ":" + port);
            switch (type.toLowerCase(Locale.ROOT)) {
                case "socks" -> {
                    handler.proxies.add(new SocksProxy(host, port, user, pass));
                    break;
                }
                case "http" -> {
                    handler.proxies.add(new HTTPProxy(host, port, user, pass));
                    break;
                }
                case "api" -> {
                    host = host.replace("%user%", user).replace("%password%", pass);
                    handler.proxies.add(new ApiProxy(host));
                    break;
                }
            }
        }
        return handler;
    }

    public void addProxy(IProxy proxy) {
        proxies.add(proxy);
    }

    public List<IProxy> getProxies() {
        return proxies;
    }

    /**
     * Find a recommended host to use for the proxy.
     * The previous host will be used if it is valid.
     * A host least used in the local bucket, and (secondary) globally, is preferred
     * @param previousHost - the previous host used, or null
     * @param tier1avoid - a list of hosts used by the local bucket
     * @param tier2avoid - a list of hosts used globally
     * @return host
     */
    public IProxy recommendHost(IProxy previousHost, List<IProxy> tier1avoid, List<IProxy> tier2avoid, boolean allowPassthrough) {
        if (proxies.isEmpty()) {
            if (!allowPassthrough) throw new IllegalStateException("No proxies available");
            return PassthroughProxy.INSTANCE;
        }
        if (previousHost != null && proxies.contains(previousHost)) return previousHost;
        if (proxies.size() == 1) return proxies.get(0);
        Map<IProxy, Long> weighting = new HashMap<>();
        for (IProxy host : tier1avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + Integer.MAX_VALUE);
        for (IProxy host : tier2avoid) weighting.put(host, weighting.getOrDefault(host, 0L) + 1);

        long minVal = Long.MAX_VALUE;
        List<IProxy> minHost = new ArrayList<>();
        for (IProxy host : proxies) {
            Long val = weighting.getOrDefault(host, 0L);
            if (val < minVal) {
                minVal = val;
                minHost.clear();
                minHost.add(host);
            } else if (val == minVal) {
                minHost.add(host);
            }
        }
        // return random from min bucket
        return minHost.get((int) (Math.random() * minHost.size()));
    }

    public IProxy getNextProxy() {
        if (proxies.size() == 0) return PassthroughProxy.INSTANCE;
        synchronized (proxies) {
            IProxy proxy = proxies.get(index++);
            if (index >= proxies.size()) index = 0;
            return proxy;
        }
    }
}
