package link.locutus.discord.gpt.imps.embedding;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.models.embeddings.EmbeddingModel;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class OpenAiEmbedding implements IEmbedding {
    private final EncodingRegistry registry;
    private final Encoding embeddingEncoder;
    private final OpenAIClient service;
    private final EmbeddingModel model;
    private final ModelType modelType;

    public OpenAiEmbedding(EncodingRegistry registry, OpenAIClient service, EmbeddingModel model) {
        this.registry = registry;
        this.service = service;
        this.embeddingEncoder = registry.getEncoding(EncodingType.CL100K_BASE);
        this.model = model;
        this.modelType = ModelType.fromName(model.asString().toLowerCase(Locale.ROOT).replace('_', '-')).orElse(null);
    }

    @Override
    public String getTableName() {
        return this.model.asString().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
    }

    @Override
    public int getSize(String text) {
        return embeddingEncoder.encode(text).size();
    }

    @Override
    public int getSizeCap() {
        if (modelType == null) {
            System.err.println("Model type is null for model: " + model.asString());
            return ModelType.TEXT_EMBEDDING_3_LARGE.getMaxContextLength();
        }
        return modelType.getMaxContextLength();
    }

    @Override
    public float[] fetchEmbedding(String text) {
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(model)
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

    @Override
    public void close() throws IOException {

    }
}
