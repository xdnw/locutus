package link.locutus.discord.gpt.imps.moderator;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import link.locutus.discord.gpt.ModerationResult;

import java.io.IOException;
import java.util.*;

public class LocalModerator implements IModerator {
    private final ZooModel<String, Classifications> model;
    private final Predictor<String, Classifications> predictor;

    public LocalModerator(String modelId, int contextWindow) throws IOException, ModelNotFoundException, MalformedModelException {
        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId)
                .optEngine("PyTorch")
                .build();
        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();
    }

    @Override
    public int getSizeCap() {
        return 8192;  // TODO FIXME CM REF
    }

    @Override
    public int getSize(String text) {
        return 0; // TODO FIXME CM REF
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
        predictor.close();
        model.close();
    }
}