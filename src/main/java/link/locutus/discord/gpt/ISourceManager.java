package link.locutus.discord.gpt;

import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.VectorRow;
import link.locutus.discord.gpt.imps.embedding.EmbeddingInfo;
import link.locutus.discord.gpt.imps.embedding.IEmbedding;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface ISourceManager {
    long getHash(String text);
    float[] getEmbedding(String text);
    float[] getEmbedding(long hash);
    float[] getOrCreateEmbedding(IEmbedding provider, long embeddingHash, String embeddingText, EmbeddingSource source, boolean save, ThrowingConsumer<String> moderate);
    void createEmbeddingIfNotExist(IEmbedding provider, long embeddingHash, String embeddingText, EmbeddingSource source, ThrowingConsumer<String> moderate);


    EmbeddingSource getSource(String name, long guild_id);
    EmbeddingSource getOrCreateSource(String name, long guild_id);
    void updateSources(List<EmbeddingSource> sources);
    Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate);
    Map<Long, String> getContent(Set<Long> hashes);
    public String getText(long hash);
    void iterateVectors(Set<EmbeddingSource> allowedSources, Consumer<VectorRow> source_hash_vector_consumer);
    float[] getEmbedding(EmbeddingSource source, String text, ThrowingConsumer<String> checkModeration);
    List<EmbeddingInfo>  getClosest(EmbeddingSource inputSource, String input, int top, Set<EmbeddingSource> allowedTypes, BiPredicate<EmbeddingSource, Long> sourceHashPredicate, ThrowingConsumer<String> moderate);
    int countVectors(int source_id);
    void deleteSource(EmbeddingSource source);
    public List<ConvertingDocument> getUnconvertedDocuments();
    public ConvertingDocument getConvertingDocument(int source_id);
    public void addDocument(List<ConvertingDocument> documents);

    public EmbeddingSource getEmbeddingSource(int source_id);

    default void setDocumentError(ConvertingDocument document, String error) {
        document.error = error;
        addDocument(List.of(document));
    }

    default void setDocumentErrorIfAbsent(ConvertingDocument document, String error) {
        if (document.error == null) {
            document.error = error;
        }
        addDocument(List.of(document));
    }
    void deleteDocument(int sourceId);

    void deleteMissing(EmbeddingSource source, Set<Long> hashesSet);

    public int getUsage(String model);
    public void addUsage(String model, int usage);
}
