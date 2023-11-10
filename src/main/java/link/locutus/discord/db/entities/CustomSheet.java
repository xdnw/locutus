package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomSheet<T> {
    public String name;
    public Class<T> type;
    public List<String> columns;

    public CustomSheet(ResultSet rs) throws SQLException {
        this.name = rs.getString("name");
        String typeStr = rs.getString("type");
        for (Class<?> type : Locutus.cmd().getV2().getPlaceholders().getTypes()) {
            if (type.getSimpleName().equalsIgnoreCase(typeStr)) {
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

    public CustomSheet(String name, Class<T> type, String filter, List<String> columns) {
        this.name = name;
        this.type = type;
        this.columns = columns;
    }

    @Override
    public String toString() {
        StringBuilder response = new StringBuilder();
        response.append('`').append(name).append("`").append(": `").append(type.getSimpleName()).append("`\n");
        for (int i = 0; i < columns.size(); i++) {
            response.append(i + 1).append(". `").append(columns.get(i)).append("`\n");
        }
        return response.toString();
    }
}
