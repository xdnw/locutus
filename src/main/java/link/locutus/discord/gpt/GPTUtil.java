package link.locutus.discord.gpt;

import ai.djl.sentencepiece.SpTokenizer;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.ModelType;
import com.openai.models.moderations.Moderation;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.gpt.imps.VectorRow;
import link.locutus.discord.gpt.imps.token.TokenizerDownloader;
import link.locutus.discord.gpt.pw.PWGPTHandler;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.web.commands.binding.value_types.WebOptions;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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

    public static Map<String, Object> toJsonSchema(Type type, boolean supportUnknown) {
        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();

            // Handle Map<K,V>
            if (Map.class.isAssignableFrom(raw)) {
                schema.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                Map<String, Object> child = toJsonSchema(valueType, supportUnknown);
                if (child == null) return null;
                schema.put("additionalProperties", child);
                return schema;
            }

            // Handle Collection<T> (List, Set)
            if (Collection.class.isAssignableFrom(raw)) {
                schema.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                Map<String, Object> child = toJsonSchema(elementType, supportUnknown);
                if (child == null) return null;
                schema.put("items", child);
                return schema;
            }

            if (!supportUnknown) return null;

            // Fallback: treat as object
            schema.put("type", "object");
            return schema;
        }

        // Handle raw classes (non-parameterized)
        if (type instanceof Class) {
            Class<?> cls = (Class<?>) type;
            if (cls.equals(String.class)) {
                schema.put("type", "string");
            } else if (cls.equals(Integer.class) || cls.equals(int.class)
                    || cls.equals(Long.class) || cls.equals(long.class)) {
                schema.put("type", "integer");
            } else if (cls.equals(Double.class) || cls.equals(double.class)
                    || cls.equals(Float.class) || cls.equals(float.class)) {
                schema.put("type", "number");
            } else if (cls.equals(Boolean.class) || cls.equals(boolean.class)) {
                schema.put("type", "boolean");
            } else if (cls.equals(Void.class) || cls.equals(void.class)) {
                schema.put("type", "null");
            } else if (cls.isArray()) {
                schema.put("type", "array");
                Map<String, Object> child = toJsonSchema(cls.getComponentType(), supportUnknown);
                if (child == null) return null;
                schema.put("items", child);
            } else {
                if (!supportUnknown) return null;
                // For any other custom class, treat as object
                schema.put("type", "object");
            }
            return schema;
        }
        if (!supportUnknown) return null;
        schema.put("type", "object");
        return schema;
    }

    public static String getJsonName(String keyName) {
        String name = keyName;
        name = name.replaceAll(" ", "")
                .replaceAll("<", "_")
                .replaceAll(">", "")
                .replaceAll(",", "_to_")
                .replaceAll("\\[", "_")
                .replaceAll("]", "")
                .toLowerCase(Locale.ROOT);
        return name;
    }

    public static Map<String, Object> generateSchema(Key<?> key, Function<Key<?>, Map<String, Object>> getOptions) {
        Map<String, Object> options = getOptions.apply(key);
        if (options != null) {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("getOptions returned empty schema for type: " + key);
            }
            return options;
        }

        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();

        Type type = key.getType();
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();

            // Handle Map<K,V>
            if (Map.class.isAssignableFrom(raw)) {
                schema.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                Map<String, Object> child = generateSchema(Key.of(valueType), getOptions);
                if (child == null) return null;
                schema.put("additionalProperties", child);
                return schema;
            }

            if (Collection.class.isAssignableFrom(raw)) {
                schema.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                Map<String, Object> child = generateSchema(Key.of(elementType), getOptions);
                if (child == null) return null;
                schema.put("items", child);
                return schema;
            }

            throw new IllegalArgumentException("Unsupported parameterized type: " + type.getTypeName());
        }

        if (type instanceof Class<?> cls) {
            if (cls.isEnum()) {
                schema.put("type", "string");
                Object[] constants = cls.getEnumConstants();
                if (constants.length == 0) {
                    schema.put("enum", Collections.emptyList());
                    return schema;
                }

                Enum<?> first = (Enum<?>) constants[0];
                boolean anyDiff = !first.name().equals(first.toString());

                if (!anyDiff) {
                    List<String> enumValues = new ArrayList<>(constants.length);
                    for (Object constant : constants) {
                        enumValues.add(((Enum<?>) constant).name());
                    }
                    schema.put("enum", enumValues);
                } else {
                    List<Map<String, Object>> oneOfList = new ArrayList<>(constants.length);
                    for (Object constant : constants) {
                        Enum<?> e = (Enum<?>) constant;
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("value", e.name());
                        entry.put("description", constant.toString());
                        oneOfList.add(entry);
                    }
                    schema.put("oneOf", oneOfList);
                }
                return schema;
            }
            if (cls.equals(String.class)) {
                schema.put("type", "string");
            } else if (cls.equals(Integer.class) || cls.equals(int.class)
                    || cls.equals(Long.class) || cls.equals(long.class)) {
                schema.put("type", "integer");
            } else if (cls.equals(Double.class) || cls.equals(double.class)
                    || cls.equals(Float.class) || cls.equals(float.class)) {
                schema.put("type", "number");
            } else if (cls.equals(Boolean.class) || cls.equals(boolean.class)) {
                schema.put("type", "boolean");
            } else if (cls.equals(Void.class) || cls.equals(void.class)) {
                schema.put("type", "null");
            } else if (cls.isArray()) {
                schema.put("type", "array");
                Map<String, Object> child = generateSchema(Key.of(cls.getComponentType()), getOptions);
                if (child == null) return null;
                schema.put("items", child);
            } else {
                throw new IllegalArgumentException("Unsupported class type: " + cls.getTypeName());
            }
            return schema;
        }
        throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }

    public static Map<String, Object> toJsonSchema(ValueStore store, ValueStore htmlOptionsStore, ValueStore schemaStore, Collection<ParametricCallable<?>> commands, GuildDB guildDB, User user, DBNation nation, String input) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();

        Map<Key<?>, Map<String, Object>> primitiveCache = new Object2ObjectLinkedOpenHashMap<>();

        Set<Parser<?>> argTypes = new ObjectLinkedOpenHashSet<>();
        Set<Key<?>> visited = new ObjectOpenHashSet<>();

        for (ParametricCallable<?> command : commands) {
            List<ParameterData> params = command.getUserParameters();
            for (ParameterData data : params) {
                Parser<?> parser = data.getBinding();
                Key<?> key = parser.getKey();
                if (!visited.add(key)) continue;

                Key<?> webType = parser.getWebTypeOrNull();
                try {
                    if (webType != null) {
                        parser = store.get(webType);
                        if (parser == null) {
                            throw new IllegalArgumentException("[Gpt-Tool] No parser for webType: " + webType + " (from " + key + ")");
                        }
                        key = parser.getKey();
                    }
                    if (data.getAnnotations().length == 0) {
                        Map<String, Object> existing = primitiveCache.get(key);
                        if (existing == null) {
                            Map<String, Object> primitiveSchema = toJsonSchema(data.getType(), false);
                            primitiveCache.put(key, primitiveSchema);
                        }
                        continue;
                    }
                    argTypes.add(parser);
                } catch (IllegalArgumentException e) {
                    System.err.println("[Gpt-Tool] " + key + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> definitions = new Object2ObjectLinkedOpenHashMap<>();

        for (Parser<?> parser : argTypes) {
            Key<?> key = parser.getKey();
            Map<String, Object> argumentDef = new Object2ObjectLinkedOpenHashMap<>();

            String typeName = GPTUtil.getJsonName(key.toSimpleString());
            Parser<?> schemaBinding = schemaStore.get(key);

            String typeDesc = schemaBinding == null ? null : schemaBinding.getDescription();
            if (typeDesc == null || typeDesc.isEmpty()) typeDesc = parser.getDescription();
            String[] examples = parser.getExamples();

            argumentDef.put("type", typeName);
            if (!typeDesc.isEmpty()) argumentDef.put("description", typeDesc);
            else {
                String classAndMethodOrNull = (parser instanceof MethodParser<?> methodParser) ?
                        methodParser.getMethod().getDeclaringClass().getSimpleName() + "." + methodParser.getMethod().getName()
                        : null;
                System.err.println("[Gpt-Tool] " + "Error: No description for type " + key + " in JSON schema generation | " + classAndMethodOrNull);
            }
            if (examples.length > 0) argumentDef.put("examples", Arrays.asList(examples));

            if (schemaBinding != null) {
                Map<String, Object> map = (Map<String, Object>) schemaBinding.apply(store, null);
                argumentDef.putAll(map);
                continue;
            }

            Function<Key<?>, Map<String, Object>> getOptions = t -> {
                Parser<?> toolOptions = schemaStore.get(t);
                if (toolOptions != null) {
                    return (Map<String, Object>) toolOptions.apply(store, null);
                }
                if (t instanceof ParameterizedType) {
                    System.err.println("[Gpt-Tool] Not checking options for parameterized type: " + t);
                    return null;
                }
                Parser<?> optionParser = htmlOptionsStore.get(t);
                if (optionParser == null) {
                    System.err.println("[Gpt-Tool] No html options for " + key);
                    return null;
                }
                WebOption option = (WebOption) optionParser.apply(store, null);
                List<String> options = option.getOptions();
                if (options != null && !options.isEmpty()) {
                    if (option.isAllowCustomOption()) {
                        return Map.of(
                        "anyOf", List.of(
                                Map.of(
                                        "type", "string",
                                        "enum", options
                                ),
                                Map.of(
                                        "type", "string"
                                )
                            )
                        );
                    }
                    return Map.of(
                            "type", "string",
                            "enum", options
                    );
                }
                WebOptions optionMeta = option.getQueryOptions(guildDB, user, nation);
                if (optionMeta != null) {
                    boolean isNumeric = optionMeta.key_numeric != null;
                    if (optionMeta.text != null || optionMeta.subtext != null) {
                        List<Map<String, Object>> oneOf = new ArrayList<>();
                        List<?> keys = isNumeric ? optionMeta.key_numeric : optionMeta.key_string;
                        List<String> texts = optionMeta.text;
                        List<String> subtexts = optionMeta.subtext;
                        int n = keys == null ? 0 : keys.size();
                        for (int i = 0; i < n; i++) {
                            Map<String, Object> entry = new LinkedHashMap<>();
                            entry.put("const", keys.get(i));
                            StringBuilder desc = new StringBuilder();
                            if (texts != null && i < texts.size() && texts.get(i) != null && !texts.get(i).isEmpty()) {
                                desc.append(texts.get(i));
                            }
                            if (subtexts != null && i < subtexts.size() && subtexts.get(i) != null && !subtexts.get(i).isEmpty()) {
                                if (desc.length() > 0) desc.append(" - ");
                                desc.append(subtexts.get(i));
                            }
                            if (desc.length() > 0) {
                                entry.put("description", desc.toString());
                            }

                            oneOf.add(entry);
                        }
                        return Map.of(
                                "type", isNumeric ? "number" : "string",
                                "oneOf", oneOf
                        );
                    }
                    if (isNumeric) {
                        return Map.of(
                                "type", "number",
                                "enum", optionMeta.key_numeric
                        );
                    } else {
                        return Map.of(
                                "type", "string",
                                "enum", optionMeta.key_string
                        );
                    }
                }

                return null;
            };

            // generateSchema for the type
            try {
                Map<String, Object> schema = generateSchema(key, getOptions);
                definitions.putAll(schema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] " + key + " (2): " + e.getMessage());
            }
            // enum

            definitions.put(typeName, argumentDef);
        }
        root.put("$defs", definitions);

        List<Map<String, Object>> tools = new ObjectArrayList<>();
        for (ParametricCallable<?> command : commands) {
            String cmdName = command.getFullPath().toLowerCase();
            try {
                Map<String, Object> schema = command.toJsonSchema(primitiveCache);
                tools.add(schema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] Skipping command " + cmdName + " in JSON schema generation: " + e.getMessage());
            }
        }
        root.put("tools", tools);

        return root;
    }
}
