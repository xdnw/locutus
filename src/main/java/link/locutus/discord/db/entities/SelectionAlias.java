package link.locutus.discord.db.entities;

public class SelectionAlias<T> {
    private String name;
    private Class<T> type;
    private String selection;

    public SelectionAlias(String name, Class<T> type, String selection) {
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
