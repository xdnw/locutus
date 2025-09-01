package link.locutus.discord.gpt.imps.moderator;

import com.google.cloud.language.v2.*;
import link.locutus.discord.gpt.ModerationResult;

import java.util.*;

public class GoogleModerator implements IModerator {

    public GoogleModerator() {
    }

    @Override
    public int getSize(String text) {
        return 0;
    }

    @Override
    public int getSizeCap() {
        return Integer.MAX_VALUE;
    }

    private static final Set<String> SEVERE_CATEGORIES = Set.of(
            "toxic",
            "derogatory",
            "insult",
            "profanity",
            "violent",
            "death, harm & tragedy",
            "dangerous content",
            "firearms & weapons",
            "public safety",
            "sexual"
    );
    final double CONFIDENCE_THRESHOLD = 0.75; // To avoid over-flagging
    @Override
    public List<ModerationResult> moderate(List<String> inputs) {
        try {
            final double CONFIDENCE_THRESHOLD = 0.75; // To avoid over-flagging

            List<ModerationResult> results = new ArrayList<>();

            try (LanguageServiceClient client = LanguageServiceClient.create()) {
                for (String text : inputs) {
                    Document doc = Document.newBuilder()
                            .setContent(text)
                            .setType(Document.Type.PLAIN_TEXT)
                            .build();

                    ModerateTextRequest request = ModerateTextRequest.newBuilder()
                            .setDocument(doc)
                            .setModelVersion(ModerateTextRequest.ModelVersion.MODEL_VERSION_2)
                            .build();

                    ModerateTextResponse response = client.moderateText(request);

                    ModerationResult result = new ModerationResult();
                    Set<String> flaggedCategories = new HashSet<>();
                    Map<String, Double> categoryScores = new HashMap<>();

                    for (ClassificationCategory cat : response.getModerationCategoriesList()) {
                        String name = cat.getName();
                        double score = cat.getConfidence();

                        boolean isSevere = SEVERE_CATEGORIES.contains(name.toLowerCase(Locale.ROOT));

                        if (isSevere && score >= CONFIDENCE_THRESHOLD) {
                            flaggedCategories.add(name);
                            categoryScores.put(name, score);
                        }
                    }

                    result.setFlagged(!flaggedCategories.isEmpty());
                    result.setFlaggedCategories(flaggedCategories);
                    result.setScores(categoryScores);
                    results.add(result);
                }
            }

            return results;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
    }
}