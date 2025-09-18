package link.locutus.discord.gpt.pw;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.entities.EmbeddingSource;

import java.util.Set;

public class PlaceholderAdapter extends PWAdapter<ParametricCallable> {
    private final Class<?> type;

    public PlaceholderAdapter(EmbeddingSource source, Class<?> type, Set<ParametricCallable> objects) {
        super(source, objects);
        this.type = type;
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean hasPermission(ParametricCallable obj, ValueStore store, CommandManager2 manager) {
        return true;
    }

    @Override
    public String getDescription(ParametricCallable obj) {
        return PlaceholdersMap.getClassName(getType()) + ": " + obj.getFullPath() + " - " + obj.simpleDesc();
    }
}
