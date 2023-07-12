package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.util.Platform;
import link.locutus.discord.db.AEmbeddingDatabase;

import java.io.IOException;
import java.sql.SQLException;

public class MiniEmbedding extends AEmbeddingDatabase {
    private final Platform platform;
    private final Criteria<String, float[]> criteria;
    private final ZooModel<String, float[]> model;

    public MiniEmbedding(Platform platform) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        super("minilm");
        this.platform = platform;
        criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build();
        this.model = criteria.loadModel();

    }


    @Override
    public double[] fetchEmbedding(String text) {
        float[] res = predictor.predict(text);
    }

    @Override
    public synchronized void close() {
        this.model.close();
    }
}
