package link.locutus.discord.gpt.pw;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.embedding.EmbeddingType;

import java.util.Set;

public class NationAttributeAdapter extends PWAdapter<ParametricCallable> {
    public NationAttributeAdapter(EmbeddingSource source, Set<ParametricCallable> objects) {
        super(source, objects);
    }

    @Override
    public EmbeddingType getType() {
        return EmbeddingType.Nation_Statistic;
    }

    @Override
    public boolean hasPermission(ParametricCallable obj, ValueStore store, CommandManager2 manager) {
        return true;
    }

    @Override
    public String getDescription(ParametricCallable obj) {
        return getType() + ": " + obj.getFullPath() + " - " + obj.simpleDesc();
    }
}
