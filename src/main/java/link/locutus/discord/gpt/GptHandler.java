package link.locutus.discord.gpt;

import ai.djl.util.Platform;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.OpenAiService;
import com.theokanning.openai.moderation.Moderation;
import com.theokanning.openai.moderation.ModerationRequest;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.AdaEmbedding;
import link.locutus.discord.gpt.imps.GPTSummarizer;
import link.locutus.discord.gpt.imps.ProcessSummarizer;
import link.locutus.discord.gpt.imps.ProcessText2Text;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.util.ArrayUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
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
import java.util.function.Function;
import java.util.stream.Stream;

import static com.pusher.client.util.internal.Preconditions.checkArgument;
import static com.pusher.client.util.internal.Preconditions.checkNotNull;

public class GptHandler {
    private final Encoding chatEncoder;
    private final EncodingRegistry registry;
    private final OpenAiService service;
    private final Platform platform;

    //
    public final IEmbeddingDatabase embeddingDatabase;
    private final ISummarizer summarizer;
    private final IModerator moderator;
    private final ProcessText2Text text2text;

    public GptHandler() throws SQLException, ClassNotFoundException {
        this.registry = Encodings.newDefaultEncodingRegistry();
        this.service = new OpenAiService(Settings.INSTANCE.OPENAI_API_KEY, Duration.ofSeconds(50));

        this.chatEncoder = registry.getEncodingForModel(ModelType.GPT_3_5_TURBO);

        this.platform = Platform.detectPlatform("pytorch");

        this.moderator = new GPTModerator(service);
        this.embeddingDatabase = new AdaEmbedding(registry, service);
        // TODO change ^ that to mini

        File gpt4freePath = new File("../gpt4free/my_project/gpt3_5_turbo.py");
        File venvExe = new File("../gpt4free/venv/Scripts/python.exe");
        // ensure files exist
        if (!gpt4freePath.exists()) {
            throw new RuntimeException("gpt4free not found: " + gpt4freePath.getAbsolutePath());
        }
        if (!venvExe.exists()) {
            throw new RuntimeException("venv not found: " + venvExe.getAbsolutePath());
        }

        this.summarizer = new ProcessSummarizer(venvExe, gpt4freePath, ModelType.GPT_3_5_TURBO, 8192);
        this.text2text = new ProcessText2Text(venvExe, gpt4freePath);
    }

    public ProcessText2Text getText2text() {
        return text2text;
    }

    public IModerator getModerator() {
        return moderator;
    }

    public ISummarizer getSummarizer() {
        return summarizer;
    }

    public OpenAiService getService() {
        return service;
    }

    public IEmbeddingDatabase getEmbeddings() {
        return embeddingDatabase;
    }

    public int getChatTokenSize(String text) {
        return chatEncoder.encode(text).size();
    }

    public void checkModeration(String text) {
        List<ModerationResult> modResult = moderator.moderate(text);
        GPTUtil.checkThrowModeration(modResult, text);
    }

    /**
     * Register all your embedding descriptions for a source
     * @param source the source to register under
     * @param descriptions the descriptions to register (moderated)
     * @param expandedDescriptions the expanded descriptions to register (may be null)
     * @param moderate if the descriptions should be moderated
     * @param deleteMissing if embeddings in the source not included will be deleted
     * @return the ids of the embeddings
     */
    public List<Long> registerEmbeddings(EmbeddingSource source, List<String> descriptions, List<String> expandedDescriptions, boolean moderate, boolean deleteMissing) {
        checkArgument(descriptions.size() == expandedDescriptions.size(), "descriptions and expandedDescriptions must be the same size");
        // create a stream Map.Entry<String, String> from descriptions and expandedDescriptions
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < descriptions.size(); i++) {
            map.put(descriptions.get(i), expandedDescriptions.get(i));
        }
        return registerEmbeddings(source, map.entrySet().stream(), moderate, deleteMissing);
    }

    public <T> List<Long> registerEmbeddings(EmbeddingSource source, Stream<Map.Entry<String, String>> descriptionAndExpandedStream, boolean moderate, boolean deleteMissing) {

        checkNotNull(source, "source must not be null");
        ThrowingConsumer<String> moderateFunc = moderate ? this::checkModeration : null;

        Set<String> duplicateCheck = new HashSet<>();

        Set<Long> hashesSet = new LongLinkedOpenHashSet();
        // iterate over descriptionAndExpandedStream
        descriptionAndExpandedStream.forEach(new Consumer<Map.Entry<String, String>>() {
            @Override
            public void accept(Map.Entry<String, String> entry) {
                String description = entry.getKey();
                String expandedDescription = entry.getValue();

                long hash = embeddingDatabase.getHash(description);
                if (!hashesSet.add(hash)) {
                    throw new IllegalArgumentException("duplicate hash: " + hash + " for description: ```\n" + description + "\n```");
                }
                float[] vector = embeddingDatabase.getOrCreateEmbedding(hash, description, () -> expandedDescription, source, true, moderateFunc);
            }
        });
        if (deleteMissing) {
            embeddingDatabase.registerHashes(source, hashesSet, deleteMissing);
        }
        return new LongArrayList(hashesSet);
    }

    public float[] getEmbedding(EmbeddingSource source, String text) {
        return embeddingDatabase.getEmbedding(source, text, this::checkModeration);
    }
}
