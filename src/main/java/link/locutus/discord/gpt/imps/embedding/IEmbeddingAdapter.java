package link.locutus.discord.gpt.imps.embedding;

import link.locutus.discord.db.entities.EmbeddingSource;

public interface IEmbeddingAdapter<T> {
    public IEmbeddingAdapter<T> setModerate(boolean moderate);
    default String getDescription(EmbeddingSource source, T obj) {
        return getDescription(obj);
    }

    public long getHash(T obj);
    public String getDescription(T obj);
    public T getObject(long hash);
}
