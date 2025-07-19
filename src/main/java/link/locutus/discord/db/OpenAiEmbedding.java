package link.locutus.discord.db;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import link.locutus.discord.gpt.pw.GptDatabase;

import java.sql.SQLException;
import java.util.List;

public class OpenAiEmbedding extends AEmbeddingDatabase{

    private final OpenAIClient service;
    private final String modelName;

    public OpenAiEmbedding(GptDatabase database, OpenAIClient service, String modelName) throws SQLException, ClassNotFoundException {
        super("openai", database);
        this.service = service;
        this.modelName = modelName;
    }

    private void init() {

    }

    @Override
    public float[] fetchEmbedding(String text) {
        init();
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .model(modelName)
                .input(text)
                .build();
        List<Embedding> data = service.embeddings().create(params).data();
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("No embeddings returned for text: " + text);
        }
        Embedding embedding = data.get(0);
        List<Float> values = embedding.embedding();
        if (values == null || values.isEmpty()) {
            throw new RuntimeException("No values in embedding for text: " + text);
        }
        float[] floatArray = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            floatArray[i] = values.get(i);
        }
        return floatArray;
    }

    @Override
    public void close() {

    }
}
