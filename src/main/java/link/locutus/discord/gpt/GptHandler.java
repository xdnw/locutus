package link.locutus.discord.gpt;

import ai.djl.util.Platform;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.math.ArrayUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GptHandler {
    public final IEmbeddingDatabase embeddingDatabase;
    private final Encoding chatEncoder;
    private final EncodingRegistry registry;
    private final OpenAiService service;
    private final Platform platform;

    public GptHandler() throws SQLException, ClassNotFoundException {
        this.registry = Encodings.newDefaultEncodingRegistry();
        this.service = new OpenAiService(Settings.INSTANCE.OPENAI_API_KEY, Duration.ofSeconds(50));

        this.embeddingDatabase = new AdaEmbedding(registry, service);

        this.chatEncoder = registry.getEncodingForModel(ModelType.GPT_3_5_TURBO);

        this.platform = Platform.detectPlatform("pytorch");
    }

    public List<Moderation> checkModeration(String input) {
        return service.createModeration(ModerationRequest.builder().input(input).build()).getResults();
    }

    public List<ModerationResult> checkModerationList(List<String> inputs) throws IOException {
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

    public JSONObject checkModeration(List<String> inputs) throws IOException {
        String url = "https://api.openai.com/v1/moderations";
        String apiKey = Settings.INSTANCE.OPENAI_API_KEY;

        Map<String, List<String>> arguments = new HashMap<>();
        arguments.put("input", inputs);

        Consumer<HttpURLConnection> apply = connection -> {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
        };

        JSONObject argsJs = new JSONObject(arguments);
        byte[] dataBinary = argsJs.toString().getBytes(StandardCharsets.UTF_8);

        CompletableFuture<String> result = FileUtil.readStringFromURL(1, url, dataBinary,  FileUtil.RequestType.POST, null, apply);
        String jsonStr = FileUtil.get(result);
        // parse to JSONObject (org.json)
        return new JSONObject(jsonStr);
    }

    public OpenAiService getService() {
        return service;
    }

    public double getSimilarity(String a, String b) {
        return ArrayUtil.cosineSimilarity(getEmbedding(a), getEmbedding(b));
    }

    public int getChatTokenSize(String text) {
        return chatEncoder.encode(text).size();
    }

    public int getEmbeddingTokenSize(String text) {
        return embeddingDatabase.getEmbeddingTokenSize(text);
    }

    public void checkThrowModeration(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.isFlagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    private double[] getEmbeddingApi(String text, boolean checkModeration) {
        if (checkModeration) {
            List<Moderation> modResult = checkModeration(text);
            checkThrowModeration(modResult, text);
        }
        return embeddingDatabase.fetchEmbedding(text);
    }

    public double[] getExistingEmbedding(int type, String text) {
        return this.embeddingDatabase.getEmbedding(type, text);
    }

    public double[] getEmbedding(String text) {
        return getEmbedding(-1, null, text, false);
    }

    public double[] getEmbedding(int type, @Nullable String id, String text, boolean saveContent) {
        double[] existing = this.embeddingDatabase.getEmbedding(text);
        if (existing == null) {
            System.out.println("Fetch embedding: " + text);
            existing = getEmbeddingApi(text, type < 0);
            embeddingDatabase.setEmbedding(type, id, text, existing, saveContent);
        }
        return existing;
    }

}
