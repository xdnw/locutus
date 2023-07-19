package link.locutus.discord.gpt;

import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.db.AEmbeddingDatabase;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.EmbeddingInfo;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public interface IEmbeddingDatabase {
    long getHash(String text);
    float[] getEmbedding(String text);
    float[] getEmbedding(long hash);
    float[] fetchEmbedding(String text);
    float[] getOrCreateEmbedding(long embeddingHash, String embeddingText, Supplier<String> fullContent, EmbeddingSource source, boolean save, ThrowingConsumer<String> moderate);
    void registerHashes(EmbeddingSource source, Set<Long> hashes, boolean deleteAbsent);
    EmbeddingSource getSource(String name, long guild_id);
    EmbeddingSource getOrCreateSource(String name, long guild_id);
    Map<Long, Set<EmbeddingSource>> getEmbeddingSources();
    Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate);
    Map<Long, String> getContent(Set<Long> hashes);
    public String getText(long hash);
    public String getExpandedText(int source_id, long embedding_hash);
    void iterateVectors(Set<EmbeddingSource> allowedSources, TriConsumer<EmbeddingSource, Long, float[]> source_hash_vector_consumer);
    float[] getEmbedding(EmbeddingSource source, String text, ThrowingConsumer<String> checkModeration);
    List<EmbeddingInfo> getClosest(EmbeddingSource inputSource, String input, int top, Set<EmbeddingSource> allowedTypes, BiPredicate<EmbeddingSource, Long> sourceHashPredicate, ThrowingConsumer<String> moderate);
    int countVectors(EmbeddingSource existing);
    void deleteSource(EmbeddingSource source);
}
