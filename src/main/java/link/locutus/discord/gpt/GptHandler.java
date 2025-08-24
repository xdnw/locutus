package link.locutus.discord.gpt;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.embeddings.EmbeddingModel;
import com.openai.models.moderations.ModerationModel;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.ASourceManager;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.imps.embedding.GoogleAiEmbedding;
import link.locutus.discord.gpt.imps.embedding.IEmbedding;
import link.locutus.discord.gpt.imps.embedding.LocalEmbedding;
import link.locutus.discord.gpt.imps.embedding.OpenAiEmbedding;
import link.locutus.discord.gpt.imps.moderator.IModerator;
import link.locutus.discord.gpt.imps.moderator.LocalModerator;
import link.locutus.discord.gpt.imps.moderator.OpenAiModerator;
import link.locutus.discord.gpt.imps.text2text.GoogleAiText2Text;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.gpt.imps.text2text.OpenAiText2Text;
import link.locutus.discord.gpt.pw.GptDatabase;
import link.locutus.discord.util.scheduler.ThrowingConsumer;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.pusher.client.util.internal.Preconditions.checkNotNull;
import static link.locutus.discord.gpt.GPTUtil.createGoogleClient;

public class GptHandler {
    public ISourceManager sourceManager;

    private IModerator moderator;
    private volatile IEmbedding embedding;

    private Map<IText2Text, Integer> text2TextList;

    private volatile boolean initOpenAIClient = false;
    private Object initOpenAIClientLock = new Object();
    private OpenAIClient openAiService2;

    private volatile boolean initGoogleClient = false;
    private Object initGoogleClientLock = new Object();
    private Client googleClient2;

    public GptHandler(GptDatabase database) throws SQLException, ClassNotFoundException, ModelNotFoundException, MalformedModelException, IOException {
        this.sourceManager = new ASourceManager(database, getEmbedding());
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

    public ISourceManager getSourceManager() {
        return sourceManager;
    }

    public IEmbedding getEmbedding() {
        if (this.embedding == null) {
            synchronized (this) {
                if (this.embedding == null) {
                    ProviderType type = ProviderType.parse(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.EMBEDDING.PROVIDER);
                    String modelName = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.EMBEDDING.EMBEDDING_MODEL;
                    if (modelName.isEmpty()) {
                           throw new IllegalArgumentException("Embedding model must not be empty. Please check your `config.yml` file.");
                    }
                    switch (type) {
                        case OPENAI -> {
                            EmbeddingModel model = EmbeddingModel.of(modelName.toUpperCase(Locale.ROOT));
                            this.embedding = new OpenAiEmbedding(GPTUtil.getRegistry(), getOrCreateOpenAiService(), model);
                        }
                        case GOOGLE -> {
                            String apiKey = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.GOOGLE_AI.API_KEY;
                            if (apiKey.isEmpty()) {
                                throw new IllegalArgumentException("Google AI API key must not be empty. Please check your `config.yml` file.");
                            }
                            this.embedding = new GoogleAiEmbedding(getOrCreateGoogleClient(), modelName);
                        }
                        case LOCAL -> {
                            this.embedding = new LocalEmbedding(modelName);
                        }
                    }
                    this.embedding.init();
                }
            }
        }
        return this.embedding;
    }

    public IModerator getModerator() {
        if (this.moderator == null) {
            synchronized (this) {
                if (this.moderator == null) {
                    ProviderType type = ProviderType.parse(Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.MODERATION.PROVIDER);
                    String modelName = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.MODERATION.MODEL;
                    if (modelName.isEmpty()) {
                        throw new IllegalArgumentException("Moderation model must not be empty. Please check your `config.yml` file.");
                    }
                    switch (type) {
                        case OPENAI -> {
                            ModerationModel model = ModerationModel.of(modelName.toUpperCase(Locale.ROOT));
                            this.moderator = new OpenAiModerator(getOrCreateOpenAiService(), model);
                        }
                        case GOOGLE -> {
                            throw new IllegalArgumentException("Google AI moderation is not yet implemented. Please use OpenAI or Local moderation.");
                        }
                        case LOCAL -> {
                            try {
                                this.moderator = new LocalModerator(modelName);
                            } catch (IOException | ModelNotFoundException | MalformedModelException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
        return this.moderator;

    }

    public IText2Text getText2Text() {
        if (this.text2TextList == null) {
            synchronized (this) {
                if (this.text2TextList == null) {
                    this.text2TextList = Settings.INSTANCE.ARTIFICIAL_INTELLIGENCE.CHAT.getInstances().stream()
                            .collect(Collectors.toMap(
                                    this::createTextToText,
                                    config -> config.DAILY_LIMIT
                            ));
                    if (this.text2TextList.isEmpty()) {
                        throw new IllegalArgumentException("No Text2Text models configured. Please check your `config.yml` file.");
                    }
                }
            }
        }

        return this.text2TextList.entrySet().stream()
                .filter(entry -> sourceManager.getUsage(entry.getKey().getId()) < entry.getValue())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("All Text2Text models have reached their daily limit."));
    }

    private IText2Text createTextToText(Settings.ARTIFICIAL_INTELLIGENCE.CHAT config) {
        ProviderType type = ProviderType.parse(config.PROVIDER);
        String modelName = config.MODEL;
        if (modelName.isEmpty()) {
            throw new IllegalArgumentException("Text2Text model must not be empty. Please check your `config.yml` file.");
        }
        switch (type) {
            case OPENAI -> {
                ChatModel model = ChatModel.of(modelName.toUpperCase(Locale.ROOT));
                return new OpenAiText2Text(getOrCreateOpenAiService(), model);
            }
            case GOOGLE -> {
                return new GoogleAiText2Text(getOrCreateGoogleClient(), modelName);
            }
            case LOCAL -> {
                throw new IllegalArgumentException("Local Text2Text is not yet implemented. Please use OpenAI or Google AI Text2Text.");
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public void checkModeration(String text) {
        List<ModerationResult> modResult = getModerator().moderate(text);
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
        IEmbedding embeddingFinal = getEmbedding();
        descriptionStream.forEach(description -> {
            long hash = sourceManager.getHash(description);
            if (hashesSet.add(hash)) {
                sourceManager.createEmbeddingIfNotExist(embeddingFinal, hash, description, source, moderateFunc);
            } else {
                Logg.info("Skipping duplicate description: ```\n" + description + "\n```");
            }
            hashes.add(hash);
        });
        if (deleteMissing) {
            sourceManager.deleteMissing(source, hashesSet);
        }
        return hashes;
    }

    public float[] getEmbedding(EmbeddingSource source, String text) {
        return sourceManager.getEmbedding(source, text, this::checkModeration);
    }
}
