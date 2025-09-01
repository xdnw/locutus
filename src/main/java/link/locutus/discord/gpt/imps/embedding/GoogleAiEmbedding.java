    package link.locutus.discord.gpt.imps.embedding;

    import ai.djl.sentencepiece.SpTokenizer;
    import com.google.genai.Client;
    import com.google.genai.types.ContentEmbedding;
    import com.google.genai.types.EmbedContentConfig;
    import com.google.genai.types.Model;
    import link.locutus.discord.gpt.GPTUtil;

    import java.io.IOException;
    import java.nio.file.Files;
    import java.nio.file.Paths;
    import java.util.List;
    import java.util.Locale;

    public class GoogleAiEmbedding implements IEmbedding {

        private final String modelName;
        private final Client client;
        private final EmbedContentConfig config;
        private Model model;
        private Integer tokenLimit;

        private SpTokenizer tokenizer;
        private boolean tokenizerInitialized;

        public GoogleAiEmbedding(Client client, String modelName) {
            this.client = client;
            this.modelName = modelName;
            this.config = null;//EmbedContentConfig.builder().build();
        }

        @Override
        public String getTableName() {
            return modelName.replaceAll("[^a-zA-Z0-9_]", "_");
        }

        @Override
        public void init() {
            try {
                this.model = client.models.get(modelName, null);
                this.tokenLimit = model.inputTokenLimit().get();
            } catch (Exception e) {
//                throw new RuntimeException(e);
                throw e;
            }
        }

        private int dimensions = -1;

        @Override
        public int getDimensions() {
            if (dimensions != -1) return dimensions;
            synchronized (this) {
                if (dimensions != -1) return dimensions;
                return dimensions = switch (modelName.toLowerCase()) {
                    case "gemini-embedding-001" -> 3072;
                    case "text-embedding-005", "text-multilingual-embedding-002" -> 768;
                    default -> {
                        String cachePath = "config/vector/dim_" + modelName + ".txt";
                        try {
                            if (Files.exists(Paths.get(cachePath))) {
                                String content = Files.readString(Paths.get(cachePath)).trim();
                                yield Integer.parseInt(content);
                            }
                        } catch (Exception e) {
                            // Ignore and proceed to fetch
                        }

                        // Fetch dummy embedding and cache
                        float[] dummyEmbedding = fetch("dimension_check");
                        int dim = dummyEmbedding.length;

                        // Cache the dimension
                        try {
                            Files.createDirectories(Paths.get("config/vector"));
                            Files.writeString(Paths.get(cachePath), Integer.toString(dim));
                        } catch (IOException e) {
                            // Ignore cache write errors
                        }
                        yield dim;
                    }
                };
            }
        }

        @Override
        public int getSizeCap() {
            return this.tokenLimit;
        }

        @Override
        public int getSize(String text) {
            if (!tokenizerInitialized) {
                synchronized (this) {
                    if (!tokenizerInitialized) {
                        this.tokenizer = GPTUtil.getSpTokenizerOrNull(modelName.toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (tokenizer != null) {
                return GPTUtil.countSentencePieceTokens(tokenizer, text);
            }
            try {
                return client.models.countTokens(modelName, text, null).totalTokens().orElseThrow();
            } catch (Exception e) {
                throw new RuntimeException("Error counting tokens", e);
            }
        }

        @Override
        public float[] fetch(String text) {
            System.out.println("Fetching embedding for text: ```\n" + text + "\n```");
            try {
                var response = client.models.embedContent(modelName, text, config);
                List<ContentEmbedding> floatList = response.embeddings().orElse(null);
                if (floatList == null || floatList.isEmpty()) {
                    throw new RuntimeException("No embeddings returned for sentence: " + text);
                }
                ContentEmbedding embedding = floatList.get(0);
                List<Float> values = embedding.values().orElse(null);
                if (values == null || values.isEmpty()) {
                    throw new RuntimeException("No values in embedding for sentence: " + text);
                }
                float[] floatArray = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    floatArray[i] = values.get(i);
                }
                return floatArray;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error embedding content: " + text, e);
            }
        }

        @Override
        public void close() {
//            tokenizer.close();
        }
    }
