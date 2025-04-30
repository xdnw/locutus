package link.locutus.discord.gpt;

import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.moderation.ModerationCategories;
import com.theokanning.openai.moderation.ModerationCategoryScores;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GPTModerator implements IModerator{
    private final OpenAiService service;

    public GPTModerator(OpenAiService service) {
        this.service = service;
    }

    public List<Moderation> checkModeration(String input) {
        return service.createModeration(ModerationRequest.builder().input(input).build()).getResults();
    }

    @Override
    public List<ModerationResult> moderate(List<String> inputs) {
        List<String> split = new ArrayList<>();
        for (String input : inputs) {
            int tokens = GPTUtil.getTokens(input, ModelType.GPT_3_5_TURBO);
            if (tokens > 2000) {
                List<String> parts = GPTUtil.getChunks(input, ModelType.GPT_3_5_TURBO, 2000);
                split.addAll(parts);
            } else {
                split.add(input);
            }
        }

        List<ModerationResult> results = new ArrayList<>();
        JSONObject response = checkModeration(split);
        if (response.has("error")) {
            ModerationResult errorResult = new ModerationResult();
            errorResult.setError(true);
            errorResult.setMessage(response.getString("error"));
            results.add(errorResult);
        } else {
            JSONArray resultsArray = response.getJSONArray("results");
            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject resultObject = resultsArray.getJSONObject(i);
                ModerationResult result = new ModerationResult();
                result.setFlagged(resultObject.getBoolean("flagged"));
                if (result.isFlagged()) {
                    JSONObject categoriesObject = resultObject.getJSONObject("categories");
                    Set<String> flaggedCategories = new HashSet<>();
                    for (String category : categoriesObject.keySet()) {
                        if (categoriesObject.getBoolean(category)) {
                            flaggedCategories.add(category);
                        }
                    }
                    result.setFlaggedCategories(flaggedCategories);
                    JSONObject categoryScoresObject = resultObject.getJSONObject("category_scores");
                    Map<String, Double> categoryScores = new HashMap<>();
                    for (String category : categoryScoresObject.keySet()) {
                        categoryScores.put(category, categoryScoresObject.getDouble(category));
                    }
                    result.setScores(categoryScores);
                }
                results.add(result);
            }
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

    public JSONObject checkModeration(List<String> inputs) {
        List<Moderation> all = new ArrayList<>();
        for (String input : inputs) {
            System.out.println("Moderating: " + input);
            List<Moderation> moderations = checkModeration(input);
            all.addAll(moderations);
        }
        // construct json
        JSONObject response = new JSONObject();
        JSONArray resultsArray = new JSONArray();
        for (Moderation moderation : all) {
            System.out.println(moderation);
            JSONObject resultObject = new JSONObject();
            resultObject.put("flagged", moderation.isFlagged());
            if (moderation.isFlagged()) {
                JSONObject categoriesObject = new JSONObject();
                ModerationCategories category = moderation.getCategories();
                addToObject(category, categoriesObject);
                resultObject.put("categories", categoriesObject);
                JSONObject categoryScoresObject = new JSONObject();
                ModerationCategoryScores categoryScores = moderation.getCategoryScores();
                addToObject(categoryScores, categoryScoresObject);
                resultObject.put("category_scores", categoryScoresObject);
            }
            resultsArray.put(resultObject);
        }
        response.put("results", resultsArray);
        return response;
//        String url = "https://api.openai.com/v1/moderations";
//        String apiKey = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY;
//
//        Map<String, List<String>> arguments = new HashMap<>();
//        arguments.put("input", inputs);
//
//        Consumer<HttpURLConnection> apply = connection -> {
//            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
//            connection.setRequestProperty("Content-Type", "application/json");
//        };
//
//        JSONObject argsJs = new JSONObject(arguments);
//        byte[] dataBinary = argsJs.toString().getBytes(StandardCharsets.UTF_8);
//
//        CompletableFuture<String> result = FileUtil.readStringFromURL(PagePriority.GPT_MODERATE, url, dataBinary,  FileUtil.RequestType.POST, null, apply);
//        String jsonStr = FileUtil.get(result);
//        // parse to JSONObject (org.json)
//        return new JSONObject(jsonStr);
    }
}
