package com.boydti.discord.web.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class TableBuilder<T> {
    private final List<String> columns;
    private final Map<String, Function<T, Object>> valueFunctions;
    private final Map<String, Boolean> visibleColumns;
    private final Map<String, Boolean> searchableColumns;

    public TableBuilder() {
        this.columns = new ArrayList<>();
        this.valueFunctions = new HashMap<>();
        this.visibleColumns = new HashMap<>();
        this.searchableColumns = new HashMap<>();
    }

    public TableBuilder(TableBuilder<T> other) {
        this.columns = new ArrayList<>(other.columns);
        this.valueFunctions = new HashMap<>(other.valueFunctions);
        this.visibleColumns = new HashMap<>(other.visibleColumns);
        this.searchableColumns = new HashMap<>(other.searchableColumns);
    }

    public TableBuilder addColumn(String name, boolean visible, boolean searchable, Function<T, Object> value) {
        name = StringEscapeUtils.escapeHtml4(name);
        if (columns.contains(name)) throw new IllegalArgumentException("Invalid duplicate column: " + name);
        columns.add(name);
        valueFunctions.put(name, value);
        this.visibleColumns.put(name, visible);
        this.searchableColumns.put(name, searchable);
        return this;
    }

    public String buildJsonEncodedString(List<T> entries) {
        return StringEscapeUtils.escapeHtml4(buildJson(entries).toString());
    }

    public JsonObject buildJson(List<T> entries) {
        JsonObject root = new JsonObject();

        JsonArray data = new JsonArray();

        root.add("data", data);
        for (T entry : entries) {
            JsonObject row = new JsonObject();
            for (String column : columns) {
                Object value = valueFunctions.get(column).apply(entry);
                String valueStr = value == null ? "" : value.toString();
                row.addProperty(column, valueStr);
            }
            data.add(row);
        }

        JsonArray searchableJson = new JsonArray();
        for (Map.Entry<String, Boolean> entry : searchableColumns.entrySet()) {
            if (entry.getValue()) searchableJson.add(entry.getKey());
        }
        root.add("searchable", searchableJson);

        JsonArray visibleJson = new JsonArray();
        for (Map.Entry<String, Boolean> entry : visibleColumns.entrySet()) {
            if (entry.getValue()) visibleJson.add(entry.getKey());
        }
        root.add("visible", visibleJson);

        return root;
    }

    public String buildHtml(String title, List<T> entries) {
        return views.data.table_data.template(title, buildJsonEncodedString(entries)).render().toString();
    }

    public TableBuilder<T> clone() {
        return new TableBuilder<T>(this);
    }
}
