package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import link.locutus.discord.db.AEmbeddingDatabase;
import link.locutus.discord.gpt.pw.GptDatabase;

import java.sql.SQLException;
import java.util.List;

public class AdaEmbedding extends AEmbeddingDatabase {
    private final EncodingRegistry registry;
    private final Encoding embeddingEncoder;
    private final OpenAIClient service;

    public AdaEmbedding(EncodingRegistry registry, OpenAIClient service, GptDatabase database) throws SQLException, ClassNotFoundException {
        super("ada", database);
        this.registry = registry;
        this.service = service;
        this.embeddingEncoder = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
    }

    public int getEmbeddingTokenSize(String text) {
        return embeddingEncoder.encode(text).size();
    }

    @Override
    public float[] fetchEmbedding(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model("text-embedding-ada-002")
                .input(text)
                .build();
        List<Embedding> data = service.embeddings().create(params).data();
        if (data.size() != 1) {
            throw new RuntimeException("Expected 1 embedding, got " + data.size());
        }
        List<Double> result = data.get(0).embedding();
        float[] target = new float[result.size()];
        for (int i = 0; i < target.length; i++) {
            target[i] = result.get(i).floatValue();
        }
        return target;
    }

}
