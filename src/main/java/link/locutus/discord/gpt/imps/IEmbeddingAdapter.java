package link.locutus.discord.gpt.imps;

import link.locutus.discord.db.entities.EmbeddingSource;

import link.locutus.discord.util.scheduler.KeyValue;
import java.util.Map;

public interface IEmbeddingAdapter<T> {
    public IEmbeddingAdapter<T> setModerate(boolean moderate);
    default Map.Entry<String, String> getDescriptionAndExpandedPair(EmbeddingSource source, T obj) {
        return new KeyValue<>(getDescription(obj), getExpanded(source, obj));
    }

    public long getHash(T obj);
    public String getDescription(T obj);
    public String getExpanded(EmbeddingSource source, T obj);
    public T getObject(long hash);
}
