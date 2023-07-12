package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.util.StringMan;

public class NationMetricEmbedding extends PWEmbedding<NationAttribute> {
    public NationMetricEmbedding(NationAttribute placeholder) {
        super(EmbeddingType.Nation_Statistic, placeholder.getName(), placeholder, false);
    }

    @Override
    public String apply(String query, GptHandler handler) {
        return null;
    }

    @Override
    public String getContent() {
        return StringMan.classNameToSimple(getObj().getType().toString()) + " " + getObj().getDesc().replaceAll("\n", " ");
    }

    @Override
    public boolean hasPermission(ValueStore store, CommandManager2 manager) {
        // TODO
        return true;
    }
}
