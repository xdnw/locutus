package link.locutus.discord.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProxyHandler {
    private final List<IProxy> proxies = new ArrayList<>();
    private final IProxy passThrough = new PassthroughProxy();
    private int index = 0;

    public ProxyHandler() {

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
            return passThrough;
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
        if (proxies.size() == 0) return passThrough;
        synchronized (proxies) {
            IProxy proxy = proxies.get(index++);
            if (index >= proxies.size()) index = 0;
            return proxy;
        }
    }
}
