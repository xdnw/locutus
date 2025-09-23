package link.locutus.discord.gpt.imps.text2text;

import ai.djl.sentencepiece.SpTokenizer;
import com.google.genai.Client;
import com.google.genai.types.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import link.locutus.discord.gpt.GPTUtil;

import java.util.*;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class GoogleAiText2Text implements IText2Text {

    private final Client client;
    private final String modelName;

    private Integer tokenLimit;
    private SpTokenizer tokenizer;
    private boolean tokenizerInitialized;

    public GoogleAiText2Text(Client client, String modelName) {
        this.client = client;
        this.modelName = modelName;
//        this.isGeminiModel = modelName.toLowerCase().contains("gemini");
    }

    // Optional init similar to embedding class to fetch token limits
    public void init() {
        try {
            Model model = client.models.get(modelName, null);
            this.tokenLimit = model.inputTokenLimit().orElse(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to init model: " + modelName, e);
        }
    }

    private Schema createRankingResponseSchema(Map<String, String> items) {
        // Deterministic order
        List<String> names = new ArrayList<>(items.keySet());
        Collections.sort(names);

        // ranking: array of strings (Gemini schema supports constraints, but we validate strictly client-side)
        Schema rankingArray = Schema.builder()
                .type(Type.Known.ARRAY)
                .items(Schema.builder()
                        .type(Type.Known.STRING)
                        .build())
                .build();

        Map<String, Schema> props = new LinkedHashMap<>();
        props.put("ranking", rankingArray);

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(props)
                .required(List.of("ranking"))
                .build();
    }

    /**
     * Gemini-based strict reranker returning a permutation of the input names in ranking order.
     * Uses structured output (application/json + responseSchema) and validates the response.
     */
    @Override
    public List<String> rerank(Map<String, String> items, String criterion, IntConsumer tokensUsed) {
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) throw new IllegalArgumentException("items must not be empty");

        // Deterministic item ordering for the prompt
        List<String> names = new ArrayList<>(items.keySet());
        Collections.sort(names);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a reranking engine. Rank the provided items by the given criterion.\n");
        prompt.append("Return JSON only in the form { \"ranking\": [name1, name2, ...] }.\n");
        prompt.append("Include each item name exactly once, ordered best to worst. No extra text.\n");
        prompt.append("Criterion: ").append(criterion).append("\n");
        for (String n : names) {
            String desc = items.get(n);
            prompt.append("- name: ").append(n).append("\n");
            prompt.append("  description: ").append(desc == null ? "" : desc.replace("\n", " ").trim()).append("\n");
        }

        GenerateContentConfig cfg = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(createRankingResponseSchema(items))
                .build();

        GenerateContentResponse response =
                client.models.generateContent(modelName, prompt.toString(), cfg);

        String out = response.text();
        if (out == null || out.isBlank()) {
            StringBuilder sb = new StringBuilder();
            response.candidates().ifPresent(cands -> {
                for (var c : cands) {
                    c.content().flatMap(Content::parts).ifPresent(parts -> {
                        for (Part p : parts) {
                            p.text().ifPresent(sb::append);
                        }
                    });
                }
            });
            out = sb.toString();
        }

        // Token usage (fallback to local count if not provided)
        if (tokensUsed != null) {
            final String outSnapshot = out == null ? "" : out;
            Supplier<Integer> backupTokenCount = () -> getSize(prompt.toString() + "\n" + outSnapshot);
            int tokens = response.usageMetadata()
                    .map(usage -> usage.totalTokenCount().orElseGet(backupTokenCount))
                    .orElseGet(backupTokenCount);
            tokensUsed.accept(tokens);
        }

        if (out.isBlank()) {
            throw new IllegalStateException("Empty rerank response");
        }

        // Parse and validate strict permutation
        Gson gson = new Gson();
        java.lang.reflect.Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> obj = gson.fromJson(out, mapType);
        Object rankingNode = obj == null ? null : obj.get("ranking");
        if (!(rankingNode instanceof List)) {
            throw new IllegalArgumentException("Missing or invalid 'ranking' array in response");
        }

        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) rankingNode;

        Set<String> allowed = new HashSet<>(items.keySet());
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>(raw.size());

        for (Object v : raw) {
            if (!(v instanceof String)) {
                throw new IllegalArgumentException("Ranking element must be a string: " + v);
            }
            String name = (String) v;
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unknown item in ranking: " + name);
            }
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Duplicate item in ranking: " + name);
            }
            result.add(name);
        }

        if (result.size() != allowed.size()) {
            throw new IllegalArgumentException("Ranking size " + result.size() + " != number of items " + allowed.size());
        }

        return result;
    }

    @Override
    public String getId() {
        return "google_" + modelName;
    }

    @Override
    public String generate(String text, IntConsumer tokenUsage) {
        try {
            GenerateContentConfig.Builder cfg = GenerateContentConfig.builder();

            GenerateContentResponse response = client.models.generateContent(modelName, text, cfg.build());

            String out = response.text();
            if (out != null) return out;

            StringBuilder sb = new StringBuilder();
            response.candidates().ifPresent(cands -> {
                for (var c : cands) {
                    c.content().flatMap(Content::parts).ifPresent(parts -> {
                        for (Part p : parts) {
                            p.text().ifPresent(sb::append);
                        }
                    });
                }
            });
            Supplier<Integer> backupTokenCount = () -> getSize(text + "\n" + sb.toString());
            if (tokenUsage != null) {
                int tokens = response.usageMetadata()
                        .map(usage -> usage.totalTokenCount().orElseGet(backupTokenCount))
                        .orElseGet(backupTokenCount);
                tokenUsage.accept(tokens);
            }

            if (sb.length() > 0) return sb.toString();
            throw new RuntimeException("No text generated for input.");
        } catch (Exception e) {
            throw new RuntimeException("Error generating content", e);
        }
    }

    // ITokenizer-style helpers (token count and cap)
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

    public int getSizeCap() {
        if (tokenLimit == null) {
            // Lazy init if not initialized explicitly
            init();
        }
        return tokenLimit != null ? tokenLimit : 0;
    }

    public void close() {
        // no-op; client is managed externally
    }

    private static Float parseFloat(String s, Float def) {
        try { return s == null ? def : Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static Integer parseInt(String s, Integer def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}