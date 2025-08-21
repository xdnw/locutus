package link.locutus.discord.gpt.imps.moderator;

import ai.djl.MalformedModelException;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ModerationResult;

import java.io.IOException;
import java.util.*;

public class LocalModerator implements IModerator {
    private final ZooModel<String, Classifications> model;
    private final Predictor<String, Classifications> predictor;
    private final HuggingFaceTokenizer tokenizer;
    private final int sizeCap;

    public LocalModerator(String modelId) throws IOException, ModelNotFoundException, MalformedModelException {
        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId)
                .optEngine("PyTorch")
                .build();
        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();

        // Load tokenizer matching the HF model to count tokens consistently
        this.tokenizer = HuggingFaceTokenizer.newInstance(modelId);

        // Detect context window from Hugging Face config; fall back to a safe default
        this.sizeCap = GPTUtil.detectContextWindow(model.getModelPath());
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

    @Override
    public List<ModerationResult> moderate(List<String> inputs) {
        List<ModerationResult> results = new ArrayList<>();
        for (String input : inputs) {
            try {
                Classifications classifications = predictor.predict(input);
                ModerationResult result = new ModerationResult();
                Set<String> flaggedCategories = new HashSet<>();
                Map<String, Double> categoryScores = new HashMap<>();

                List<String> classNames = classifications.getClassNames();
                List<Double> probabilities = classifications.getProbabilities();
                for (int i = 0; i < classNames.size(); i++) {
                    String className = classNames.get(i);
                    double score = probabilities.get(i);
                    if (score > 0.5) {
                        categoryScores.put(className, score);
                        flaggedCategories.add(className);
                    }
                }
                result.setFlagged(!flaggedCategories.isEmpty());
                result.setFlaggedCategories(flaggedCategories);
                result.setScores(categoryScores);
                results.add(result);
            } catch (TranslateException e) {
                throw new RuntimeException(e);
            }
        }
        return results;
    }

    public void close() {
        try {
            tokenizer.close();
        } catch (Exception ignore) {

        }
        predictor.close();
        model.close();
    }
}