package link.locutus.discord.db.entities;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomSheet {
    private final String url;
    private final Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs;
    private final String name;

    public CustomSheet(String name, String url, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs) {
        this.name = name;
        this.url = url;
        this.tabs = tabs;
    }

    public String getName() {
        return name;
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

    @Override
    public String toString() {
        StringBuilder response = new StringBuilder();
        response.append("**Name:** `" + name + "`\n");
        response.append("**URL:** <" + url + ">\n");
        if (tabs.isEmpty()) {
            response.append("**Tabs:** Add one with TODO CM REF\n");
        } else {
            response.append("**Tabs:**\n");
            for (Map.Entry<String, Map.Entry<SelectionAlias, SheetTemplate>> entry : tabs.entrySet()) {
                String name = entry.getKey();
                Map.Entry<SelectionAlias, SheetTemplate> value = entry.getValue();
                SelectionAlias alias = value.getKey();
                SheetTemplate template = value.getValue();
                response.append("- `" + name + "` (type: `" + alias.getType().getSimpleName() + "`): `select:" + alias.getName() + "` -> `columns:" + template.getName() + "`\n");
                response.append(" - `" + alias.getSelection() + "`\n");
                response.append(" - `" + template.getColumns() + "`\n");
            }
        }
        return response.toString();
    }

    public List<String> update() {
        return null;
    }
}
