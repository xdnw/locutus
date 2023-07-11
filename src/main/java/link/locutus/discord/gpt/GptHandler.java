package link.locutus.discord.gpt;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GptDB;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.math.ArrayUtil;
import org.json.JSONObject;

import javax.annotation.Nullable;
import javax.validation.constraints.Null;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class GptHandler {
    public final GptDB db;
    private final Encoding embeddingEncoder;
    private final Encoding chatEncoder;
    private final EncodingRegistry registry;
    private final OpenAiService service;

    public GptHandler() throws SQLException, ClassNotFoundException {
        this.db = new GptDB();
        this.registry = Encodings.newDefaultEncodingRegistry();

        this.embeddingEncoder = registry.getEncodingForModel(ModelType.TEXT_EMBEDDING_ADA_002);
        this.chatEncoder = registry.getEncodingForModel(ModelType.GPT_3_5_TURBO);

        this.service = new OpenAiService(Settings.INSTANCE.OPENAI_API_KEY, Duration.ofSeconds(50));
    }

    public JSONObject checkModeration(String input) throws IOException {
        String url = "https://api.openai.com/v1/moderations";
        String apiKey = Settings.INSTANCE.OPENAI_API_KEY;

        Map<String, String> arguments = new HashMap<>();
        arguments.put("input", input);

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
        return embeddingEncoder.encode(text).size();
    }
    
    private double[] getEmbeddingApi(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(List.of(text))
                .build();
        EmbeddingResult embedResult = service.createEmbeddings(request);
        List<Embedding> data = embedResult.getData();
        if (data.size() != 1) {
            throw new RuntimeException("Expected 1 embedding, got " + data.size());
        }
        List<Double> result = data.get(0).getEmbedding();
        double[] target = new double[result.size()];
        for (int i = 0; i < target.length; i++) {
            target[i] = result.get(i);
        }
        return target;
    }

    public double[] getExistingEmbedding(int type, String id) {
        return this.db.getEmbedding(type, id);
    }

    public double[] getEmbedding(String text) {
        return getEmbedding(-1, null, text, false);
    }

    public double[] getEmbedding(int type, @Nullable String id, String text, boolean saveContent) {
        double[] existing = this.db.getEmbedding(text);
        if (existing == null) {
            System.out.println("Fetch embedding: " + text);
            existing = getEmbeddingApi(text);
            db.setEmbedding(type, id, text, existing, saveContent);
        }
        return existing;
    }

}
