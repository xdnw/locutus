package link.locutus.discord.db.entities;

import static com.google.common.base.Preconditions.checkNotNull;

public class SelectionAlias<T> {
    private String name;
    private Class<T> type;
    private String selection;

    public SelectionAlias(String name, Class<T> type, String selection) {
        checkNotNull(name, "name");
        checkNotNull(type, "type");
        checkNotNull(selection, "selection");
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
