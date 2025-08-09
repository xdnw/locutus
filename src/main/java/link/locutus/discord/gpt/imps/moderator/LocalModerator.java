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

    public static void main(String[] args) {
        // KoalaAI/Text-Moderation
        String modelId = "unitary/toxic-bert"; // Replace with your model ID
        List<String> testInputs = Arrays.asList(
                "I love this!",
                "You are so stupid.",
                "Have a nice day."
        );

        try {
            LocalModerator moderator = new LocalModerator(modelId);
            List<ModerationResult> results = moderator.moderate(testInputs);

            for (int i = 0; i < testInputs.size(); i++) {
                System.out.println("Input: " + testInputs.get(i));
                System.out.println("Flagged: " + results.get(i).isFlagged());
                System.out.println("Flagged Categories: " + results.get(i).getFlaggedCategories());
                System.out.println("Scores: " + results.get(i).getScores());
                System.out.println();
            }
            moderator.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        }

    public LocalModerator(String modelId) throws IOException, ModelNotFoundException, MalformedModelException {
        Criteria<String, Classifications> criteria = Criteria.builder()
                .setTypes(String.class, Classifications.class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/" + modelId)
                .optEngine("PyTorch")
                .build();
        this.model = criteria.loadModel();
        this.predictor = model.newPredictor();
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