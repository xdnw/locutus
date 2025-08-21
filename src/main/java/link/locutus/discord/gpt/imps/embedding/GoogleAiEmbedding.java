    package link.locutus.discord.gpt.imps.embedding;

    import com.google.genai.Client;
    import com.google.genai.types.ContentEmbedding;
    import com.google.genai.types.EmbedContentConfig;
    import com.google.genai.types.Model;

    import java.util.List;

    public class GoogleAiEmbedding implements IEmbedding {

        private final String modelName;
        private final Client client;
        private final EmbedContentConfig config;
        private Model model;
        private Integer tokenLimit;
//        private SpTokenizer tokenizer;

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

        @Override
        public int getSizeCap() {
            return this.tokenLimit;
        }

        @Override
        public int getSize(String text) {
            return client.models.countTokens(modelName, text, null).totalTokens().orElseThrow();
        }

        @Override
        public float[] fetchEmbedding(String text) {
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
