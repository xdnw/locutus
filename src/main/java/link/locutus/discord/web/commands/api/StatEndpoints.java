package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.WebTable;
import link.locutus.discord.web.commands.binding.value_types.WebTableError;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatEndpoints {

    // TODO validate permissions
    @Command
    @ReturnType(WebTable.class)
    public <T> WebTable table(ValueStore store, @Me @Default User user, @PlaceholderType Class type, String selection_str, @TextArea List<String> columns) {
        System.out.println("Columns " + columns.size() + " | " + columns);
        Class<T> typeCasted = (Class<T>) type;
        Map<Integer, List<WebTableError>> errors = new LinkedHashMap<>();
        int maxPerCol = 3;

        PlaceholdersMap map = Locutus.cmd().getV2().getPlaceholders();
        Placeholders<T> ph = map.get(typeCasted);

        String modifier = null;

        Set<T> selection = ph.parseSet(store, selection_str, modifier);
        ValueStore<T> cacheStore = PlaceholderCache.createCache(selection, typeCasted);

        List<String> renderers = new ObjectArrayList<>(columns.size());
        List<TypedFunction<T, ?>> formatters = new ObjectArrayList<>(columns.size());
        boolean[] isEnum = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            try {
                TypedFunction<T, ?> result = ph.formatRecursively(cacheStore, column, null, 0, false, true);
                Type rsType = result.getType();
                formatters.add(result);
                if (rsType instanceof Class clazz) {
                    renderers.add(switch (clazz.getSimpleName()) {
                        case "double", "Double", "int", "Integer", "float", "Float" -> "comma";
                        case "NationColor" -> "color";
                        case "String" -> "normal";
                        default -> {
                            if (clazz.isEnum()) {
                                isEnum[i] = true;
                                yield "enum:" + clazz.getSimpleName();
                            } else {
                                yield null;
                            }
                        }
                    });
                } else {
                    renderers.add(null);
                }
            } catch (Exception e) {
                List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                if (errList.size() < maxPerCol) {
                    errList.add(new WebTableError(i, null, e.getMessage()));
                }
                formatters.add(null);
                renderers.add(null);
            }
        }

        boolean[] checkedIsJson = new boolean[columns.size()];
        List<List<Object>> data = new ObjectArrayList<>(selection.size());
        data.add(new ObjectArrayList<>(columns));
        int rowI = 0;
        for (T obj : selection) {
            List<Object> row = new ObjectArrayList<>(columns.size());
            for (int i = 0; i < formatters.size(); i++) {
                TypedFunction<T, ?> formatter = formatters.get(i);
                if (formatter == null) {
                    row.add(null);
                } else {
                    try {
                        Object td = formatter.apply(obj);
                        if (td != null && td.getClass().isEnum() && isEnum[i]) {
                            row.add(((Enum<?>) td).ordinal());
                        } else {
                            Object serialized = StringMan.toSerializable(td);
                            if (!checkedIsJson[i] && serialized != null) {
                                checkedIsJson[i] = true;
                                if (renderers.get(i) == null && serialized instanceof Map || serialized instanceof List) {
                                    renderers.set(i, "json");
                                }
                            }
                            row.add(serialized);
                        }
                    } catch (Exception e) {
                        List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                        if (errList.size() < maxPerCol) {
                            errList.add(new WebTableError(i, rowI, e.getMessage()));
                        }
                        row.add(null);
                    }
                }
            }
            data.add(row);
            rowI++;
        }
        List<WebTableError> errorsArr = errors.isEmpty() ? null : errors.values().stream().collect(ObjectArrayList::new, List::addAll, List::addAll);
        return new WebTable(data, errorsArr, renderers);
    }
}
