package link.locutus.discord.gpt.imps;

import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.db.AEmbeddingDatabase;

import java.sql.SQLException;
import java.util.List;

public class AdaEmbedding extends AEmbeddingDatabase {
    private final EncodingRegistry registry;
    private final Encoding embeddingEncoder;
    private final OpenAiService service;

    public AdaEmbedding(EncodingRegistry registry, OpenAiService service) throws SQLException, ClassNotFoundException {
        super("gpt");
        this.registry = registry;
        this.service = service;
        this.embeddingEncoder = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
    }

    public int getEmbeddingTokenSize(String text) {
        return embeddingEncoder.encode(text).size();
    }

    @Override
    public float[] fetchEmbedding(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(List.of(text))
                .build();
        EmbeddingResult embedResult = service.createEmbeddings(request);
        List<Embedding> data = embedResult.getData();
        if (data.size() != 1) {
            throw new RuntimeException("Expected 1 embedding, got " + data.size());
        }
        List<Double> result = data.get(0).getEmbedding();
        float[] target = new float[result.size()];
        for (int i = 0; i < target.length; i++) {
            target[i] = result.get(i).floatValue();
        }
        return target;
    }
}
