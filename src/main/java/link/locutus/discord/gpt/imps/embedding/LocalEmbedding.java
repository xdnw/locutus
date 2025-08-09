package link.locutus.discord.gpt.imps.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.sql.SQLException;

public class LocalEmbedding implements IEmbedding {
    private final String modelName;
    private Criteria<String, float[]> criteria;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public LocalEmbedding(String modelName) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        this.modelName = modelName;
    }

    @Override
    public String getTableName() {
        // get last part after / if present
        String tmp = modelName;
        if (tmp.contains("/")) {
            tmp = tmp.substring(tmp.lastIndexOf('/') + 1);
        }
        if (tmp.equalsIgnoreCase("all-MiniLM-L6-v2")) {
            // legacy table name
            return "minilm";
        }
        return tmp.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }

    @Override
    public void init() {
        if (criteria == null) {
            synchronized (this) {
                if (criteria == null) {
                    try {
                        criteria = Criteria.builder()
                                .setTypes(String.class, float[].class)
                                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelName)
                                .optEngine("PyTorch")
                                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                                .build();
                        this.model = criteria.loadModel();
                        predictor = model.newPredictor();
                    } catch (ModelNotFoundException | MalformedModelException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public float[] fetchEmbedding(String text) {
        init();
        try {
            return predictor.predict(text);
        } catch (TranslateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void close() {
        if (model != null) {
            this.model.close();
        }
    }
}
