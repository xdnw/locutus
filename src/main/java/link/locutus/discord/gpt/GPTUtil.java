package link.locutus.discord.gpt;

import ai.djl.sentencepiece.SpTokenizer;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.models.moderations.Moderation;
import link.locutus.discord.Locutus;
import link.locutus.discord.gpt.imps.VectorRow;
import link.locutus.discord.gpt.imps.token.TokenizerDownloader;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.MathMan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GPTUtil {

    private static class RegistryHolder {
        static final EncodingRegistry INSTANCE = Encodings.newDefaultEncodingRegistry();
    }

    public static EncodingRegistry getRegistry() {
        return RegistryHolder.INSTANCE;
    }

    private static class Gemma2Tokenizer {
        static final SpTokenizer TOKENIZER;

        static {
            try {
                TOKENIZER = TokenizerDownloader.downloadAndLoad(TokenizerDownloader.SourceType.GITHUB, "google/gemma_pytorch/33b652c465537c6158f9a472ea5700e5e770ad3f/tokenizer", "tokenizer.model");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class Gemma3Tokenizer {
        static final SpTokenizer TOKENIZER;

        static {
            try {
                TOKENIZER = TokenizerDownloader.downloadAndLoad(TokenizerDownloader.SourceType.GITHUB, "google/gemma_pytorch/014acb7ac4563a5f77c76d7ff98f31b568c16508/tokenizer", "gemma3_cleaned_262144_v2.spiece.model");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static SpTokenizer getSpTokenizerOrNull(String modelName) {
        return switch (modelName) {
            case
                    "gemini-1.0-pro",
                    "gemini-1.0-pro-001",
                    "gemini-1.0-pro-002",
                    "gemini-1.5-pro",
                    "gemini-1.5-pro-001",
                    "gemini-1.5-pro-002",
                    "gemini-1.5-flash",
                    "gemini-1.5-flash-001",
                    "gemini-1.5-flash-002" -> GPTUtil.GEMMA_2();
            case
                    "gemini-embedding-001", // gemini-embedding-001 is preliminary, may not be correct
                    "gemini-2.5-pro",
                    "gemini-2.5-pro-preview-06-05",
                    "gemini-2.5-pro-preview-05-06",
                    "gemini-2.5-pro-exp-03-25",
                    "gemini-live-2.5-flash",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-preview-05-20",
                    "gemini-2.5-flash-preview-04-17",
                    "gemini-2.5-flash-lite",
                    "gemini-2.5-flash-lite-preview-06-17",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-lite",
                    "gemini-2.0-flash-001",
                    "gemini-2.0-flash-lite-001" -> GPTUtil.GEMMA_3();
            default -> null;
        };
    }

    public static SpTokenizer GEMMA_3() {
        return Gemma3Tokenizer.TOKENIZER;
    }

    public static SpTokenizer GEMMA_2() {
        return Gemma2Tokenizer.TOKENIZER;
    }

    public static int countSentencePieceTokens(SpTokenizer tokenizer, String text) {
        int count = tokenizer.tokenize(text).size();
        // Add end token
        return count + 1;
    }

    public static void normalize(float[] vector) {
        double normSquared = 0.0;
        for (float v : vector) {
            normSquared += v * v;
        }

        // Calculate norm
        double norm = Math.sqrt(normSquared);

        // If the vector is already very close to unit length, skip normalization
        // Threshold is tunable; 1e-6 is usually safe for float precision
        if (Math.abs(norm - 1.0) < 1e-6) {
            return;
        }

        if (norm == 0.0) {
            throw new IllegalArgumentException("Cannot normalize a zero vector");
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    public static int detectContextWindow(Path modelDir) {
        // Try common HF config locations
        Path[] candidates = new Path[] {
                modelDir.resolve("config.json"),
                modelDir.resolve("model").resolve("config.json")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    Integer v = findInt(json, "\"max_position_embeddings\"\\s*:\\s*(\\d+)");
                    if (v == null) v = findInt(json, "\"n_positions\"\\s*:\\s*(\\d+)");
                    if (v == null) v = findInt(json, "\"max_sequence_length\"\\s*:\\s*(\\d+)");
                    if (v != null) return v;
                } catch (IOException ignore) {
                }
            }
        }
        System.err.println("Warning: Unable to determine context window size from model config. " +
                "Using default value of 512 tokens. This may lead to unexpected behavior if the model supports a different size.");
        // Sensible default if the config doesn't declare it (typical BERT/Roberta: 512)
        return 512;
    }

    private static Integer findInt(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    public static void checkThrowModeration2(List<Moderation> moderations, String text) {
        for (Moderation result : moderations) {
            if (result.flagged()) {
                String message = "Your submission has been flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    public static void checkThrowModeration(String text) {
        if (text == null || text.isEmpty() || MathMan.isInteger(text)) return;
        PWGPTHandler gpt = Locutus.imp().getCommandManager().getV2().getPwgptHandler();
        if (gpt != null) {
            GptHandler handler = gpt.getHandler();
            List<ModerationResult> result = handler.getModerator().moderate(text);
            GPTUtil.checkThrowModeration(result, "<redacted>");
        }
    }

    public static void checkThrowModeration(List<ModerationResult> moderations, String text) {
        for (ModerationResult result : moderations) {
            if (result.isFlagged()) {
                String message = "Your submission was flagged as inappropriate:\n" +
                        "```json\n" + result.toString() + "\n```\n" +
                        "The content submitted:\n" +
                        "```json\n" + text.replaceAll("```", "\\`\\`\\`") + "\n```";
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static final ConcurrentHashMap<ModelType, Encoding> ENCODER_CACHE = new ConcurrentHashMap<>();

    public static int getTokens(String input, ModelType type) {
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, getRegistry()::getEncodingForModel);
        return enc.countTokens(input);
    }

    public static List<String> getChunksOld(String input, ModelType type, int tokenSizeCap) {
        Encoding enc = ENCODER_CACHE.computeIfAbsent(type, getRegistry()::getEncodingForModel);
        return Chunker.getChunks(input, tokenSizeCap, enc::countTokens);
    }

    public static float[] handleVectorChunking(String text, int tokenSizeCap,
                                               Function<String, Integer> getSize,
                                               Function<String, float[]> fetchEmbedding) {
        List<String> chunks = Chunker.getChunks(text, tokenSizeCap, getSize);
        if (chunks.isEmpty()) {
            return new float[0];
        }

        // Fast-path for a single chunk
        if (chunks.size() == 1) {
            float[] single = fetchEmbedding.apply(chunks.get(0));
            return (single != null) ? single : new float[0];
        }

        float[] weightedSum = null;
        long totalTokens = 0L;

        for (String chunk : chunks) {
            int tokens = Math.max(0, getSize.apply(chunk));
            if (tokens == 0) {
                continue;
            }

            float[] emb = fetchEmbedding.apply(chunk);
            if (emb == null || emb.length == 0) {
                continue;
            }

            if (weightedSum == null) {
                weightedSum = new float[emb.length];
            } else if (weightedSum.length != emb.length) {
                throw new IllegalStateException("Embedding dimension mismatch across chunks.");
            }

            // Accumulate weighted by token count
            for (int j = 0; j < emb.length; j++) {
                weightedSum[j] += emb[j] * tokens;
            }
            totalTokens += tokens;
        }

        if (weightedSum == null || totalTokens == 0L) {
            return new float[0];
        }

        // Divide by total token weight to get the mean
        float inv = 1.0f / (float) totalTokens;
        for (int j = 0; j < weightedSum.length; j++) {
            weightedSum[j] *= inv;
        }

        return weightedSum;
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

    public static List<VectorRow> rerankTopKByCosine_mutable(List<VectorRow> candidates, float[] queryVector, int k) {
        if (queryVector == null) throw new IllegalArgumentException("queryVector must not be null");
        if (candidates == null || candidates.isEmpty() || k <= 0) return Collections.emptyList();

        // compute query norm once
        int len = queryVector.length;
        double qNormSq = 0.0;
        for (float v : queryVector) qNormSq += (double) v * v;
        double qNorm = Math.sqrt(qNormSq);
        if (qNorm == 0.0) return Collections.emptyList();

        // produce a normalized query (unit length) so dot(candidate, qUnit) == cosine
        float[] qUnit = new float[len];
        double invQNorm = 1.0 / qNorm;
        for (int i = 0; i < len; i++) qUnit[i] = (float) (queryVector[i] * invQNorm);

        int n = candidates.size();

        // If k >= n: score all, set score field, sort descending
        if (k >= n) {
            List<VectorRow> scored = new ArrayList<>(n);
            for (VectorRow row : candidates) {
                if (row == null || row.vector == null || row.vector.length != len) {
                    if (row != null) row.score = Double.NEGATIVE_INFINITY;
                    continue;
                }
                double dot = 0.0;
                float[] v = row.vector;
                for (int i = 0; i < len; i++) dot += (double) v[i] * qUnit[i];
                // dot is already cosine because both are unit length
                row.score = Double.isFinite(dot) ? dot : Double.NEGATIVE_INFINITY;
                if (row.score > Double.NEGATIVE_INFINITY) scored.add(row);
            }
            scored.sort(Comparator.comparingDouble((ToDoubleFunction<VectorRow>) f -> f.score).reversed());
            return scored;
        }

        // Else use a min-heap of size k (min at head) for O(n log k)
        PriorityQueue<VectorRow> heap = new PriorityQueue<>(Comparator.comparingDouble(r -> r.score));

        for (VectorRow row : candidates) {
            if (row == null || row.vector == null || row.vector.length != len) {
                if (row != null) row.score = Double.NEGATIVE_INFINITY;
                continue;
            }
            double dot = 0.0;
            float[] v = row.vector;
            for (int i = 0; i < len; i++) dot += (double) v[i] * qUnit[i];

            double score = Double.isFinite(dot) ? dot : Double.NEGATIVE_INFINITY;
            if (score == Double.NEGATIVE_INFINITY) {
                row.score = score;
                continue;
            }

            row.score = score;

            if (heap.size() < k) {
                heap.offer(row);
            } else if (heap.peek().score < score) {
                heap.poll();
                heap.offer(row);
            }
        }

        List<VectorRow> top = new ArrayList<>(heap);
        top.sort(Comparator.comparingDouble((ToDoubleFunction<VectorRow>) f -> f.score).reversed());
        return top;
    }

    // Helper to compute cosine score between queryVector and a candidate row (uses precomputed queryNorm).
    private static double scoreCandidate(float[] queryVector, double queryNorm, VectorRow row) {
        if (row == null) return Double.NEGATIVE_INFINITY;
        float[] v = row.vector;
        if (v == null || v.length != queryVector.length) return Double.NEGATIVE_INFINITY;

        double dot = 0.0;
        double candNormSq = 0.0;
        for (int i = 0; i < v.length; i++) {
            double a = queryVector[i];
            double b = v[i];
            dot += a * b;
            candNormSq += b * b;
        }
        if (candNormSq == 0.0) return Double.NEGATIVE_INFINITY;
        double candNorm = Math.sqrt(candNormSq);
        double score = dot / (queryNorm * candNorm);
        if (Double.isFinite(score)) return score;
        return Double.NEGATIVE_INFINITY;
    }
}
