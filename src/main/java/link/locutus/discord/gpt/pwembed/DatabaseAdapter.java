package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.imps.EmbeddingAdapter;
import link.locutus.discord.gpt.imps.EmbeddingType;
import link.locutus.discord.gpt.imps.IEmbeddingAdapter;

import java.util.Map;

public class DatabaseAdapter implements IEmbeddingAdapter<Long> {
    private final IEmbeddingDatabase database;

    public DatabaseAdapter(IEmbeddingDatabase database) {
        this.database = database;
    }

    @Override
    public IEmbeddingAdapter<Long> setModerate(boolean moderate) {
        // Do not moderate. Results already in database
        return this;
    }


    @Override
    public long getHash(Long obj) {
        return obj;
    }

    @Override
    public String getDescription(Long hash) {
        return database.getText(hash);
    }

    @Override
    public String getExpanded(EmbeddingSource source, Long hash) {
        return database.getExpandedText(source.source_id, hash);
    }

    @Override
    public Long getObject(long hash) {
        return null;
    }
}
