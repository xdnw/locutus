package link.locutus.discord.gpt.pw;

import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.IEmbeddingDatabase;
import link.locutus.discord.gpt.imps.IEmbeddingAdapter;

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
