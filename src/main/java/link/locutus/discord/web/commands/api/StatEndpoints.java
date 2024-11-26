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
import net.dv8tion.jda.api.entities.User;

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
        Map<Integer, List<String>> errors = new LinkedHashMap<>();
        int maxPerCol = 3;

        PlaceholdersMap map = Locutus.cmd().getV2().getPlaceholders();
        Placeholders<T> ph = map.get(typeCasted);

        String modifier = null;

        Set<T> selection = ph.parseSet(store, selection_str, modifier);
        PlaceholderCache<T> cache = new PlaceholderCache<>(selection);

        List<TypedFunction<T, ?>> formatters = new ObjectArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            try {
                formatters.add(ph.getFormatFunction(store, column, cache, true));
            } catch (Exception e) {
                errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol)).add("Failed to get column: " + e.getMessage());
                formatters.add(null);
            }
        }

        List<List<Object>> data = new ObjectArrayList<>(selection.size());
        data.add(new ObjectArrayList<>(columns));
        for (T obj : selection) {
            List<Object> row = new ObjectArrayList<>(columns.size());
            for (int i = 0; i < formatters.size(); i++) {
                TypedFunction<T, ?> formatter = formatters.get(i);
                if (formatter == null) {
                    row.add(null);
                } else {
                    try {
                        Object td = formatter.apply(obj);
                        row.add(StringMan.toSerializable(td));
                    } catch (Exception e) {
                        errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol)).add("Failed to format column: " + e.getMessage());
                        row.add(null);
                    }
                }
            }
            data.add(row);
        }
        List<String> errorsArr = errors.isEmpty() ? null : errors.values().stream().collect(ObjectArrayList::new, List::addAll, List::addAll);

        return new WebTable(data, errorsArr, null);
    }
}
