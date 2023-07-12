package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;

public abstract class PWEmbedding<T> {
    private final EmbeddingType type;
    private final String id;
    private final T obj;
    private final boolean shouldSave;

    public PWEmbedding(EmbeddingType type, String id, T obj, boolean shouldSave) {
        this.type = type;
        this.id = id;
        this.obj = obj;
        this.shouldSave = shouldSave;
    }

    public EmbeddingType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public T getObj() {
        return obj;
    }

    public abstract String apply(String query, GptHandler handler);

    public abstract String getContent();

    public boolean shouldSaveConent() {
        return shouldSave;
    }

    public abstract boolean hasPermission(ValueStore store, CommandManager2 manager);
}