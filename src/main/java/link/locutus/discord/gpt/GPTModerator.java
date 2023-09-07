package link.locutus.discord.gpt;

import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.io.PagePriority;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
        List<ModerationResult> results = new ArrayList<>();
        JSONObject response = checkModeration(inputs);
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

    public JSONObject checkModeration(List<String> inputs) {
        String url = "https://api.openai.com/v1/moderations";
        String apiKey = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY;

        Map<String, List<String>> arguments = new HashMap<>();
        arguments.put("input", inputs);

        Consumer<HttpURLConnection> apply = connection -> {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
        };

        JSONObject argsJs = new JSONObject(arguments);
        byte[] dataBinary = argsJs.toString().getBytes(StandardCharsets.UTF_8);

        CompletableFuture<String> result = FileUtil.readStringFromURL(PagePriority.GPT_MODERATE, url, dataBinary,  FileUtil.RequestType.POST, null, apply);
        String jsonStr = FileUtil.get(result);
        // parse to JSONObject (org.json)
        return new JSONObject(jsonStr);
    }
}
