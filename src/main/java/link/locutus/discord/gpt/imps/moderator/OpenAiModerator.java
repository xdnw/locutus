package link.locutus.discord.gpt.imps.moderator;

import com.openai.client.OpenAIClient;
import com.openai.models.moderations.Moderation;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationModel;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.gpt.ModerationResult;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.*;

public class OpenAiModerator implements IModerator {
    private final OpenAIClient service;
    private final ModerationModel model;

    public OpenAiModerator(OpenAIClient service, ModerationModel model) {
        if (service == null) {
            throw new IllegalArgumentException("Text-Moderation: OpenAI service cannot be null. Please configure a valid openai key in your config.yml or disable AI features\n" +
                    "Note: Moderation api usage is free, but you need to have a valid OpenAI API key configured.");
        }
        this.service = service;
        this.model = model;
    }

    @Override
    public int getSize(String text) {
        return 0;
    }

    @Override
    public int getSizeCap() {
        return Integer.MAX_VALUE;
    }

    public List<Moderation> checkModeration(String input) {
        ModerationCreateParams params = ModerationCreateParams.builder()
                .model(model)
                .input(input)
                .build();

        return service.moderations().create(params).results();
    }

    @Override
    public List<ModerationResult> moderate(List<String> inputs) {
        List<String> split = new ArrayList<>();
        int cap = getSizeCap();
        for (String input : inputs) {
            int tokens = getSize(input);
            if (tokens > cap) {
                List<String> parts = GPTUtil.getChunks(input, cap, this::getSize);
                split.addAll(parts);
            } else {
                split.add(input);
            }
        }

        List<ModerationResult> results = new ObjectArrayList<>();
        List<Moderation> responses = checkModeration(split);
        for (Moderation resultObject : responses) {
            ModerationResult result = new ModerationResult();
            result.setFlagged(resultObject.flagged());
            if (result.isFlagged()) {
                Moderation.Categories categoriesObject = resultObject.categories();
                Moderation.CategoryScores catScoresObject = resultObject.categoryScores();
                Set<String> flaggedCategories = new HashSet<>();
                Map<String, Double> categoryScores = new Object2DoubleOpenHashMap<>();
                if (categoriesObject.harassment()) {
                    flaggedCategories.add("harassment");
                    categoryScores.put("harassment", catScoresObject.harassment());
                }
                if (categoriesObject.harassmentThreatening()) {
                    flaggedCategories.add("harassment_threatening");
                    categoryScores.put("harassment_threatening", catScoresObject.harassmentThreatening());
                }
                if (categoriesObject.hate()) {
                    flaggedCategories.add("hate");
                    categoryScores.put("hate", catScoresObject.hate());
                }
                if (categoriesObject.hateThreatening()) {
                    flaggedCategories.add("hate_threatening");
                    categoryScores.put("hate_threatening", catScoresObject.hateThreatening());
                }
                if (categoriesObject.illicit().orElse(false)) {
                    flaggedCategories.add("illicit");
                    categoryScores.put("illicit", catScoresObject.illicit());
                }
                if (categoriesObject.illicitViolent().orElse(false)) {
                    flaggedCategories.add("illicit_violent");
                    categoryScores.put("illicit_violent", catScoresObject.illicitViolent());
                }
                if (categoriesObject.selfHarm()) {
                    flaggedCategories.add("self_harm");
                    categoryScores.put("self_harm", catScoresObject.selfHarm());
                }
                if (categoriesObject.selfHarmInstructions()) {
                    flaggedCategories.add("self_harm_instructions");
                    categoryScores.put("self_harm_instructions", catScoresObject.selfHarmInstructions());
                }
                if (categoriesObject.selfHarmIntent()) {
                    flaggedCategories.add("self_harm_intent");
                    categoryScores.put("self_harm_intent", catScoresObject.selfHarmIntent());
                }
                if (categoriesObject.sexual()) {
                    flaggedCategories.add("sexual");
                    categoryScores.put("sexual", catScoresObject.sexual());
                }
                if (categoriesObject.sexualMinors()) {
                    flaggedCategories.add("sexual_minors");
                    categoryScores.put("sexual_minors", catScoresObject.sexualMinors());
                }
                if (categoriesObject.violence()) {
                    flaggedCategories.add("violence");
                    categoryScores.put("violence", catScoresObject.violence());
                }
                if (categoriesObject.violenceGraphic()) {
                    flaggedCategories.add("violence_graphic");
                    categoryScores.put("violence_graphic", catScoresObject.violenceGraphic());
                }
                result.setScores(categoryScores);
                result.setFlaggedCategories(flaggedCategories);
            }
            results.add(result);

        }
        return results;
    }

    public void addToObject(Object classWithProperties, JSONObject categoriesObject) {
        for (Field field : classWithProperties.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                categoriesObject.put(field.getName(), field.get(classWithProperties));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public List<Moderation> checkModeration(List<String> inputs) {
        List<Moderation> results = new ObjectArrayList<>();
        for (String input : inputs) {
            results.addAll(checkModeration(input));
        }
        return results;
    }
}
