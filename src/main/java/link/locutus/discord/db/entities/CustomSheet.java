package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            response.append("**Tabs:** None\n");
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
        return update(store, tabs);
    }

    public List<String> update(ValueStore store, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> customTabs) throws GeneralSecurityException, IOException {
        SpreadSheet sheet = SpreadSheet.create(sheetId);
        List<String> errors = new ArrayList<>();

        List<Future<?>> writeTasks = new ArrayList<>();
        ExecutorService executor = Locutus.imp().getExecutor();
        sheet.reset();
        Map<String, Boolean> tabsCreated = new LinkedHashMap<>();
        Future<?> createTabsFuture = executor.submit(() -> {
            try {
                Set<String> customTabsKeys = customTabs.keySet();
                Set<String> customTabsKeysLower = customTabsKeys.stream().map(String::toLowerCase).collect(Collectors.toSet());
                Map<String, Boolean> result = sheet.updateCreateTabsIfAbsent(customTabsKeys);
                tabsCreated.putAll(result);
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue() && customTabsKeysLower.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                        sheet.clearAllButFirstRow(entry.getKey());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Set<String> tabsUpdated = new HashSet<>();
        for (Map.Entry<String, Map.Entry<SelectionAlias, SheetTemplate>> entry : customTabs.entrySet()) {
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

            Placeholders<Object> ph = Locutus.cmd().getV2().getPlaceholders().get(type);
            if (ph == null) {
                errors.add("[Tab: `" + tabName + "`] Invalid type: `" + type.getSimpleName() + "`");
                continue;
            }

            Future<?> future = executor.submit(() -> {
                try {
                    Set<Object> selection = ph.deserializeSelection(store, alias.getSelection());
                    List<String> columns = template.getColumns();
                    List<Object> header = new ArrayList<>(columns);
                    for (int i = 0; i < header.size(); i++) {
                        if (header.get(i) instanceof String str && str.startsWith("=")) {
                            // add ' prefix when starts
                            header.set(i, "'" + str);
                        }
                    }

                    // add header
                    sheet.addRow(tabName, header);

                    // get write cache
                    PlaceholderCache<?> cache = new PlaceholderCache<>(selection);
                    store.addProvider(Key.of(PlaceholderCache.class, ph.getType()), cache);

                    List<Function<Object, String>> functions = new ArrayList<>();
                    for (String column : columns) {
                        try {
                            Function<Object, String> function = ph.getFormatFunction(store, column, true);
                            functions.add(function);
                        } catch (IllegalArgumentException e) {
                            errors.add("[Tab: `" + tabName + "`,Column:`" + column + "`] " + StringMan.stripApiKey(e.getMessage()));
                            functions.add(null);
                        }
                    }
                    for (Object o : selection) {
                        for (int i = 0; i < columns.size(); i++) {
                            Function<Object, String> function = functions.get(i);
                            if (function == null) {
                                header.set(i, "");
                                continue;
                            }
                            try {
                                String value1 = function.apply(o);
                                header.set(i, value1);
                            } catch (Exception e) {
                                String column = columns.get(i);
                                String elemStr = ph.getName(o);
                                errors.add("[Tab: `" + tabName + "`,Column:`" + column + "`,Elem:`" + elemStr + "`] " + StringMan.stripApiKey(e.getMessage()));
                            }
                        }
                        sheet.addRow(tabName, header);
                    }
                    if (selection.isEmpty()) {
                        errors.add("[Tab: `" + tabName + "`] No elements found for selection: `" + alias.getSelection() + "`");
                    }
                    createTabsFuture.get();
                    sheet.updateWrite(tabName);
                    errors.add("[Tab: `" + tabName + "`] Finished update.");
                    tabsUpdated.add(tabName.toLowerCase(Locale.ROOT));
                } catch (Exception e) {
                    e.printStackTrace();
                    errors.add("[Tab: `" + tabName + "`] " + StringMan.stripApiKey(e.getMessage()));
                }
            });
            writeTasks.add(future);
        }
        for (Future<?> writeTask : writeTasks) {
            try {
                writeTask.get();
            } catch (Exception e) {
                errors.add(StringMan.stripApiKey(e.getMessage()));
            }
        }
        for (Map.Entry<String, Boolean> entry : tabsCreated.entrySet()) {
            if (tabsUpdated.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            errors.add("[Tab: `" + entry.getKey() + "`] Exists in the google sheet, but has no template.");
        }
        return errors;
    }
}
