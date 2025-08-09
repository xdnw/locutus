package link.locutus.discord.gpt.pw;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.embedding.EmbeddingAdapter;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;

import java.util.Set;

public abstract class PWAdapter<T> extends EmbeddingAdapter<T> {
    public PWAdapter(EmbeddingSource source, Set<T> objects) {
        super(source, objects);
    }

    public abstract EmbeddingType getType();

//    public abstract String getFindCommandText(T obj);
//
//    public abstract String getUsageText(T obj);

    public abstract boolean hasPermission(T obj, ValueStore store, CommandManager2 manager);
}
