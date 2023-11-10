package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class CustomSelection<T> {
    private String name;
    private Class<T> type;
    private String selection;

    public CustomSelection(String name, Class<T> type, String selection) {
        this.name = name;
        this.selection = selection;
        this.type = (Class<T>) type;
    }

    public Class<T> getType() {
        return type;
    }

    public String getSelection() {
        return selection;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "`" + type.getSimpleName() + "`: `" + name + "` | `" + selection + "`";
    }
}
