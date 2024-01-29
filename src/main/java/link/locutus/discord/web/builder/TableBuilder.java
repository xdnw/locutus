package link.locutus.discord.web.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.jte.generated.precompiled.data.Jtetable_dataGenerated;
import gg.jte.generated.precompiled.data.Jtetable_fullGenerated;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import org.apache.commons.lang3.StringEscapeUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TableBuilder<T> {
    private final List<String> columns;
    private final List<Function<T, Object>> valueFunctions;
    private final Set<Integer> visibleColumns;
    private final Set<Integer> searchableColumns;
    private final Map<String, Set<Integer>> cellFormatFunction;
    private String rowFormatFunction;
    private final WebStore ws;
    private int sortColumn = -1;
    private boolean descending;

    public TableBuilder(WebStore ws) {
        this.ws = ws;
        this.columns = new ArrayList<>();
        this.valueFunctions = new ArrayList<>();
        this.visibleColumns = new LinkedHashSet<>();
        this.searchableColumns = new LinkedHashSet<>();
        this.cellFormatFunction = new HashMap<>();
    }

    public TableBuilder(TableBuilder<T> other) {
        this.ws = other.ws;
        this.columns = new ArrayList<>(other.columns);
        this.valueFunctions = new ArrayList<>(other.valueFunctions);
        this.visibleColumns = new LinkedHashSet<>(other.visibleColumns);
        this.searchableColumns = new LinkedHashSet<>(other.searchableColumns);
        this.cellFormatFunction = new HashMap<>(other.cellFormatFunction);
        this.rowFormatFunction = other.rowFormatFunction;
        this.sortColumn = other.sortColumn;
        this.descending = other.descending;
    }

    public TableBuilder<T> sort(int column, boolean descending) {
        this.sortColumn = column;
        this.descending = descending;
        return this;
    }

    public int getIndex(String column) {
        return column.indexOf(column);
    }

    public TableBuilder addColumn(String name, boolean visible, boolean searchable, Function<T, Object> value) {
        name = StringEscapeUtils.escapeHtml4(name);
        if (columns.contains(name)) throw new IllegalArgumentException("Invalid duplicate column: " + name);
        int index = columns.size();
        columns.add(name);
        valueFunctions.add(value);
        if (visible) this.visibleColumns.add(index);
        if (searchable) this.searchableColumns.add(index);
        return this;
    }

    public String buildJsonEncodedString(List<T> entries) {
        return Base64.getEncoder().encodeToString(buildJson(entries).toString().getBytes(StandardCharsets.UTF_8));
    }

    public JsonObject buildJson(List<T> entries) {
        JsonObject root = new JsonObject();

        JsonArray columnNames = new JsonArray();
        for (String name : columns) columnNames.add(name);
        root.add("columns", columnNames);

        JsonArray data = new JsonArray();
        for (T entry : entries) {
            JsonArray row = new JsonArray();
            for (Function<T, Object> valueFunction : valueFunctions) {
                Object value = valueFunction.apply(entry);
                if (value instanceof JsonElement) {
                    row.add((JsonElement) value);
                } else {
                    String valueStr = value == null ? "" : value.toString();
                    row.add(valueStr);
                }
            }
            data.add(row);
        }
        root.add("data", data);

        JsonArray searchableJson = new JsonArray();
        searchableColumns.forEach(searchableJson::add);
        root.add("searchable", searchableJson);

        JsonArray visibleJson = new JsonArray();
        visibleColumns.forEach(visibleJson::add);
        root.add("visible", visibleJson);

        if (!cellFormatFunction.isEmpty()) {
            JsonObject cellFormatJson = new JsonObject();
            for (Map.Entry<String, Set<Integer>> cellFormat : cellFormatFunction.entrySet()) {
                JsonArray cellFormatArray = new JsonArray();
                for (Integer colId : cellFormat.getValue()) {
                    cellFormatArray.add(colId);
                }
                cellFormatJson.add(cellFormat.getKey(), cellFormatArray);
            }
            root.add("cell_format", cellFormatJson);
        }

        if (rowFormatFunction != null) {
            root.addProperty("row_format", rowFormatFunction);
        }

        if (sortColumn != -1) {
            JsonArray sortJson = new JsonArray();
            sortJson.add(sortColumn);
            sortJson.add(descending ? "desc" : "asc");
            root.add("sort", sortJson);
        }

        return root;
    }

    public String buildTableHtml(String title, List<T> entries) {
        return WebStore.render(f -> Jtetable_dataGenerated.render(f, null, ws, title, buildJsonEncodedString(entries)));
    }
    public String buildPageHtml(String title, List<T> entries) {
        return WebStore.render(f -> Jtetable_fullGenerated.render(f, null, ws, title, buildJsonEncodedString(entries)));
    }

    public TableBuilder<T> clone() {
        return new TableBuilder<T>(this);
    }

    public TableBuilder<T> setRenderer(int columnIndex, String functionName) {
        cellFormatFunction.computeIfAbsent(functionName, k -> new LinkedHashSet<>()).add(columnIndex);
        return this;
    }
}
