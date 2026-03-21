package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.objects.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CustomSheet {
    private final String sheetId;
    private final Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs;
    private final String name;
    private SpreadSheet sheet;

    public CustomSheet(String name, String sheetId, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> tabs) {
        this.name = name;
        this.sheetId = sheetId;
        this.tabs = tabs;
    }

    public synchronized SpreadSheet getSheet() throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(sheetId);
        }
        return sheet;
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

    public List<String> update(ValueStore store, Map<String, List<String>> exportColumns) throws GeneralSecurityException, IOException {
        return update(getSheet(), store, tabs, exportColumns);
    }

    public List<String> update(SpreadSheet sheet, ValueStore store, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> customTabs, Map<String, List<String>> exportColumns) throws GeneralSecurityException, IOException {
        List<String> messageList = new ObjectArrayList<>();
        Map<String, List<String>> errorGroups = new Object2ObjectLinkedOpenHashMap<>();
        Supplier<List<String>> toErrorList = () -> {
            List<String> errors = new ArrayList<>(messageList);
            for (Map.Entry<String, List<String>> entry : errorGroups.entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (values.isEmpty()) {
                    continue;
                }
                String error = key.replace("{value}", "`" + StringMan.join(values, "`,`") + "`");
                errors.add(error);
            }
            return errors;
        };

        ExecutorService executor = Locutus.imp().getExecutor();
        sheet.reset();
        try {
            Map<String, Boolean> tabsCreated = new Object2BooleanLinkedOpenHashMap<>();
            Set<String> customTabsKeys = customTabs.keySet();
            Set<String> customTabsKeysLower = customTabsKeys.stream()
                    .map(tab -> tab.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            Map<String, Boolean> createdTabs = sheet.updateCreateTabsIfAbsent(customTabsKeys);
            tabsCreated.putAll(createdTabs);
            for (Map.Entry<String, Boolean> entry : createdTabs.entrySet()) {
                if (!entry.getValue() && customTabsKeysLower.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    sheet.clearAllButFirstRow(entry.getKey());
                }
            }

            Set<String> tabsUpdated = new ObjectOpenHashSet<>();
            List<Map.Entry<String, Long>> slowPlaceholders = new ObjectArrayList<>();
            List<Map.Entry<String, Future<TabComputationResult>>> tabTasks = new ArrayList<>();

            for (Map.Entry<String, Map.Entry<SelectionAlias, SheetTemplate>> entry : customTabs.entrySet()) {
                String tabName = entry.getKey();
                Map.Entry<SelectionAlias, SheetTemplate> value = entry.getValue();
                SelectionAlias alias = value.getKey();
                SheetTemplate template = value.getValue();

                Class type = alias.getType();
                if (!template.getType().equals(alias.getType())) {
                    messageList.add("[Tab: `" + tabName + "`] Incompatible types for `select:" + alias.getName() + "` and `template:" + template.getName() + "` | " +
                            "`" + alias.getType().getSimpleName() + "` != " + "`" + template.getType().getSimpleName() + "`");
                    continue;
                }

                Placeholders<Object, Object> ph = Locutus.cmd().getV2().getPlaceholders().get(type);
                if (ph == null) {
                    messageList.add("[Tab: `" + tabName + "`] Invalid type: `" + type.getSimpleName() + "`");
                    continue;
                }

                Future<TabComputationResult> future = executor.submit(() -> computeTab(tabName, alias, template, ph, store));
                tabTasks.add(new AbstractMap.SimpleEntry<>(tabName, future));
            }

            for (Map.Entry<String, Future<TabComputationResult>> task : tabTasks) {
                String tabName = task.getKey();
                try {
                    TabComputationResult result = task.getValue().get();
                    if (exportColumns != null) {
                        exportColumns.put(tabName, result.columns);
                    }
                    messageList.addAll(result.messages);
                    mergeErrorGroups(errorGroups, result.errorGroups);
                    slowPlaceholders.addAll(result.slowPlaceholders);

                    for (List<Object> row : result.rows) {
                        sheet.addRow(tabName, row);
                    }
                    sheet.updateWrite(tabName);
                    errorGroups.computeIfAbsent("Tabs: {value}, updated successfully.", f -> new ObjectArrayList<>()).add(tabName);
                    tabsUpdated.add(tabName.toLowerCase(Locale.ROOT));
                } catch (Exception e) {
                    e.printStackTrace();
                    messageList.add("[Tab: `" + tabName + "`] " + StringMan.stripApiKey(getRootMessage(e)));
                }
            }

            for (Map.Entry<String, Boolean> entry : tabsCreated.entrySet()) {
                if (tabsUpdated.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                errorGroups.computeIfAbsent("Tabs: {value}, exists in the google sheet, but has no template.", f -> new ObjectArrayList<>()).add(entry.getKey());
            }
            if (tabsUpdated.size() > 1) {
                messageList.add("To update only a single tab: " + CM.sheet_custom.auto_tab.cmd.toSlashMention());
            }
            if (!slowPlaceholders.isEmpty()) {
                slowPlaceholders.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                StringBuilder slowPlaceholdersMessage = new StringBuilder("Timing information:\n");
                for (Map.Entry<String, Long> entry : slowPlaceholders) {
                    slowPlaceholdersMessage.append(" - `").append(entry.getKey()).append("`: ").append(entry.getValue()).append("ms\n");
                }
                messageList.add(slowPlaceholdersMessage.toString());
            }

            sheet.flush();
            return toErrorList.get();
        } finally {
            sheet.reset();
        }
    }

    private TabComputationResult computeTab(String tabName,
                                            SelectionAlias alias,
                                            SheetTemplate template,
                                            Placeholders<Object, Object> ph,
                                            ValueStore store) {
        List<String> messages = new ObjectArrayList<>();
        Map<String, List<String>> errorGroups = new Object2ObjectLinkedOpenHashMap<>();
        List<Map.Entry<String, Long>> slowPlaceholders = new ObjectArrayList<>();

        Object modifier = alias.getModifier() == null ? null : ph.parseModifierLegacy(store, alias.getModifier());
        Set<Object> selection = ph.deserializeSelection(store, alias.getSelection(), modifier);
        List<String> columns = new ArrayList<>(template.getColumns());
        List<List<Object>> rows = new ArrayList<>();

        List<Object> header = new ArrayList<>(columns);
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i) instanceof String str && str.startsWith("=")) {
                header.set(i, "'" + str);
            }
        }
        rows.add(new ArrayList<>(header));

        PlaceholderCache<?> cache = new PlaceholderCache<>(selection);
        LocalValueStore tabStore = new LocalValueStore(store);
        tabStore.addProvider(Key.nested(PlaceholderCache.class, ph.getType()), cache);

        List<Function<Object, String>> functions = new ArrayList<>();
        for (String column : columns) {
            try {
                Function<Object, String> function = ph.getFormatFunction(tabStore, column, true);
                functions.add(function);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                messages.add("[Tab: `" + tabName + "`,Column:`" + column + "`] " + StringMan.stripApiKey(e.getMessage()));
                functions.add(null);
            }
        }

        long[] timePerColumn = new long[columns.size()];
        int errorCount = 0;
        for (Object element : selection) {
            List<Object> row = new ArrayList<>(columns);
            for (int i = 0; i < columns.size(); i++) {
                Function<Object, String> function = functions.get(i);
                if (function == null) {
                    row.set(i, "");
                    continue;
                }

                long start = System.currentTimeMillis();
                try {
                    String value = function.apply(element);
                    long diff = System.currentTimeMillis() - start;
                    timePerColumn[i] += diff;
                    row.set(i, value == null ? "" : value);
                } catch (Throwable e) {
                    long diff = System.currentTimeMillis() - start;
                    timePerColumn[i] += diff;

                    Throwable root = e;
                    row.set(i, "");
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    errorCount++;
                    if (errorCount == 25) {
                        String msgWithPlaceholder = "Tabs: {value}; contained too many errors, skipping the rest.";
                        errorGroups.computeIfAbsent(msgWithPlaceholder, f -> new ObjectArrayList<>()).add(tabName);
                    } else if (errorCount < 25) {
                        root.printStackTrace();
                        String column = columns.get(i);
                        String elemStr = ph.getName(element);
                        messages.add("[Tab: `" + tabName + "`,Column:`" + column + "`,Elem:`" + elemStr + "`] " + StringMan.stripApiKey(root.getMessage()));
                    }
                }
            }
            rows.add(row);
        }

        if (selection.isEmpty()) {
            messages.add("[Tab: `" + tabName + "`] No elements found for selection: `" + alias.getSelection() + "`");
        }
        for (int i = 0; i < timePerColumn.length; i++) {
            if (timePerColumn[i] > 200) {
                slowPlaceholders.add(new AbstractMap.SimpleEntry<>(columns.get(i), timePerColumn[i]));
            }
        }

        return new TabComputationResult(columns, rows, messages, errorGroups, slowPlaceholders);
    }

    private void mergeErrorGroups(Map<String, List<String>> target, Map<String, List<String>> source) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            target.computeIfAbsent(entry.getKey(), f -> new ObjectArrayList<>()).addAll(entry.getValue());
        }
    }

    private String getRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getMessage() : current.getMessage();
    }

    private static final class TabComputationResult {
        private final List<String> columns;
        private final List<List<Object>> rows;
        private final List<String> messages;
        private final Map<String, List<String>> errorGroups;
        private final List<Map.Entry<String, Long>> slowPlaceholders;

        private TabComputationResult(List<String> columns,
                                     List<List<Object>> rows,
                                     List<String> messages,
                                     Map<String, List<String>> errorGroups,
                                     List<Map.Entry<String, Long>> slowPlaceholders) {
            this.columns = columns;
            this.rows = rows;
            this.messages = messages;
            this.errorGroups = errorGroups;
            this.slowPlaceholders = slowPlaceholders;
        }
    }
}
