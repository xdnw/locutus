package link.locutus.discord.db.entities;

import java.util.Map;
import java.util.Set;

public class CustomSheet {
    private final String url;
    private final Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs;

    public CustomSheet(String url, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs) {
        this.url = url;
        this.tabs = tabs;
    }

    public String getUrl() {
        return url;
    }

    public Set<String> getTabs() {
        return tabs.keySet();
    }

    public Map.Entry<SelectionAlias, SheetTemplate> getTab(String tab) {
        return tabs.get(tab);
    }
}
