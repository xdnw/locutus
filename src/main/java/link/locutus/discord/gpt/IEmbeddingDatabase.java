package link.locutus.discord.gpt;

import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.ConvertingDocument;
import link.locutus.discord.gpt.imps.DocumentChunk;
import link.locutus.discord.gpt.imps.embedding.EmbeddingInfo;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.TriConsumer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public interface IEmbeddingDatabase {
    long getHash(String text);
    float[] getEmbedding(String text);
    float[] getEmbedding(long hash);
    float[] fetchEmbedding(String text);
    float[] getOrCreateEmbedding(long embeddingHash, String embeddingText, EmbeddingSource source, boolean save, ThrowingConsumer<String> moderate);

    void registerHashes(EmbeddingSource source, Set<Long> hashes, boolean deleteAbsent);
    EmbeddingSource getSource(String name, long guild_id);
    EmbeddingSource getOrCreateSource(String name, long guild_id);
    void updateSources(List<EmbeddingSource> sources);
    Set<EmbeddingSource> getSources(Predicate<Long> guildPredicateOrNull, Predicate<EmbeddingSource> sourcePredicate);
    Map<Long, String> getContent(Set<Long> hashes);
    public String getText(long hash);
    public String getExpandedText(int source_id, long embedding_hash);
    void iterateVectors(Set<EmbeddingSource> allowedSources, TriConsumer<EmbeddingSource, Long, float[]> source_hash_vector_consumer);
    float[] getEmbedding(EmbeddingSource source, String text, ThrowingConsumer<String> checkModeration);
    List<EmbeddingInfo> getClosest(EmbeddingSource inputSource, String input, int top, Set<EmbeddingSource> allowedTypes, BiPredicate<EmbeddingSource, Long> sourceHashPredicate, ThrowingConsumer<String> moderate);
    int countVectors(EmbeddingSource existing);
    void deleteSource(EmbeddingSource source);
    public List<ConvertingDocument> getUnconvertedDocuments();
    public ConvertingDocument getConvertingDocument(int source_id);
    public void addConvertingDocument(List<ConvertingDocument> documents);
    public void addChunks(List<DocumentChunk> chunks);
    public List<DocumentChunk> getChunks(int source_id);
    public EmbeddingSource getEmbeddingSource(int source_id);

    default void setDocumentError(ConvertingDocument document, String error) {
        document.error = error;
        addConvertingDocument(List.of(document));
    }

    default void setDocumentErrorIfAbsent(ConvertingDocument document, String error) {
        if (document.error == null) {
            document.error = error;
        }
        addConvertingDocument(List.of(document));
    }
    void deleteDocumentAndChunks(int sourceId);
}
