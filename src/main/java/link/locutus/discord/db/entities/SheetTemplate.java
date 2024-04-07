package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SheetTemplate<T> {
    public String name;
    public Class<T> type;
    public List<String> columns;

    public SheetTemplate(ResultSet rs) throws SQLException {
        this.name = rs.getString("name");
        String typeStr = PlaceholdersMap.getClassName(rs.getString("type"));
        for (Class<?> type : Locutus.cmd().getV2().getPlaceholders().getTypes()) {
            if (PlaceholdersMap.getClassName(type).equalsIgnoreCase(typeStr)) {
                this.type = (Class<T>) type;
                break;
            }
        }
        if (type == null) {
            throw new IllegalArgumentException("Invalid type: " + typeStr);
        }
        columns = new ArrayList<>(Arrays.asList(rs.getString("columns").split("\n")));
    }

    public String getName() {
        return name;
    }

    public Class getType() {
        return type;
    }

    public List<String> getColumns() {
        return columns;
    }

    public SheetTemplate(String name, Class<T> type, List<String> columns) {
        this.name = name;
        this.type = type;
        this.columns = columns;
    }

    public SheetTemplate<T> resolve(Class<T> type) {
        if (this.type == null) {
            this.type = type;
        } else if (this.type != type) {
            throw new IllegalArgumentException("Selection type does not match column template type: " + PlaceholdersMap.getClassName(this.type) + " != " + PlaceholdersMap.getClassName(type));
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder response = new StringBuilder();
        response.append('`').append(name).append("`").append(": `type:").append(type.getSimpleName()).append("`\n");
        for (int i = 0; i < columns.size(); i++) {
            response.append(i + 1).append(". `").append(columns.get(i)).append("`\n");
        }
        return response.toString();
    }
}
