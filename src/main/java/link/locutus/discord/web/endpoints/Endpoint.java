package link.locutus.discord.web.endpoints;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Endpoint {
    private final String name;
    public Map<String, Endpoint> children;
    public String childrenHtml;

    public Endpoint(String name) {
        this.children = new LinkedHashMap<>();
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public Endpoint add(Endpoint child, String... aliases) {
        for (String alias : aliases) {
            children.put(alias, child);
        }
        if (aliases.length == 0) {
            children.put(child.toString(), child);
        }
        childrenHtml = null;
        return this;
    }

    public final String toList(Map<?, ?> urlMap) {
        StringBuilder html = new StringBuilder();
        html.append("<ul>");
        for (Map.Entry<?, ?> entry : urlMap.entrySet()) {
            html.append("<li><a href='" + entry.getValue() + "'>" + entry.getKey() + "</a></li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    public final String toList(List<String> mylinks) {
        StringBuilder html = new StringBuilder();
        html.append("<ul>");
        for (String link : mylinks) {
            html.append("<li><a href='" + link + "'>" + link + "</a></li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    public final String defaultHtml(long userId) {
        if (childrenHtml != null) return childrenHtml;
        Map<Endpoint, String> byEndpoint = new LinkedHashMap<>();
        for (Map.Entry<String, Endpoint> entry : children.entrySet()) {
            byEndpoint.putIfAbsent(entry.getValue(), entry.getKey());
        }
        return childrenHtml = toList(byEndpoint);
    }

    public String apply(long userId, List<String> path) {
        if (!path.isEmpty()) {
            Endpoint child = children.get(path.get(0));
            if (child != null) {
                path.remove(0);
                return child.apply(userId, path);
            }
        }
        return defaultHtml(userId);
    }
}
