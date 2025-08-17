package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.copilot.CopilotDeviceAuthenticationData;
import link.locutus.discord.gpt.imps.moderator.IModerator;
import link.locutus.discord.gpt.imps.text2text.CopilotText2Text;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.gpt.imps.text2text.OpenAiText2Text;
import link.locutus.discord.gpt.pw.GptDatabase;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.pusher.client.util.internal.Preconditions.checkNotNull;

public class GptHandler {
    public IEmbeddingDatabase embeddingDatabase;
    private IModerator moderator;
    private IText2Text text2Text;
//    private final ProcessText2Text processT2;

    private volatile boolean initOpenAIClient = false;
    private Object initOpenAIClientLock = new Object();
    private OpenAIClient openAiService2;

    private volatile boolean initGoogleClient = false;
    private Object initGoogleClientLock = new Object();
    private Client googleClient2;

    // api key
    // base url
    // provider

    public GptHandler(GptDatabase database) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
//        this.moderator = new OpenAiModerator(getOpenAiService());
//        this.embeddingDatabase = new LocalEmbedding(database, "sentence-transformers/all-MiniLM-L6-v2");
//        File scriptPath = new File("../gpt4free/my_project/gpt3_5_turbo.py");
//        File venvExe = new File("../gpt4free/venv/Scripts/python.exe");
//        File workingDirectory = new File("../gpt4free");
//        // ensure files exist
//        if (scriptPath.exists()) {
////            throw new RuntimeException("gpt4free not found: " + scriptPath.getAbsolutePath());
////        }
//            if (!venvExe.exists()) {
//                throw new RuntimeException("venv not found: " + venvExe.getAbsolutePath());
//            }
////        this.summarizer = new ProcessSummarizer(venvExe, gpt4freePath, ModelType.GPT_3_5_TURBO, 8192);
////            this.processT2 = new ProcessText2Text(venvExe, "my_project.gpt3_5_turbo", workingDirectory);
//        } else {
//            processT2 = null;
//        }
    }

    private OpenAIClient getOrCreateOpenAiService() {
        if (openAiService2 == null && !initOpenAIClient) {
            synchronized (initOpenAIClientLock) {
                if (openAiService2 == null) {
                    OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder()
                            .apiKey(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.API_KEY)
                            .timeout(Duration.ofSeconds(120));
                    if (Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.BASE_URL != null && !Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.BASE_URL.isEmpty()) {
                        builder = builder.baseUrl(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.OPENAI.BASE_URL);
                    }
                    openAiService2 = builder.build();
                }
            }
        }
        return openAiService2;
    }

    private Client getOrCreateGoogleClient() {
        if (googleClient2 == null && !initGoogleClient) {
            synchronized (initGoogleClientLock) {
                initGoogleClient = true;
                this.googleClient2 = createGoogleClient(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.GOOGLE_AI.BASE_URL, Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.GOOGLE_AI.API_KEY);
            }
        }
        if (googleClient2 == null) {
            throw new IllegalStateException("Google client not initialized. Please check your configuration.");
        }
        return googleClient2;
    }

    public static Client createGoogleClient(String baseUrl, String apiKey) {
        HttpOptions.Builder googeHttpOpt = HttpOptions.builder();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            googeHttpOpt.baseUrl(baseUrl);
        }
        googeHttpOpt.timeout(120);
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(googeHttpOpt.build())
                .build();
    }

    public IModerator getModerator() {
        return moderator;
    }

    public OpenAIClient getOpenAiService() {
        return openAiService2;
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
    public List<Long> registerEmbeddings(EmbeddingSource source, List<String> descriptions, boolean moderate, boolean deleteMissing) {
        // create a stream Map.Entry<String, String> from descriptions and expandedDescriptions
        return registerEmbeddings(source, descriptions.stream(), moderate, deleteMissing);
    }

    public <T> List<Long> registerEmbeddings(EmbeddingSource source, Stream<String> descriptionStream, boolean moderate, boolean deleteMissing) {

        checkNotNull(source, "source must not be null");
        ThrowingConsumer<String> moderateFunc = moderate ? this::checkModeration : null;

        Set<Long> hashesSet = new LongLinkedOpenHashSet();
        List<Long> hashes = new LongArrayList();
        // iterate over descriptionAndExpandedStream
        descriptionStream.forEach(new Consumer<String>() {
            @Override
            public void accept(String description) {
                long hash = embeddingDatabase.getHash(description);
                if (hashesSet.add(hash)) {
                    float[] vector = embeddingDatabase.getOrCreateEmbedding(hash, description, source, true, moderateFunc);
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
        return new OpenAiText2Text(openAiKey, model);
    }

    public IText2Text createCopilotText2Text(String path, Consumer<CopilotDeviceAuthenticationData> deviceAuthDataConsumer) {
        return new CopilotText2Text(path, deviceAuthDataConsumer);
    }

//    public IText2Text getProcessText2Text() {
//        return processT2;
//    }
}
