package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.copilot.CopilotDeviceAuthenticationData;
import link.locutus.discord.gpt.imps.*;
import link.locutus.discord.gpt.pw.GptDatabase;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.pusher.client.util.internal.Preconditions.checkArgument;
import static com.pusher.client.util.internal.Preconditions.checkNotNull;

public class GptHandler {
    private final OpenAIClient service;
    public final IEmbeddingDatabase embeddingDatabase;
    private final IModerator moderator;
    private final ProcessText2Text processT2;

    public GptHandler(GptDatabase database) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
//        this.service = new OpenAIClient(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY, Duration.ofSeconds(120));
        this.service = OpenAIOkHttpClient.builder().apiKey(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY)
                .timeout(Duration.ofSeconds(120))
                .build();
        this.moderator = new GPTModerator(service);
//        this.embeddingDatabase = new AdaEmbedding(registry, service);
        // TODO change ^ that to mini
        this.embeddingDatabase = new MiniEmbedding(database);


        File scriptPath = new File("../gpt4free/my_project/gpt3_5_turbo.py");
        File venvExe = new File("../gpt4free/venv/Scripts/python.exe");
        File workingDirectory = new File("../gpt4free");
        // ensure files exist
        if (scriptPath.exists()) {
//            throw new RuntimeException("gpt4free not found: " + scriptPath.getAbsolutePath());
//        }
            if (!venvExe.exists()) {
                throw new RuntimeException("venv not found: " + venvExe.getAbsolutePath());
            }
//        this.summarizer = new ProcessSummarizer(venvExe, gpt4freePath, ModelType.GPT_3_5_TURBO, 8192);
            this.processT2 = new ProcessText2Text(venvExe, "my_project.gpt3_5_turbo", workingDirectory);
        } else {
            processT2 = null;
        }
    }

    public IModerator getModerator() {
        return moderator;
    }

    public OpenAIClient getService() {
        return service;
    }

    public IEmbeddingDatabase getEmbeddings() {
        return embeddingDatabase;
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
    public List<Long> registerEmbeddings(EmbeddingSource source, List<String> descriptions, @Nullable List<String> expandedDescriptions, boolean moderate, boolean deleteMissing) {
        checkArgument(expandedDescriptions == null || descriptions.size() == expandedDescriptions.size(), "descriptions and expandedDescriptions must be the same size");
        // create a stream Map.Entry<String, String> from descriptions and expandedDescriptions
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < descriptions.size(); i++) {
            map.put(descriptions.get(i), expandedDescriptions == null ? null : expandedDescriptions.get(i));
        }
        return registerEmbeddings(source, map.entrySet().stream(), moderate, deleteMissing);
    }

    public <T> List<Long> registerEmbeddings(EmbeddingSource source, Stream<Map.Entry<String, String>> descriptionAndExpandedStream, boolean moderate, boolean deleteMissing) {

        checkNotNull(source, "source must not be null");
        ThrowingConsumer<String> moderateFunc = moderate ? this::checkModeration : null;

        Set<Long> hashesSet = new LongLinkedOpenHashSet();
        List<Long> hashes = new LongArrayList();
        // iterate over descriptionAndExpandedStream
        descriptionAndExpandedStream.forEach(new Consumer<Map.Entry<String, String>>() {
            @Override
            public void accept(Map.Entry<String, String> entry) {
                String description = entry.getKey();
                String expandedDescription = entry.getValue();

                long hash = embeddingDatabase.getHash(description);
                if (hashesSet.add(hash)) {
                    float[] vector = embeddingDatabase.getOrCreateEmbedding(hash, description, expandedDescription == null ? null : () -> expandedDescription, source, true, moderateFunc);
                } else {
                    Logg.info("Skipping duplicate description: ```\n" + description + "\n```");
                }
                hashes.add(hash);
            }
        });
        if (deleteMissing) {
            embeddingDatabase.registerHashes(source, hashesSet, deleteMissing);
        }
        return hashes;
    }

    public float[] getEmbedding(EmbeddingSource source, String text) {
        return embeddingDatabase.getEmbedding(source, text, this::checkModeration);
    }

    public IText2Text createOpenAiText2Text(String openAiKey, ChatModel model) {
        return new GPTText2Text(openAiKey, model);
    }

    public IText2Text createCopilotText2Text(String path, Consumer<CopilotDeviceAuthenticationData> deviceAuthDataConsumer) {
        return new CopilotText2Text(path, deviceAuthDataConsumer);
    }

    public IText2Text getProcessText2Text() {
        return processT2;
    }
}
