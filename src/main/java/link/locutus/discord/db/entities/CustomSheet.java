package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;

public class CustomSheet {
    private final String sheetId;
    private final Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs;
    private final String name;

    public CustomSheet(String name, String sheetId, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs) {
        this.name = name;
        this.sheetId = sheetId;
        this.tabs = tabs;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return "https://docs.google.com/spreadsheets/d/" + sheetId + "/edit";
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
        response.append("**Name:** `" + getName() + "`\n");
        response.append("**URL:** <" + getUrl() + ">\n");
        if (tabs.isEmpty()) {
            response.append("**Tabs:** Add one with TODO CM REF\n");
        } else {
            response.append("**Tabs:**\n");
            for (Map.Entry<String, Map.Entry<SelectionAlias, SheetTemplate>> entry : tabs.entrySet()) {
                String name = entry.getKey();
                Map.Entry<SelectionAlias, SheetTemplate> value = entry.getValue();
                SelectionAlias alias = value.getKey();
                SheetTemplate template = value.getValue();
                response.append("- `" + name + "` (type: `" + alias.getType().getSimpleName() + "`): `select:" + alias.getName() + "` -> `template:" + template.getName() + "`\n");
                response.append(" - `" + alias.getSelection() + "`\n");
                response.append(" - `" + template.getColumns() + "`\n");
            }
        }
        return response.toString();
    }

    public List<String> update(ValueStore store) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(sheetId);
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Map.Entry<SelectionAlias, SheetTemplate>> entry : this.tabs.entrySet()) {
            String tabName = entry.getKey();
            Map.Entry<SelectionAlias, SheetTemplate> value = entry.getValue();
            SelectionAlias alias = value.getKey();
            SheetTemplate template = value.getValue();

            Class type = alias.getType();
            if (!template.getType().equals(alias.getType())) {
                errors.add("[Tab: `" + tabName + "`] Incompatible types for `select:" + alias.getName() + "` and `template:" + template.getName() + "` | " +
                        "`" + alias.getType().getSimpleName() + "` != " +  "`" + template.getType().getSimpleName() + "`");
                continue;
            }

            Placeholders<?> ph = Locutus.cmd().getV2().getPlaceholders().get(type);
            if (ph == null) {
                errors.add("[Tab: `" + tabName + "`] Invalid type: `" + type.getSimpleName() + "`");
                continue;
            }

            Set<?> selection = ph.deserializeSelection(store, alias.getSelection());
            List<String> columns = template.getColumns();

            // TODO CM REF TODO FIXME
//            sheet.set();
        }
        return errors;
    }
}
