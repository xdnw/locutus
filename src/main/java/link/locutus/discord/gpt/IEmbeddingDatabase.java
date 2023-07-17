package link.locutus.discord.gpt;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.db.AEmbeddingDatabase;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.TriConsumer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface IEmbeddingDatabase {
    float[] getEmbedding(String text);
    float[] fetchEmbedding(String text);
    float[] getOrCreateEmbedding(String content, EmbeddingSource source);
    void registerHashes(int source, Set<Long> hashes, boolean deleteAbsent);
    EmbeddingSource getOrCreateSource(String name, long guild_id);
    Map<Long, Set<EmbeddingSource>> getEmbeddingSources();
    Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate);
    Map<Long, String> getContent(Set<Long> hashes);

    void iterateVectors(Set<EmbeddingSource> allowedSources, TriConsumer<Integer, Long, float[]> source_hash_vector_consumer);
}
