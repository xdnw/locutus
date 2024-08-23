package link.locutus.discord.gpt.imps;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import ai.djl.util.Platform;
import link.locutus.discord.Logg;
import link.locutus.discord.db.AEmbeddingDatabase;
import link.locutus.discord.gpt.pw.GptDatabase;

import java.io.IOException;
import java.sql.SQLException;

public class MiniEmbedding extends AEmbeddingDatabase {
    private Criteria<String, float[]> criteria;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public MiniEmbedding(GptDatabase database) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        super("minilm", database);
    }

    private void init() {
        if (criteria == null) {
            synchronized (this) {
                if (criteria == null) {
                    try {
                        long start = System.currentTimeMillis();
                        criteria = Criteria.builder()
                                .setTypes(String.class, float[].class)
                                .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
                                .optEngine("PyTorch")
                                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                                .build();
                        Logg.text("remove:||PERF Mini embedding " + (-start + (start = System.currentTimeMillis())));
                        this.model = criteria.loadModel();
                        Logg.text("remove:||PERF Mini embedding load " + (-start + (start = System.currentTimeMillis())));
                        predictor = model.newPredictor();
                        Logg.text("remove:||PERF Mini embedding predictor " + (-start + (start = System.currentTimeMillis())));
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
        super.close();
    }
}
