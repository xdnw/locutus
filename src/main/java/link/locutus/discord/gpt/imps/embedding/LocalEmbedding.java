package link.locutus.discord.gpt.imps.embedding;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import link.locutus.discord.gpt.GPTUtil;

import java.io.IOException;

public class LocalEmbedding implements IEmbedding {
    private final String modelName;
    private final HuggingFaceTokenizer tokenizer;
    private int sizeCap;
    private Criteria<String, float[]> criteria;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;

    public LocalEmbedding(String modelName) {
        this.modelName = modelName;
        // Load tokenizer matching the HF model to count tokens consistently
        this.tokenizer = HuggingFaceTokenizer.newInstance(modelName);
    }

    @Override
    public int getSizeCap() {
        return sizeCap;
    }

    @Override
    public int getSize(String text) {
        // Count tokens using the HF tokenizer
        return tokenizer.encode(text).getIds().length;
    }

    private int dimensions = -1;

    @Override
    public int getDimensions() {
        if (dimensions != -1) {
            return dimensions;
        }
        String dimStr = model.getProperty("embedding_size");
        if (dimStr != null) {
            return dimensions = Integer.parseInt(dimStr);
        }
        return dimensions = fetch("dimension_check").length;
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
                        this.sizeCap = GPTUtil.detectContextWindow(model.getModelPath());
                        predictor = model.newPredictor();
                    } catch (ModelNotFoundException | MalformedModelException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    @Override
    public float[] fetch(String text) {
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

}
