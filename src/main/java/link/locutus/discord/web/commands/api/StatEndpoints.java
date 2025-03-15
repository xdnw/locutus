package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.commands.manager.v2.table.imp.*;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StatEndpoints {
    // EntityTable custom
    // EntityGroup
    // TaxCategoryGraph


    // TODO validate permissions
    @Command
    @ReturnType(WebTable.class)
    public <T> WebTable table(ValueStore store, @Me @Default User user, @PlaceholderType Class type,
                              String selection_str,
                              @TextArea List<String> columns) {
        Class<T> typeCasted = (Class<T>) type;
        Map<Integer, List<WebTableError>> errors = new LinkedHashMap<>();
        int maxPerCol = 3;

        PlaceholdersMap map = Locutus.cmd().getV2().getPlaceholders();
        Placeholders<T, Object> ph = map.get(typeCasted);
        Object modifier = null;
        if (selection_str.startsWith("{") && selection_str.endsWith("}")) {
            JSONObject json = new JSONObject(selection_str);
            Map<String, Object> args = json.toMap();
            Map.Entry<String, ?> entry = ph.parseModifier(store, args);
            if (entry != null && entry.getValue() != null) {
                selection_str = entry.getKey();
                modifier = entry.getValue();
            }
        }
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
                        case "Map" -> "numeric_map";
                        case "WebGraph" -> "graph";
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
                e.printStackTrace();
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
                                if (renderers.get(i) == null) {
                                    if (serialized instanceof Map sMap) {
                                        if (sMap.containsKey("title") && sMap.containsKey("x") && sMap.containsKey("y") && sMap.containsKey("labels") && sMap.containsKey("data")) {
                                            renderers.set(i, "graph");
                                        } else {
                                            renderers.set(i, "json");
                                        }
                                    } else if (serialized instanceof List list) {
                                        renderers.set(i, "json");
                                    }
                                }
                            }
                            row.add(serialized);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
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
