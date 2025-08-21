package link.locutus.discord.gpt.pw;

import link.locutus.discord.gpt.ISourceManager;
import link.locutus.discord.gpt.imps.embedding.IEmbeddingAdapter;

public class DatabaseAdapter implements IEmbeddingAdapter<Long> {
    private final ISourceManager database;

    public DatabaseAdapter(ISourceManager database) {
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
    public Long getObject(long hash) {
        return null;
    }
}
