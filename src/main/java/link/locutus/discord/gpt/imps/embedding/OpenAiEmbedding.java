package link.locutus.discord.gpt.imps.embedding;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class OpenAiEmbedding implements IEmbedding {
    private final EncodingRegistry registry;
    private final Encoding embeddingEncoder;
    private final OpenAIClient service;
    private final ModelType model;

    public OpenAiEmbedding(EncodingRegistry registry, OpenAIClient service, ModelType modelType) throws ClassNotFoundException {
        this.registry = registry;
        this.service = service;
        this.embeddingEncoder = registry.getEncodingForModel(modelType);
        this.model = modelType;
    }

    @Override
    public String getTableName() {
        return this.model.getName();
    }

    public int getEmbeddingTokenSize(String text) {
        return embeddingEncoder.encode(text).size();
    }

    @Override
    public float[] fetchEmbedding(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(model.name().toLowerCase(Locale.ROOT))
                .input(text)
                .build();
        List<Embedding> data = service.embeddings().create(params).data();
        if (data.size() != 1) {
            throw new RuntimeException("Expected 1 embedding, got " + data.size());
        }
        List<Float> result = data.get(0).embedding();
        float[] target = new float[result.size()];
        for (int i = 0; i < target.length; i++) {
            target[i] = result.get(i);
        }
        return target;
    }

    @Override
    public void init() {

    }
}
