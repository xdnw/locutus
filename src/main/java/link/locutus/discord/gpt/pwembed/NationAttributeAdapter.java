package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.util.StringMan;

import java.util.Set;

public class NationAttributeAdapter extends PWAdapter<NationAttribute> {
    public NationAttributeAdapter(EmbeddingSource source, Set<NationAttribute> objects) {
        super(source, objects);
    }

    @Override
    public EmbeddingType getType() {
        return EmbeddingType.Nation_Statistic;
    }

    @Override
    public boolean hasPermission(NationAttribute obj, ValueStore store, CommandManager2 manager) {
        return true;
    }

    @Override
    public String getDescription(NationAttribute obj) {
        return StringMan.classNameToSimple(obj.getType().toString()) + " " + obj.getDesc().replaceAll("\n", " ");
    }

    @Override
    public String getExpanded(NationAttribute obj) {
        return null;
    }
}
