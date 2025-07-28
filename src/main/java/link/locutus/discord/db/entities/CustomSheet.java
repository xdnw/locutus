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

    public List<String> update(ValueStore store) throws GeneralSecurityException, IOException {
        return update(getSheet(), store, tabs);
    }

    public List<String> update(SpreadSheet sheet, ValueStore store, Map<String, Map.Entry<SelectionAlias, SheetTemplate>> customTabs) throws GeneralSecurityException, IOException {
        synchronized (sheet) {
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

            List<Future<?>> writeTasks = new ArrayList<>();
            ExecutorService executor = Locutus.imp().getExecutor();
            sheet.reset();
            Map<String, Boolean> tabsCreated = new Object2BooleanLinkedOpenHashMap<>();
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
            Set<String> tabsUpdated = new ObjectOpenHashSet<>();
            List<Map.Entry<String, Long>> slowPlaceholders = new ObjectArrayList<>();

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

                Map<String, Integer> maxErrors = new Object2IntLinkedOpenHashMap<>();
                Future<?> future = executor.submit(() -> {
                    try {
                        Object modifier = alias.getModifier() == null ? null : ph.parseModifierLegacy(store, alias.getModifier());
                        Set<Object> selection = ph.deserializeSelection(store, alias.getSelection(), modifier);
                        List<String> columns = template.getColumns();
                        List<Object> header = new ArrayList<>(columns);
                        for (int i = 0; i < header.size(); i++) {
                            if (header.get(i) instanceof String str && str.startsWith("=")) {
                                header.set(i, "'" + str);
                            }
                        }

                        // add header
                        sheet.addRow(tabName, header);

                        // get write cache
                        PlaceholderCache<?> cache = new PlaceholderCache<>(selection);
                        LocalValueStore tabStore = new LocalValueStore<>(store);
                        tabStore.addProvider(Key.of(PlaceholderCache.class, ph.getType()), cache);

                        List<Function<Object, String>> functions = new ArrayList<>();
                        for (String column : columns) {
                            try {
                                Function<Object, String> function = ph.getFormatFunction(tabStore, column, true);
                                functions.add(function);
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                                messageList.add("[Tab: `" + tabName + "`,Column:`" + column + "`] " + StringMan.stripApiKey(e.getMessage()));
                                functions.add(null);
                            }
                        }
                        long[] timePerColumn = new long[columns.size()];
                        for (Object o : selection) {
                            for (int i = 0; i < columns.size(); i++) {
                                Function<Object, String> function = functions.get(i);
                                if (function == null) {
                                    header.set(i, "");
                                    continue;
                                }
                                long start = System.currentTimeMillis();
                                try {
                                    String value1 = function.apply(o);
                                    long diff = System.currentTimeMillis() - start;
                                    timePerColumn[i] += diff;
                                    header.set(i, value1 == null ? "" : value1);
                                } catch (Throwable e) {
                                    long diff = System.currentTimeMillis() - start;
                                    timePerColumn[i] += diff;

                                    Throwable t = e;
                                    header.set(i, "");
                                    while (t.getCause() != null && t.getCause() != t) {
                                        t = t.getCause();
                                    }
                                    int currentErrors = maxErrors.merge(tabName, 1, Integer::sum);
                                    if (currentErrors == 25) {
                                        String msgWithPlaceholder = "Tabs: {value}; contained too many errors, skipping the rest.";
                                        errorGroups.computeIfAbsent(msgWithPlaceholder, f -> new ObjectArrayList<>()).add(tabName);
                                    } else if (currentErrors < 25) {
                                        t.printStackTrace();
                                        String column = columns.get(i);
                                        String elemStr = ph.getName(o);
                                        messageList.add("[Tab: `" + tabName + "`,Column:`" + column + "`,Elem:`" + elemStr + "`] " + StringMan.stripApiKey(t.getMessage()));
                                    }
                                }
                            }
                            sheet.addRow(tabName, header);
                        }
                        if (selection.isEmpty()) {
                            messageList.add("[Tab: `" + tabName + "`] No elements found for selection: `" + alias.getSelection() + "`");
                        }
                        for (int i = 0; i < timePerColumn.length; i++) {
                            if (timePerColumn[i] > 200) {
                                slowPlaceholders.add(new AbstractMap.SimpleEntry<>(columns.get(i), timePerColumn[i]));
                            }
                        }
                        createTabsFuture.get();
                        sheet.updateWrite(tabName);
                        errorGroups.computeIfAbsent("Tabs: {value}, updated successfully.", f -> new ObjectArrayList<>()).add(tabName);
                        tabsUpdated.add(tabName.toLowerCase(Locale.ROOT));
                    } catch (Exception e) {
                        e.printStackTrace();
                        messageList.add("[Tab: `" + tabName + "`] " + StringMan.stripApiKey(e.getMessage()));
                    }
                });
                writeTasks.add(future);
            }
            for (Future<?> writeTask : writeTasks) {
                try {
                    writeTask.get();
                } catch (Exception e) {
                    messageList.add(StringMan.stripApiKey(e.getMessage()));
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

            sheet.reset();

            return toErrorList.get();
        }
    }
}
