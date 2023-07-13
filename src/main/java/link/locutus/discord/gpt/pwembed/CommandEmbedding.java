package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;

public class CommandEmbedding extends PWEmbedding<ParametricCallable> {

    public CommandEmbedding(ParametricCallable obj) {
        super(EmbeddingType.Command, obj.getFullPath(), obj, false);
    }

    @Override
    public String apply(String query, GptHandler handler) {
        return null;
    }

    @Override
    public String getSummary() {
        return getObj().simpleDesc();
    }

    @Override
    public String getFull() {
        ValueStore<Object> store = Locutus.imp().getCommandManager().getV2().getStore();
        return getObj().toBasicMarkdown(store, null, "/", false, false);//simpleDesc();
    }

    @Override
    public boolean hasPermission(ValueStore store, CommandManager2 manager) {
        return getObj().hasPermission(store, manager.getPermisser());
    }
}
